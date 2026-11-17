# Pertahankan JS Bridge (@JavascriptInterface) agar tidak di-obfuscate saat build Release
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class app.web.examprida.twa.MainActivity$AndroidAppBridge { *; }

# Google Mobile Ads / AdMob
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**
-keepattributes *Annotation*
