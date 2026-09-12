plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.nova.engine"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.nova.engine"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables { useSupportLibrary = true }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
    ndkVersion = "27.2.12479018"
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug"; isMinifyEnabled = false }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    packaging { resources { excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*") } }
}

kotlin { jvmToolchain(17) }
