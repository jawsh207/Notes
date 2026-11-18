plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// version.txt: line 1 is the version code, line 2 the version name.
val versionLines = rootProject.file("version.txt").readLines().map { it.trim() }

android {
    namespace = "app.folio.notes"
    // Material 3 Expressive (1.5) needs compileSdk 37 and AGP 9.1+.
    compileSdk = 37

    defaultConfig {
        applicationId = "app.folio.notes"
        minSdk = 31 // Wallpaper colours
        targetSdk = 36
        versionCode = versionLines[0].toInt()
        versionName = versionLines[1]
    }

    // Unit tests run on the computer, not a phone. Android calls the tested code happens to make
    // return empty defaults instead of failing.
    testOptions { unitTests.isReturnDefaultValues = true }

    // CI supplies the key through ORG_GRADLE_PROJECT_RELEASE_* (the dev key for test builds,
    // the release key for releases). Without it, builds are signed with the debug key.
    val storeFilePath = findProperty("RELEASE_STORE_FILE") as String?
    signingConfigs {
        if (storeFilePath != null) {
            create("ci") {
                storeFile = file(storeFilePath)
                storePassword = findProperty("RELEASE_STORE_PASSWORD") as String?
                keyAlias = findProperty("RELEASE_KEY_ALIAS") as String?
                keyPassword = findProperty("RELEASE_KEY_PASSWORD") as String?
            }
        }
    }

    buildTypes {
        release {
            // R8: shrink, optimize and obfuscate; drop unused resources.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("ci") ?: signingConfigs.getByName("debug")
        }
        // Test builds: quick to build and inspectable, with their own app id so they install
        // beside the release.
        create("dev") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            matchingFallbacks += "release"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
}

// APKs are named notes-dev.apk / notes-release.apk.
base.archivesName.set("notes")

// A failing unit test prints what was expected and what came out, so CI can show it.
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
    }
}
