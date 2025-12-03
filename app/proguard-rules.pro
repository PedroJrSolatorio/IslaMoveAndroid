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

# ========== REMOVE LOGS ==========
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# ========== FIREBASE ==========
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ========== YOUR DATA LAYER ==========
# Data models (keep structure for serialization)
-keep class com.rj.islamove.data.models.** {
    <fields>;
}
-keepclassmembers class com.rj.islamove.data.models.** {
    <init>(...);
}

# API interfaces
-keep interface com.rj.islamove.data.api.** { *; }

# Services under data
-keep class com.rj.islamove.data.services.** { *; }

# ========== SERVICES (root level) ==========
-keep class com.rj.islamove.services.** { *; }

# ========== DEPENDENCY INJECTION (Hilt/Dagger) ==========
-keep class com.rj.islamove.di.** { *; }
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepclassmembers class * {
    @dagger.* <methods>;
    @javax.inject.* <methods>;
}
-keepclassmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

# ========== VIEWMODELS ==========
-keep class com.rj.islamove.ui.viewmodels.** { *; }
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class androidx.lifecycle.** { *; }

# ========== UI COMPONENTS ==========
# Usually don't need explicit rules, but keep if issues arise
# -keep class com.rj.islamove.ui.components.** { *; }
# -keep class com.rj.islamove.ui.screens.** { *; }

# ========== NAVIGATION ==========
-keep class com.rj.islamove.ui.navigation.** { *; }

# ========== UTILS ==========
# Usually fine without rules, but add if you have reflection/serialization
# -keep class com.rj.islamove.utils.** { *; }

# ========== HILT ==========
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# ========== KOTLIN SERIALIZATION ==========
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.rj.islamove.**$$serializer { *; }
-keepclassmembers class com.rj.islamove.** {
    *** Companion;
}
-keepclasseswithmembers class com.rj.islamove.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ========== GSON (if using) ==========
-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ========== OKHTTP ==========
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# ========== RETROFIT (if using) ==========
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# ========== MAPBOX ==========
-keep class com.mapbox.** { *; }
-dontwarn com.mapbox.**

# ========== CLOUDINARY ==========
-keep class com.cloudinary.** { *; }
-dontwarn com.cloudinary.**

# ========== COMPOSE ==========
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ========== COIL (Image Loading) ==========
-keep class coil.** { *; }
-dontwarn coil.**

# ========== OTHER WARNINGS ==========
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn edu.umd.cs.findbugs.annotations.**