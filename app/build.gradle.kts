import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Development signing key. It is committed on purpose so that anyone cloning the
 * repository can produce an installable release build. It is NOT a production key:
 * replace it before publishing anywhere.
 */
val devKeystore = rootProject.file("keystore/sereno-dev.jks")

android {
    namespace = "app.sereno.weather"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.sereno.weather"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    androidResources {
        localeFilters += listOf("en", "it")
    }

    signingConfigs {
        create("dev") {
            if (devKeystore.exists()) {
                storeFile = devKeystore
                storePassword = "serenodev"
                keyAlias = "sereno"
                keyPassword = "serenodev"
            }
            // minSdk is 26, so the v1 JAR signature is dead weight; v2 and v3
            // cover every device this app can be installed on.
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // Kept off for the first cut so the shipped APK is byte-for-byte what
            // was tested; the R8 rules in proguard-rules.pro are ready for when it
            // is switched on.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (devKeystore.exists()) signingConfig = signingConfigs.getByName("dev")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
            )
        }
    }

    testOptions {
        unitTests {
            // Robolectric renders the real Compose UI to a bitmap on the JVM,
            // which is the only way to actually look at these screens without a
            // device; that needs merged Android resources on the test classpath.
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        disable += setOf("MissingTranslation", "UnusedResources")
    }
}

/**
 * Robolectric normally downloads its Android runtime jar itself, at test time,
 * over its own HTTP client. Letting Gradle resolve it instead means it comes
 * through the same repositories (and the same proxy configuration) as every
 * other dependency, and that tests can then run fully offline.
 */
val robolectricRuntime: Configuration by configurations.creating

val prepareRobolectricJars by tasks.registering(Copy::class) {
    from(robolectricRuntime)
    into(layout.buildDirectory.dir("robolectric-jars"))
}

tasks.withType<Test>().configureEach {
    dependsOn(prepareRobolectricJars)
    systemProperty("robolectric.offline", "true")
    systemProperty(
        "robolectric.dependency.dir",
        layout.buildDirectory.dir("robolectric-jars").get().asFile.absolutePath,
    )
    // Screenshot rendering needs real Skia rather than Robolectric's no-op canvas.
    systemProperty("robolectric.graphicsMode", "NATIVE")
    maxHeapSize = "2g"
}

dependencies {
    // Both SDK levels Robolectric may pick for this project.
    robolectricRuntime("org.robolectric:android-all-instrumented:14-robolectric-10818077-i7")
    robolectricRuntime("org.robolectric:android-all-instrumented:15-robolectric-12650502-i7")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.compose.ui.test.manifest)
}
