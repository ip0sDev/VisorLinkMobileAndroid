plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.sentry.android)
    alias(libs.plugins.roborazzi)
}

// Читаем CommitID из свойства, которое передаёт CI (./gradlew assembleDebug -PcommitId=abc1234)
// При локальной сборке оставляем пустым
val commitId: String = if (project.hasProperty("commitId")) {
    project.property("commitId").toString()
} else {
    providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.map { it.trim() }.getOrElse("")
}
val currentChannel = "CANARY"

android {
    namespace = "org.visorlink.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.visorlink.app"
        minSdk = 30
        targetSdk = 37
        versionCode = 170
        versionName = "4.2.00"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("long", "BUILD_TIMESTAMP", "${System.currentTimeMillis()}L")
        buildConfigField("String", "CHANNEL", "\"CANARY\"")
        buildConfigField("boolean", "InternalBuild", "true")
        buildConfigField("String", "CommitID", "\"$commitId\"")
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
            isDefault = true
        }
        create("standalone") {
            dimension = "distribution"
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            // applicationIdSuffix = ".canary"
            versionNameSuffix = "-debug"
        }
        release {
            if (currentChannel == "CANARY") {
                versionNameSuffix = "-canary+$commitId"
            }
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

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric для скриншот-тестов: нужны ресурсы (шрифты, строки)
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.graphicsMode", "NATIVE")
            it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
            it.maxHeapSize = "4g"
            // Robolectric лезет во внутренности FileDescriptor; на JDK 17+ без этого падает
            it.jvmArgs(
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
            )
        }
    }
}

sentry {
    // Включаем загрузку маппингов и символов только если есть токен (чтобы билд не падал локально или в CI без секретов)
    val hasSentryToken = System.getenv("SENTRY_AUTH_TOKEN") != null || project.hasProperty("SENTRY_AUTH_TOKEN")

    // Automatically upload ProGuard/R8 mapping files
    includeProguardMapping.set(hasSentryToken)
    // Automatically upload Native Symbols (if using NDK)
    uploadNativeSymbols.set(hasSentryToken)
    // Enable auto-instrumentation (HTTP, fragments, etc.)
    tracingInstrumentation {
        enabled.set(true)
    }
}

dependencies {
    // ── Ipos Store In-App Updates SDK (Только для standalone сборок через Actions) ─
    "standaloneImplementation"(files("libs/ipos-store-sdk-release.aar"))

    // ── Compose ──────────────────────────────────────────────────────────────
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui.geometry)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.ui.text)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // ── AndroidX & Lifecycle ─────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.media)
    implementation(libs.sentry.android)

    // ── Koin ─────────────────────────────────────────────────────────────────
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)

    // ── Firebase ─────────────────────────────────────────────────────────────
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.database)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.config)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)

    // ── Other ────────────────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.play.services.auth)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.lottie.compose)
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.media3.exoplayer) // или 1.3.0+
    implementation(libs.androidx.media3.ui)
    implementation(libs.coil.video)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.jwt.decode)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.video)

    // ── Tests ────────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test) // Для runTest, setMain, advanceUntilIdle
    testImplementation(libs.mockito.kotlin)          // Для mock, whenever, any, verify
    testImplementation(libs.mockito.core)            // Ядро Mockito
    // Скриншот-тесты тем (Roborazzi поверх Robolectric, без устройства)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}

// Автоматическое создание app/google-services.json из шаблона при клонировании репозитория
tasks.register("ensureGoogleServices") {
    doFirst {
        val target = file("google-services.json")
        val example = file("google-services.json.example")
        if (!target.exists() && example.exists()) {
            example.copyTo(target)
            logger.lifecycle("Автоматически создан app/google-services.json из шаблона.")
        }
    }
}
tasks.matching { it.name.startsWith("process") && it.name.endsWith("GoogleServices") }.configureEach {
    dependsOn("ensureGoogleServices")
}

// Позволяет использовать Compose 1.13-alpha без принудительного обновления локального SDK до 37.1
tasks.matching { it.name.contains("AarMetadata") }.configureEach {
    enabled = false
}

roborazzi {
    // Эталоны коммитятся в репозиторий: по ним verify ловит визуальные регрессии тем
    outputDir.set(file("src/test/screenshots"))
}

// ── Правила UI-слоя ──────────────────────────────────────────────────────────
// Экраны не должны обходить компоненты Vl* и токены темы: прямые M3-компоненты
// и захардкоженные цвета — главная причина, по которой M3E и Forge разъезжались.
// Существующие нарушения зафиксированы в baseline: сборка падает, только если
// в файле нарушений стало БОЛЬШЕ. Уменьшили — обновите baseline:
//   ./gradlew checkUiRules -PupdateUiBaseline
val uiRulesSrc = file("src/main/java/org/visorlink/app/ui")
val uiRulesBaseline = rootProject.file("config/ui-rules-baseline.txt")
val uiRulesUpdate = providers.gradleProperty("updateUiBaseline").isPresent
tasks.register("checkUiRules") {
    group = "verification"
    description = "Запрещает новые захардкоженные цвета и прямые M3-компоненты в экранах"
    inputs.dir(uiRulesSrc)
    inputs.files(uiRulesBaseline)
    val srcDir = uiRulesSrc
    val baselineFile = uiRulesBaseline
    val update = uiRulesUpdate
    doLast {
        val rawM3 = Regex(
            """(?<![\w.])(Card|ElevatedCard|OutlinedCard|Button|OutlinedButton|TextButton|FilledTonalButton|ElevatedButton|""" +
                """Switch|TopAppBar|CenterAlignedTopAppBar|MediumTopAppBar|LargeTopAppBar|TextField|OutlinedTextField|""" +
                """AlertDialog|FloatingActionButton|ExtendedFloatingActionButton|NavigationBar|SingleChoiceSegmentedButtonRow)\s*\("""
        )
        val hexColor = Regex("""(?<![\w.])Color\(\s*0x""")
        // Захардкоженные цвета ищем везде, кроме ui/theme — палитры живут там по определению.
        // Прямые M3-компоненты — только в экранах: компоненты Vl* их как раз оборачивают.
        val counts = sortedMapOf<String, Int>()
        srcDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val rel = f.relativeTo(srcDir).invariantSeparatorsPath
            if (rel.startsWith("theme/")) return@forEach
            val code = f.readLines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
            val hex = hexColor.findAll(code).count()
            if (hex > 0) counts["$rel hex-color"] = hex
            if (rel.startsWith("screens/")) {
                val m3 = rawM3.findAll(code).count()
                if (m3 > 0) counts["$rel raw-m3"] = m3
            }
        }
        if (update) {
            baselineFile.parentFile.mkdirs()
            baselineFile.writeText(
                "# Сгенерировано: ./gradlew checkUiRules -PupdateUiBaseline\n" +
                    "# <файл относительно ui/> <правило> <допустимое число>\n" +
                    counts.entries.joinToString("") { "${it.key} ${it.value}\n" }
            )
            logger.lifecycle("Baseline обновлён: ${counts.size} записей, ${counts.values.sum()} нарушений")
            return@doLast
        }
        val baseline = if (baselineFile.exists()) {
            baselineFile.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.associate {
                val i = it.lastIndexOf(' ')
                it.substring(0, i) to it.substring(i + 1).toInt()
            }
        } else emptyMap()
        val grown = counts.filter { (k, v) -> v > (baseline[k] ?: 0) }
        val shrunk = baseline.filter { (k, v) -> (counts[k] ?: 0) < v }
        if (shrunk.isNotEmpty()) {
            logger.lifecycle("Нарушений стало меньше в ${shrunk.size} местах — зафиксируйте: ./gradlew checkUiRules -PupdateUiBaseline")
        }
        if (grown.isNotEmpty()) {
            throw GradleException(
                "Новые нарушения правил UI (используйте компоненты Vl* и цвета из MaterialTheme/VlTheme.tokens):\n" +
                    grown.entries.joinToString("\n") { "  ui/${it.key}: ${baseline[it.key] ?: 0} → ${it.value}" }
            )
        }
    }
}
tasks.matching { it.name == "check" }.configureEach { dependsOn("checkUiRules") }

// Алиас для запуска юнит-тестов без ошибки неоднозначности флейворов (play/standalone)
tasks.register("testDebugUnitTest") {
    dependsOn("testPlayDebugUnitTest")
    description = "Runs unit tests for the default (playDebug) variant"
    group = "verification"
}

