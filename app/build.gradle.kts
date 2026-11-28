import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 签名信息外移：keystore.properties 不入库（见 .gitignore）
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties()
if (keystorePropsFile.exists()) {
    keystorePropsFile.inputStream().use { stream -> keystoreProps.load(stream) }
}
val hasSigningKey = keystorePropsFile.exists() &&
    !keystoreProps.getProperty("storePassword").isNullOrBlank()

// 版本号唯一来源：versionName / versionCode 与关于页展示的 BuildConfig 字段共用
val appVersionCode = 54
val appVersionName = "2.21.0"

android {
    namespace = "com.lihoo.socialcrop"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lihoo.socialcrop"
        minSdk = 24
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        // 关于页读这两个字段，避免 UI 里写死版本导致过期
        buildConfigField("String", "APP_VERSION", "\"$appVersionName\"")
        buildConfigField("int", "APP_VERSION_CODE", "$appVersionCode")
        buildConfigField("String", "AUTHOR_NAME", "\"Lihoo\"")
        // 仓库地址：与 README / 关于页保持一致
        buildConfigField("String", "GITHUB_URL", "\"https://github.com/Amatorelover/socialcrop\"")
    }

    signingConfigs {
        create("personal") {
            if (hasSigningKey) {
                val storeFilePath = keystoreProps.getProperty("storeFile") ?: "app/keystore/app.jks"
                storeFile = rootProject.file(storeFilePath)
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias") ?: "socialcrop"
                keyPassword = keystoreProps.getProperty("keyPassword") ?: keystoreProps.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasSigningKey) signingConfigs.getByName("personal") else null
        }
        debug {
            signingConfig = if (hasSigningKey) signingConfigs.getByName("personal") else null
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
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
