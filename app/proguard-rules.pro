# JavaScript-Brücke der Anmeldeseite (wird per Reflection aufgerufen)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# WorkManager instanziiert Worker über den Klassennamen
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
