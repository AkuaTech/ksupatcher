import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

fun inferKeystoreType(storeFilePath: String?): String? = when {
    storeFilePath.isNullOrBlank() -> null
    storeFilePath.endsWith(".p12", ignoreCase = true) || storeFilePath.endsWith(".pfx", ignoreCase = true) -> "PKCS12"
    storeFilePath.endsWith(".jks", ignoreCase = true) || storeFilePath.endsWith(".keystore", ignoreCase = true) -> "JKS"
    else -> null
}

android {
    namespace = "org.akuatech.ksupatcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.akuatech.ksupatcher"
        minSdk = 28
        targetSdk = 36
        versionCode = providers.gradleProperty("ciVersionCode").map(String::toInt).orElse(1).get()
        versionName = providers.gradleProperty("ciVersionName").orElse("0.1.0").get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                val storeFilePath = keystoreProperties.getProperty("storeFile")
                val configuredStoreType = keystoreProperties.getProperty("storeType")

                storeFilePath?.let { storeFile = rootProject.file(it) }
                configuredStoreType?.let { storeType = it }
                    ?: inferKeystoreType(storeFilePath)?.let { storeType = it }
                keystoreProperties.getProperty("storePassword")?.let { storePassword = it }
                keystoreProperties.getProperty("keyAlias")?.let { keyAlias = it }
                keystoreProperties.getProperty("keyPassword")?.let { keyPassword = it }
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // split per abi arm64-v8a only
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
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
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.github.rikkahub:hugeicons-compose:1.4")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.documentfile:documentfile:1.1.0")

    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.tukaani:xz:1.12")
}
