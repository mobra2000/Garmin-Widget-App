package de.mbraisch.garminwidget.auth

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Die Anmeldung ist abgelaufen oder wurde widerrufen – der Nutzer muss sich neu anmelden. */
class AuthExpiredException(message: String) : IOException(message)

/** Garmin blockiert die Anfrage (z. B. 403/429) – meist vorübergehend. */
class GarminBlockedException(message: String) : IOException(message)

/**
 * Spricht direkt mit Garmin Connect – so, wie es die offizielle Garmin-Connect-App tut:
 *
 * 1. Anmeldung auf der Garmin-Anmeldeseite (WebView) liefert ein einmaliges "Service Ticket".
 * 2. Dieses Ticket wird bei diauth.garmin.com gegen ein Access- und Refresh-Token getauscht.
 * 3. Mit dem Access-Token werden die Werte von connectapi.garmin.com gelesen.
 *
 * Die Endpunkte sind nicht offiziell dokumentiert (Vorbild: python-garminconnect)
 * und können sich ändern.
 */
class GarminClient private constructor(context: Context) {

    private val tokenStore = TokenStore(context)
    private val refreshMutex = Mutex()

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val isLoggedIn: Boolean get() = tokenStore.load() != null

    fun logout() = tokenStore.clear()

    // ------------------------------------------------------------------
    // Anmeldung
    // ------------------------------------------------------------------

    /** Tauscht das Service-Ticket aus der Anmeldeseite gegen Tokens. */
    suspend fun exchangeTicket(ticket: String): Unit = withContext(Dispatchers.IO) {
        var lastStatus = 0
        for (clientId in DI_CLIENT_IDS) {
            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add("service_ticket", ticket)
                .add("grant_type", DI_GRANT_TYPE)
                .add("service_url", SERVICE_URL)
                .build()
            val request = Request.Builder()
                .url(DI_TOKEN_URL)
                .post(body)
                .nativeHeaders()
                .header("Authorization", basicAuth(clientId))
                .header("Accept", "application/json,text/html;q=0.9,*/*;q=0.8")
                .header("Cache-Control", "no-cache")
                .build()
            http.newCall(request).execute().use { resp ->
                lastStatus = resp.code
                if (resp.code == 429) throw GarminBlockedException("Garmin meldet zu viele Anfragen (429). Bitte später erneut versuchen.")
                if (!resp.isSuccessful) return@use
                val json = JSONObject(resp.body?.string().orEmpty())
                val access = json.optString("access_token")
                if (access.isBlank()) return@use
                tokenStore.save(
                    Tokens(
                        accessToken = access,
                        refreshToken = json.optString("refresh_token").ifBlank { null },
                        clientId = jwtClaim(access, "client_id") ?: clientId,
                    )
                )
                return@withContext
            }
        }
        throw IOException("Token-Tausch fehlgeschlagen (HTTP $lastStatus)")
    }

    // ------------------------------------------------------------------
    // API-Zugriff
    // ------------------------------------------------------------------

    /**
     * GET auf connectapi.garmin.com. Liefert [JSONObject], [JSONArray] oder null
     * (keine Daten, z. B. HTTP 204 oder für dieses Gerät nicht verfügbar).
     */
    suspend fun get(path: String, params: Map<String, String> = emptyMap()): Any? = withContext(Dispatchers.IO) {
        var tokens = validTokens()
        var response = execute(path, params, tokens)
        if (response.first == 401) {
            tokens = refresh(tokens, force = true)
            response = execute(path, params, tokens)
        }
        val (code, body) = response
        when {
            code == 401 -> throw AuthExpiredException("Anmeldung abgelaufen")
            code == 403 || code == 429 -> throw GarminBlockedException("Garmin hat die Anfrage abgelehnt (HTTP $code, ${path.split('/').getOrElse(1) { path }})")
            code == 204 || code == 404 -> null
            code !in 200..299 -> throw IOException("HTTP $code (${path.split('/').getOrElse(1) { path }})")
            body.isBlank() -> null
            else -> runCatching { JSONTokener(body).nextValue() }.getOrNull()
        }
    }

    suspend fun getObject(path: String, params: Map<String, String> = emptyMap()): JSONObject? =
        get(path, params) as? JSONObject

    suspend fun getArray(path: String, params: Map<String, String> = emptyMap()): JSONArray? =
        get(path, params) as? JSONArray

    private fun execute(path: String, params: Map<String, String>, tokens: Tokens): Pair<Int, String> {
        val urlBuilder = (API_BASE + path).toHttpUrl().newBuilder()
        params.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
        val request = Request.Builder()
            .url(urlBuilder.build())
            .get()
            .nativeHeaders()
            .header("Authorization", "Bearer ${tokens.accessToken}")
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { resp ->
            return resp.code to resp.body?.string().orEmpty()
        }
    }

    private suspend fun validTokens(): Tokens {
        val tokens = tokenStore.load() ?: throw AuthExpiredException("Nicht angemeldet")
        val exp = jwtClaim(tokens.accessToken, "exp")?.toLongOrNull()
        val msLeft = if (exp != null) exp * 1000 - System.currentTimeMillis() else Long.MAX_VALUE
        if (msLeft > 5 * 60 * 1000) return tokens
        return try {
            refresh(tokens, force = false)
        } catch (e: AuthExpiredException) {
            throw e
        } catch (e: IOException) {
            // Erneuerung gerade nicht möglich – solange das alte Token noch gilt, damit weitermachen
            if (msLeft > 30 * 1000) tokens else throw e
        }
    }

    private suspend fun refresh(old: Tokens, force: Boolean): Tokens = refreshMutex.withLock {
        // Ein paralleler Aufruf hat das Token evtl. schon erneuert.
        val current = tokenStore.load() ?: throw AuthExpiredException("Nicht angemeldet")
        if (current.accessToken != old.accessToken) return current
        if (!force) {
            val exp = jwtClaim(current.accessToken, "exp")?.toLongOrNull()
            if (exp != null && exp * 1000 - System.currentTimeMillis() > 5 * 60 * 1000) return current
        }
        val refreshToken = current.refreshToken ?: throw AuthExpiredException("Kein Refresh-Token")
        try {
            return doRefresh(current, refreshToken)
        } catch (e: AuthExpiredException) {
            throw e
        } catch (e: IOException) {
            // Einmal kurz warten und erneut versuchen (Netzwerk-/Serverfehler)
            kotlinx.coroutines.delay(1500)
            return doRefresh(current, refreshToken)
        }
    }

    private fun doRefresh(current: Tokens, refreshToken: String): Tokens {

        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", current.clientId)
            .add("refresh_token", refreshToken)
            .build()
        val request = Request.Builder()
            .url(DI_TOKEN_URL)
            .post(body)
            .nativeHeaders()
            .header("Authorization", basicAuth(current.clientId))
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .build()
        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.code == 400 || resp.code == 401) {
                throw AuthExpiredException("Anmeldung abgelaufen – bitte neu anmelden (HTTP ${resp.code} ${text.take(80)})")
            }
            if (!resp.isSuccessful) throw IOException("Token-Erneuerung fehlgeschlagen (HTTP ${resp.code} ${text.take(80)})")
            val json = JSONObject(text)
            val access = json.optString("access_token")
            if (access.isBlank()) throw IOException("Token-Erneuerung ohne Token")
            val updated = Tokens(
                accessToken = access,
                refreshToken = json.optString("refresh_token").ifBlank { refreshToken },
                clientId = jwtClaim(access, "client_id") ?: current.clientId,
            )
            tokenStore.save(updated)
            return updated
        }
    }

    private fun Request.Builder.nativeHeaders(): Request.Builder = this
        .header("User-Agent", NATIVE_USER_AGENT)
        .header("X-Garmin-User-Agent", NATIVE_X_GARMIN_USER_AGENT)
        .header("X-Garmin-Paired-App-Version", "10861")
        .header("X-Garmin-Client-Platform", "Android")
        .header("X-App-Ver", "10861")
        .header("X-Lang", "de")
        .header("X-GCExperience", "GC5")
        .header("Accept-Language", "de-DE,de;q=0.9,en;q=0.8")

    companion object {
        @Volatile
        private var instance: GarminClient? = null

        /**
         * Eine gemeinsame Instanz für App, Widget und Hintergrund-Aktualisierung,
         * damit das Token nie von zwei Stellen gleichzeitig erneuert wird.
         */
        fun get(context: Context): GarminClient =
            instance ?: synchronized(this) {
                instance ?: GarminClient(context.applicationContext).also { instance = it }
            }

        /** Anmeldeseite der Garmin-Connect-App (gleiches Verfahren wie die offizielle App). */
        const val SSO_CLIENT_ID = "GCM_IOS_DARK"
        const val SERVICE_URL = "https://mobile.integration.garmin.com/gcm/ios"
        val LOGIN_URL: String = "https://sso.garmin.com/mobile/sso/en_US/sign-in".toHttpUrl().newBuilder()
            .addQueryParameter("clientId", SSO_CLIENT_ID)
            .addQueryParameter("service", SERVICE_URL)
            .build()
            .toString()

        private const val API_BASE = "https://connectapi.garmin.com"
        private const val DI_TOKEN_URL = "https://diauth.garmin.com/di-oauth2-service/oauth/token"
        private const val DI_GRANT_TYPE =
            "https://connectapi.garmin.com/di-oauth2-service/oauth/grant/service_ticket"
        private val DI_CLIENT_IDS = listOf(
            "GARMIN_CONNECT_MOBILE_ANDROID_DI_2025Q2",
            "GARMIN_CONNECT_MOBILE_ANDROID_DI_2024Q4",
            "GARMIN_CONNECT_MOBILE_ANDROID_DI",
            "GARMIN_CONNECT_MOBILE_IOS_DI",
        )
        private const val NATIVE_USER_AGENT = "GCM-Android-5.23"
        private const val NATIVE_X_GARMIN_USER_AGENT =
            "com.garmin.android.apps.connectmobile/5.23; ; Google/sdk_gphone64_arm64/google; Android/33; Dalvik/2.1.0"

        private fun basicAuth(clientId: String): String =
            "Basic " + Base64.encodeToString("$clientId:".toByteArray(), Base64.NO_WRAP)

        /** Liest ein Feld aus dem (unsignierten) JWT-Payload, z. B. "exp" oder "client_id". */
        fun jwtClaim(token: String, claim: String): String? = runCatching {
            val payload = token.split('.')[1]
            val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
            val obj = JSONObject(json)
            if (obj.isNull(claim)) null else obj.get(claim).toString()
        }.getOrNull()
    }
}
