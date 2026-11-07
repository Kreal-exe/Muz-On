plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreFile = findProperty("release.keystoreFile")?.toString()

android {
    namespace = "dev.kreal.muzon"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.kreal.muzon"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = findProperty("release.keystorePassword")?.toString()
                keyAlias = findProperty("release.keyAlias")?.toString()
                keyPassword = findProperty("release.keyPassword")?.toString()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val compose = "1.12.1"
    val media3 = "1.10.1"
    val lifecycle = "2.11.0"

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycle")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycle")
    implementation("androidx.compose.ui:ui:$compose")
    implementation("androidx.compose.foundation:foundation:$compose")
    implementation("androidx.compose.animation:animation:$compose")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.palette:palette-ktx:1.1.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.github.chr56:jaudiotagger:0.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.guava:guava:33.4.8-android")

    testImplementation("junit:junit:4.13.2")
}
