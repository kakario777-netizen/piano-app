plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kakario.piano"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kakario.piano"
        minSdk = 24
        targetSdk = 34
        // 버전은 릴리스 태그(v1.2.3)에서 가져온다
        val ver = (System.getenv("APP_VERSION") ?: "1.0.0").removePrefix("v")
        val parts = ver.split(".").map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0)
        versionName = ver
        versionCode = parts[0] * 10000 + parts[1] * 100 + parts[2]
    }

    // 서명 키는 저장소에 두지 않는다. GitHub Secrets 에서 빌드 때만 꺼내 쓴다.
    val keyFile = file("signing.jks")
    val keyPass = System.getenv("SIGNING_KEY_PASSWORD")
    val hasKey = keyFile.exists() && !keyPass.isNullOrEmpty()

    signingConfigs {
        if (hasKey) {
            create("release") {
                storeFile = keyFile
                storePassword = keyPass
                keyAlias = "piano"
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasKey) signingConfig = signingConfigs.getByName("release")
        }
    }

    androidResources {
        noCompress += listOf("mp3")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
