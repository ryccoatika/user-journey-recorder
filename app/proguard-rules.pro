# R8 full-mode keep rules for Journey Recorder.
#
# Most libraries ship their own consumer rules (Room, Compose, DataStore), so
# only project-specific needs live here.

# --- Firebase (Analytics + Crashlytics) ------------------------------------
# Firebase discovers its components reflectively: the ComponentRegistrar class
# names are baked into the merged manifest as strings and Class.forName()'d at
# startup. R8 full mode renames those classes, breaking discovery →
# "FirebaseCrashlytics component is not present" crash in Application.onCreate.
# Keep the registrars (and their names) so discovery resolves.
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
-keepnames class * implements com.google.firebase.components.ComponentRegistrar

# --- kotlinx.serialization -------------------------------------------------
# Type-safe Navigation routes are @Serializable. Keep the generated serializers
# and companions so R8 full mode can't strip them. (Mirrors the rules published
# in the kotlinx.serialization README for R8 full mode.)
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Keep `INSTANCE` + serializer() of @Serializable objects (our route objects).
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep the Companion field and its serializer() for @Serializable classes
# (our DetailRoute data class and any future ones).
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static <1>$Companion Companion;
}
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
