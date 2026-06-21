# ProGuard rules for IVANNA FUSION

# Keep all native method names
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Kotlin metadata
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep Compose classes
-keep class androidx.compose.** { *; }
-keep interface androidx.compose.** { *; }

# Keep coroutine classes
-keep class kotlinx.coroutines.** { *; }

# Keep DataStore classes
-keep class androidx.datastore.** { *; }

# Keep main application class
-keep class com.ivannafusion.** { *; }

# Keep JNI interface
-keep class com.ivannafusion.IvannaNativeLib { *; }
-keep class com.ivannafusion.AudioEngine { *; }
-keep class com.ivannafusion.PresetManager { *; }
-keep class com.ivannafusion.AudioCallbackManager { *; }
-keep class com.ivannafusion.MainActivity { *; }
-keep class com.ivannafusion.IVANNAApplication { *; }
-keep class com.ivannafusion.AudioProcessingService { *; }

# Logging
-keep class com.jakewharton.timber.** { *; }

# Don't obfuscate Timber
-dontwarn timber.log.Timber
