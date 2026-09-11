plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android { namespace = "com.nova.engine"; compileSdk = 35
    defaultConfig { applicationId = "com.nova.engine"; minSdk = 24; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
    ndkVersion = "27.2.12479018"
    buildTypes { release { isMinifyEnabled = false; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
}

kotlin { jvmToolchain(17) }
