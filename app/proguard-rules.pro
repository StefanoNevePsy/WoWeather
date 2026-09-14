# kotlinx.serialization -----------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class app.sereno.weather.** {
    *** Companion;
}
-keepclasseswithmembers class app.sereno.weather.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.sereno.weather.**$$serializer { *; }

# OkHttp --------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Compose -------------------------------------------------------------------
-dontwarn androidx.compose.**
