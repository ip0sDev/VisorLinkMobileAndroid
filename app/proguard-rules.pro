# Disable aggressive R8 method/constructor inlining and bytecode rewrites that break reflection
-dontoptimize

# ─── Firebase Firestore & Data Models ───────────────────────────────────────
# Keep all data model classes, subclasses, constructors, fields, and methods
-keep class org.visorlink.app.data.model.** { *; }
-keepclassmembers class org.visorlink.app.data.model.** { *; }
-keepclassmembers class org.visorlink.app.data.model.**$* { *; }

-keep class org.visorlink.app.data.idcard.** { *; }
-keepclassmembers class org.visorlink.app.data.idcard.** { *; }

-keep class org.visorlink.app.data.activity.** { *; }
-keepclassmembers class org.visorlink.app.data.activity.** { *; }

-keep class org.visorlink.app.data.remote.** { *; }
-keepclassmembers class org.visorlink.app.data.remote.** { *; }

-keep class org.visorlink.app.utils.ChatDataCache** { *; }
-keepclassmembers class org.visorlink.app.utils.ChatDataCache** { *; }

-keep class org.visorlink.app.utils.OutboxManager** { *; }
-keepclassmembers class org.visorlink.app.utils.OutboxManager** { *; }

# Firestore annotations & JavaBeans getters/setters/properties
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-keepattributes RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations, RuntimeInvisibleParameterAnnotations

-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.Exclude <fields>;
    @com.google.firebase.firestore.Exclude <methods>;
    @com.google.firebase.firestore.ServerTimestamp <fields>;
    @com.google.firebase.firestore.ServerTimestamp <methods>;
    @com.google.firebase.firestore.IgnoreExtraProperties <fields>;
    @com.google.firebase.firestore.IgnoreExtraProperties <methods>;
}

# Preserve all constructors for Kotlin reflection / Firestore toObject(...)
-keepclassmembers class * {
    <init>(...);
}

# ─── Firebase Core, Firestore, gRPC & Protobuf ──────────────────────────────
-keep class com.google.firebase.** { *; }
-keepclassmembers class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

-keep class io.grpc.** { *; }
-keepclassmembers class io.grpc.** { *; }
-dontwarn io.grpc.**

-keep class com.google.protobuf.** { *; }
-keepclassmembers class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

-keep class io.perfmark.** { *; }
-dontwarn io.perfmark.**

# ─── Coil Image Loader ──────────────────────────────────────────────────────
-keep class coil.** { *; }
-keepclassmembers class coil.** { *; }
-dontwarn coil.**
-keep class coil.compose.** { *; }
-keepclassmembers class coil.compose.** { *; }
-keep class coil.decode.** { *; }
-keepclassmembers class coil.decode.** { *; }
-keep class coil.fetch.** { *; }
-keepclassmembers class coil.fetch.** { *; }
-keep class coil.request.** { *; }
-keepclassmembers class coil.request.** { *; }

# ─── Koin DI ────────────────────────────────────────────────────────────────
-keep class org.koin.** { *; }
-keepclassmembers class * {
    @org.koin.core.annotation.* <methods>;
}

# ─── Kotlin Coroutines & Flow ───────────────────────────────────────────────
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.Metadata { *; }

# ─── Retrofit, OkHttp & Okio ────────────────────────────────────────────────
-dontnote retrofit2.Platform
-dontwarn retrofit2.Platform$Java8
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep class okhttp3.** { *; }
-keepclassmembers class okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class okio.** { *; }
-keepclassmembers class okio.** { *; }
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ─── Gson & DTO Serialization ───────────────────────────────────────────────
-keep class com.google.gson.** { *; }
-keepclassmembers enum * { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ─── Credential Manager & Google Sign-In ─────────────────────────────────────
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}
-keep class com.google.android.libraries.identity.googleid.** { *; }
