plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

// Читаем CommitID из свойства, которое передаёт CI (./gradlew assembleDebug -PcommitId=abc1234)
// При локальной сборке берём из git напрямую, либо оставляем пустым
val commitId: String = if (project.hasProperty("commitId")) {
    project.property("commitId").toString()
} else {
    try {
        val process = Runtime.getRuntime().exec(arrayOf("git", "rev-parse", "--short", "HEAD"))
        process.inputStream.bufferedReader().readLine()?.trim() ?: ""
    } catch (_: Exception) {
        ""
    }
}

android {
    namespace = "by.iposdev.visorlink"
    compileSdk = 36

    defaultConfig {
        applicationId = "by.iposdev.visorlink"
        minSdk = 30
        targetSdk = 36
        versionCode = 21
        versionName = "1.18.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("long", "BUILD_TIMESTAMP", "${System.currentTimeMillis()}L")
        buildConfigField("String", "CHANNEL", "\"CANARY\"")
        buildConfigField("boolean", "InternalBuild", "true")
        buildConfigField("String", "CommitID", "\"$commitId\"")
    }

    buildTypes {
        debug {
            // Canary — только debug-сборка
            isDebuggable = true
            isMinifyEnabled = false
            applicationIdSuffix = ".canary"
            versionNameSuffix = "-canary+$commitId"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation.layout)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.media)

    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.functions)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.coil.compose)
    implementation(libs.accompanist.permissions)
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    implementation(libs.firebase.database)
    implementation(libs.firebase.config.ktx)
    implementation(libs.androidx.media.v170)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(platform(libs.firebase.bom.v3370))
}