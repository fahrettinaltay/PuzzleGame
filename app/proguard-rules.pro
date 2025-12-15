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

# --- Ktor --- 
# Keep classes for Ktor CIO engine
-keep class io.ktor.client.engine.cio.** { *; }

# Keep serialization classes used by Ktor for JSON parsing
-keepattributes Signature
-keepnames class kotlinx.serialization.Serializable
-keepclassmembers class **$$serializer { *; }

# --- Coil --- 
# Coil's rules are usually bundled, but adding them manually can solve R8 issues.
-keepclasseswithmembernames class * {
    @coil.annotation.ExperimentalCoilApi <methods>;
    @coil.annotation.InternalCoilApi <methods>;
}
-dontwarn coil.util.**
-dontwarn okio.**

# --- Kotlin Coroutines ---
-keepclassmembers class kotlin.coroutines.jvm.internal.BaseContinuationImpl {
    private java.lang.Object label;
    private java.lang.Object[] interceptors;
}

# --- Fix for R8 Missing Classes Warning ---
-dontwarn org.slf4j.impl.StaticLoggerBinder
