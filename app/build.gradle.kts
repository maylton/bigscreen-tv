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
        versionCode = 1
        versionName = "0.1.0"
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
