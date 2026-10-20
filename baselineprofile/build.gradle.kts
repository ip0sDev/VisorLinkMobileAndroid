plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "org.visorlink.app.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 30
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Тот же флейвор, что и у приложения: профиль снимаем с play-сборки (код одинаков, отличается только апдейтер)
    flavorDimensions += "distribution"
    productFlavors {
        create("play") { dimension = "distribution" }
        create("standalone") { dimension = "distribution" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    targetProjectPath = ":app"
}

baselineProfile {
    // Профиль снимается на подключённом устройстве/эмуляторе (API 33+ рекомендуется)
    useConnectedDevices = true
}

// Генерации профиля (вариант nonMinifiedRelease) нужен только генератор: бенчмарки в этом режиме
// пропускают себя через assumption, и AGP засчитывал их как упавшие тесты
androidComponents {
    onVariants(selector().withBuildType("nonMinifiedRelease")) { variant ->
        variant.instrumentationRunnerArguments.put("class", "org.visorlink.app.baselineprofile.BaselineProfileGenerator")
    }
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
