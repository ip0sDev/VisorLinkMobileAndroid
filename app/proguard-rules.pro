# Firebase Firestore — keep all data model classes
-keep class by.iposdev.visorlink.data.model.** { *; }
-keepclassmembers class by.iposdev.visorlink.data.model.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Koin
-keep class org.koin.** { *; }
-keepclassmembers class * {
    @org.koin.core.annotation.* <methods>;
}

# Kotlin coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.Metadata { *; }

# Kotlin serialization
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations