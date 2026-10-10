import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    // Firebase is optional at build time. The google-services plugin hard-fails when
    // google-services.json is absent, which would make the project unbuildable for
    // anyone who has not created a Firebase project yet. Declaring it `apply false`
    // here and applying it below only when the file exists keeps the app fully
    // functional offline, and lights Firebase up the moment a config is dropped in.
    alias(libs.plugins.google.services) apply false
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

android {
    namespace = "com.example.guardianangel"
    compileSdk {
        version = release(37)
    }

    val secretsProps = Properties()
    val secFile = rootProject.file("secrets.properties")
    val locFile = rootProject.file("local.properties")
    if (secFile.exists()) {
        secFile.inputStream().use { secretsProps.load(it) }
    } else if (locFile.exists()) {
        locFile.inputStream().use { secretsProps.load(it) }
    }
    val mapsApiKey = secretsProps.getProperty("MAPS_API_KEY")
        ?: secretsProps.getProperty("GOOGLE_MAPS_API_KEY")
        ?: "AIzaSyPlaceholderGuardianAngelKey"

    defaultConfig {
        applicationId = "com.example.guardianangel"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // sherpa-onnx ships 121 MB of native libraries across four ABIs. armeabi-v7a
            // and x86 are 57 MB of that and reach essentially nobody: 32-bit ARM phones
            // cannot comfortably run this stack anyway, and x86 is emulator-only.
            // x86_64 is kept so the app still runs on a development emulator.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
            )
        }
    }


    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

// Exported Room schemas are checked in, so a migration becomes a reviewable diff rather
// than something discovered when a user's database fails to open.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    // sherpa-onnx ships Android as a prebuilt .aar from its GitHub releases rather than
    // a Maven artifact (the JitPack coordinate is JVM/desktop only and would drag in
    // ~100 MB of linux/macOS/Windows natives). Drop the AAR into app/libs/ and it is
    // picked up here — see "Speech stack" in the README.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Firebase: compiled in always so the code is type-checked, but only initialised at
    // runtime when google-services.json supplied a project. See FirebaseAvailability.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.ai)
    implementation(libs.firebase.appcheck.debug)

    // Gives Firebase's Task API a suspend bridge (`Task.await()`).
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.litert)
    implementation(libs.maps.compose)
    implementation(libs.play.services.location)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Google ADK
    implementation(libs.google.adk.kotlin.core)

    // Places and Maps SDKs
    implementation(libs.places)
}