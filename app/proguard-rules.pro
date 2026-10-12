# ReefLive R8 rules. Compose and AndroidX ship their own consumer rules.
# Keep line numbers so crash stack traces from sideloaded release builds stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: species sheets, seed, backups and Room JSON columns are decoded with generated
# serializers. Keep them (and the Companion.serializer() lookups) for every @Serializable class of ours.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.reeflive.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.reeflive.**$$serializer { *; }
-keepclassmembers class com.reeflive.** {
    *** Companion;
}
