package de.mbraisch.garminwidget.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import de.mbraisch.garminwidget.auth.GarminClient
import de.mbraisch.garminwidget.ui.LoginState
import de.mbraisch.garminwidget.ui.MainViewModel
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Anmeldung über die echte Garmin-Anmeldeseite in einem WebView.
 * Passwort, Zwei-Faktor-Code und Captcha gibt der Nutzer direkt bei Garmin ein.
 * Die App fängt nur das einmalige Service-Ticket ab, das Garmin nach erfolgreicher
 * Anmeldung ausgibt, und tauscht es gegen ein Zugriffstoken.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(vm: MainViewModel, onClose: () -> Unit) {
    val loginState by vm.loginState.collectAsStateWithLifecycle()
    var attempt by remember { mutableIntStateOf(0) }
    var pageLoading by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bei Garmin anmelden") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Schließen") }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = loginState) {
                LoginState.Working -> CenterMessage {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Verbinde mit Garmin Connect…", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Die ersten Werte werden geladen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is LoginState.Failed -> CenterMessage {
                    Text("Anmeldung fehlgeschlagen", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(state.message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { vm.resetLogin(); attempt++ }) { Text("Erneut versuchen") }
                }

                LoginState.Idle -> {
                    // key(attempt): bei "Erneut versuchen" wird ein frischer WebView erzeugt
                    androidx.compose.runtime.key(attempt) {
                        GarminLoginWebView(
                            onTicket = { ticket -> vm.completeLogin(ticket, onDone = onClose) },
                            onLoading = { pageLoading = it },
                        )
                    }
                    if (pageLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterMessage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

/** Fängt die Antwort der Garmin-Anmelde-API ab (enthält "serviceTicketId"). */
private const val HOOK_JS = """
(function () {
  if (window.__gwHooked) return;
  window.__gwHooked = true;
  function check(text) {
    try {
      var m = /"serviceTicketId"\s*:\s*"(ST-[^"]+)"/.exec(text) || /[?&]ticket=(ST-[^"&\s]+)/.exec(text);
      if (m && window.GWBridge) window.GWBridge.onTicket(m[1]);
    } catch (e) {}
  }
  if (window.fetch) {
    var origFetch = window.fetch;
    window.fetch = function () {
      return origFetch.apply(this, arguments).then(function (resp) {
        try { resp.clone().text().then(check); } catch (e) {}
        return resp;
      });
    };
  }
  var origSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send = function () {
    this.addEventListener('load', function () {
      try { check(this.responseText); } catch (e) {}
    });
    return origSend.apply(this, arguments);
  };
})();
"""

private val TICKET_IN_URL = Regex("[?&]ticket=(ST-[^&#]+)")

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GarminLoginWebView(onTicket: (String) -> Unit, onLoading: (Boolean) -> Unit) {
    val delivered = remember { AtomicBoolean(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun deliver(ticket: String) {
        if (delivered.compareAndSet(false, true)) {
            mainHandler.post { onTicket(ticket) }
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            // Alte Garmin-Sitzungen verwerfen, damit wirklich eine neue Anmeldung startet
            CookieManager.getInstance().removeAllCookies(null)
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Wie ein normales Chrome auftreten (ohne WebView-Kennung)
                settings.userAgentString = settings.userAgentString.replace("; wv", "")
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onTicket(ticket: String) {
                        if (ticket.startsWith("ST-")) deliver(ticket)
                    }
                }, "GWBridge")

                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    WebViewCompat.addDocumentStartJavaScript(this, HOOK_JS, setOf("https://sso.garmin.com"))
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url.toString()
                        TICKET_IN_URL.find(url)?.let {
                            deliver(it.groupValues[1])
                            return true
                        }
                        val host = request.url.host.orEmpty()
                        if (!host.endsWith("garmin.com") && !host.endsWith("garmin.cn")) {
                            // Fremde Links (z. B. Datenschutz) im Browser öffnen
                            runCatching {
                                view.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                            return true
                        }
                        return false
                    }

                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                        onLoading(true)
                        TICKET_IN_URL.find(url)?.let {
                            view.stopLoading()
                            deliver(it.groupValues[1])
                        }
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        onLoading(false)
                        // Falls das Skript nicht schon beim Seitenstart eingefügt werden konnte
                        view.evaluateJavascript(HOOK_JS, null)
                    }
                }
                loadUrl(GarminClient.LOGIN_URL)
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
    )
}
