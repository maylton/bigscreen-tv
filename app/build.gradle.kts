plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.maylton.bigscreen.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.maylton.bigscreen.tv"
        minSdk = 28
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        buildConfigField("String", "UPDATE_URL", "\"https://api.github.com/repos/maylton/caelestia-bigscreen-tv/releases/latest\"")
    }

    buildFeatures {
        buildConfig = true
    }

    // Release key kept outside the repository (see README); without it the release build is unsigned
    val keystore = providers.gradleProperty("bigscreen.keystore").orNull
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = providers.gradleProperty("bigscreen.keystore.password").get()
                keyAlias = "bigscreen"
                keyPassword = providers.gradleProperty("bigscreen.keystore.password").get()
            }
        }
    }

    buildTypes {
        debug {
            // Lets the update flow be tested against a local server
            providers.gradleProperty("bigscreen.updateUrl").orNull?.let { buildConfigField("String", "UPDATE_URL", "\"$it\"") }
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.4.0")
}
