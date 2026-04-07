# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep all data/domain models used by Gson, Retrofit, or Room
-keep class com.webscare.orangelinetrain.domain.model.** { *; }
-keepclassmembers class com.webscare.orangelinetrain.domain.model.** { *; }

-keep class com.webscare.orangelinetrain.data.local.model.** { *; }
-keepclassmembers class com.webscare.orangelinetrain.data.local.model.** { *; }

-keep class com.webscare.orangelinetrain.data.remote.model.** { *; }
-keepclassmembers class com.webscare.orangelinetrain.data.remote.model.** { *; }

# Keep Room entities and DAOs
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers @androidx.room.Entity class * { *; }

# Keep Retrofit API interfaces
-keep interface com.webscare.orangelinetrain.** { *; }
-keep class com.webscare.orangelinetrain.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Retrofit + OkHttp
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}