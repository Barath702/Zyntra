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

# Preserve native JNI audio engine methods and class
-keep class com.example.core.audio.NativeAudioEngine {
    native <methods>;
    *;
}

# Preserve kotlinx.serialization metadata, serializers, and NoteCraft data models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.core.project.**$$serializer { *; }
-keepclassmembers class com.example.core.project.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.core.project.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.core.common.**$$serializer { *; }
-keepclassmembers class com.example.core.common.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.core.common.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.NoteCraftRoute** { *; }
-keepclassmembers class com.example.NoteCraftRoute** {
    *** Companion;
}

# Preserve Room entities and DAOs
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class com.example.core.database.** { *; }
-keep @androidx.room.Dao interface com.example.core.database.** { *; }

