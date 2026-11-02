plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.fasdeq.vpn"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = (project.findProperty("app.id") as String?) ?: "app.fasdeq.vpn"
        minSdk = 26
        targetSdk = 36
        versionCode = (project.findProperty("app.vcode") as String?)?.toInt() ?: 27
        versionName = (project.findProperty("app.vname") as String?) ?: "3.6"
        buildConfigField("boolean", "FDROID", (project.findProperty("app.fdroid") != null).toString())
        manifestPlaceholders["appLabel"] = (project.findProperty("app.label") as String?) ?: "Tunn"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        create("release") {
            val path = project.findProperty("ks.path") as String?
            if (path != null) {
                storeFile = file(path)
                storePassword = project.property("ks.pass") as String
                keyAlias = project.property("ks.alias") as String
                keyPassword = project.property("ks.pass") as String
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

configurations.all {
    resolutionStrategy {
        force("androidx.core:core-ktx:1.15.0", "androidx.core:core:1.15.0", "org.jetbrains.kotlin:kotlin-stdlib:2.0.21", "org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.0.21", "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.21", "org.jetbrains.kotlin:kotlin-reflect:2.0.21")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation(files("libs/libv2ray.aar"))
    implementation("com.zaneschepke:amneziawg-android:2.3.7")
}
