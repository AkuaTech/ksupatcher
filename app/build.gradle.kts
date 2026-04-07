import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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
    compileSdk = 35

    defaultConfig {
        applicationId = "org.akuatech.ksupatcher"
        minSdk = 28
        targetSdk = 35
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.documentfile:documentfile:1.1.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
