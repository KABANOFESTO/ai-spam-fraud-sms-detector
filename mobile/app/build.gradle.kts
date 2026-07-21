plugins {
    id("com.android.application") version "8.5.2-fixed"
}

android {
    namespace = "com.smsai.smsfrauddetector"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.smsai.smsfrauddetector"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "DEFAULT_BASE_URL", "\"http://10.0.2.2:8000/\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main").java.setSrcDirs(listOf("src/main/simplified/java"))
    }
}

dependencies {
}
