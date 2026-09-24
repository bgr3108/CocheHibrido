plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val umpTestDeviceHashedId = providers.gradleProperty("umpTestDeviceHashedId").orNull.orEmpty()
val googleTestAdmobAppId = "ca-app-pub-3940256099942544~3347511713"
val googleTestBannerAdUnitId = "ca-app-pub-3940256099942544/9214589741"
val releaseAdmobAppIdProvider = providers.gradleProperty("kilonomReleaseAdmobAppId")
val releaseAdmobBannerAdUnitIdProvider = providers.gradleProperty("kilonomReleaseAdmobBannerAdUnitId")
// Keep these values empty during configuration so debug builds remain self-contained. The
// release verification task below rejects empty, malformed, or Google test identifiers.
val releaseAdmobAppId = releaseAdmobAppIdProvider.orElse("").get()
val releaseAdmobBannerAdUnitId = releaseAdmobBannerAdUnitIdProvider.orElse("").get()
val resetUmpConsentForDebug = providers.gradleProperty("resetUmpConsentForDebug")
    .orNull
    .equals("true", ignoreCase = true)

android {
    namespace = "com.bgr3108.kilonom"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bgr3108.kilonom"
        minSdk = 26
        targetSdk = 37
        versionCode = 10
        versionName = "1.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "ADS_ENABLED", "false")
        buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", "\"\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = googleTestAdmobAppId
            buildConfigField("boolean", "ADS_ENABLED", "true")
            buildConfigField(
                "boolean",
                "RESET_UMP_CONSENT_FOR_DEBUG",
                resetUmpConsentForDebug.toString()
            )
            buildConfigField("String", "UMP_TEST_DEVICE_HASHED_ID", "\"$umpTestDeviceHashedId\"")
            buildConfigField(
                "String",
                "ADMOB_BANNER_AD_UNIT_ID",
                "\"$googleTestBannerAdUnitId\""
            )
        }
        release {
            manifestPlaceholders["releaseAdmobAppId"] = releaseAdmobAppId
            buildConfigField("boolean", "ADS_ENABLED", "true")
            buildConfigField(
                "String",
                "ADMOB_BANNER_AD_UNIT_ID",
                "\"$releaseAdmobBannerAdUnitId\""
            )
            buildConfigField("boolean", "RESET_UMP_CONSENT_FOR_DEBUG", "false")
            buildConfigField("String", "UMP_TEST_DEVICE_HASHED_ID", "\"\"")
            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

val verifyReleaseAdMobConfiguration = tasks.register("verifyReleaseAdMobConfiguration") {
    group = "verification"
    description = "Verifies that release advertising uses local production AdMob identifiers."

    doLast {
        fun requireProductionIdentifier(name: String, value: String, pattern: Regex) {
            check(value.isNotBlank()) {
                "Missing $name. Supply it through an untracked Gradle property; release builds never fall back to test IDs."
            }
            check(value.matches(pattern)) {
                "$name has an invalid AdMob identifier format."
            }
            check(!value.contains("3940256099942544")) {
                "$name must not use Google's official test identifier in release."
            }
        }

        requireProductionIdentifier(
            name = "kilonomReleaseAdmobAppId",
            value = releaseAdmobAppIdProvider.orNull.orEmpty(),
            pattern = Regex("ca-app-pub-\\d+~\\d+")
        )
        requireProductionIdentifier(
            name = "kilonomReleaseAdmobBannerAdUnitId",
            value = releaseAdmobBannerAdUnitIdProvider.orNull.orEmpty(),
            pattern = Regex("ca-app-pub-\\d+/\\d+")
        )
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyReleaseAdMobConfiguration)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // ✅ Compose extra
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.appcompat)

    // Debug uses Google's official test identifiers; release identifiers are supplied locally.
    implementation(libs.google.mobile.ads)
    implementation(libs.google.ump)

    // The map is rendered from the local station cache. It does not require a map API key.
    implementation(libs.maplibre.compose)
    runtimeOnly(libs.maplibre.compose.runtime.vulkan.android)
    implementation(libs.kotlinx.serialization.json)

    // ROOM
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    // DATASTORE
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    // tests...
}
