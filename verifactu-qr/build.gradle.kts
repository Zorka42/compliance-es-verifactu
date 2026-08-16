plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    explicitApi()
    androidTarget()
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    macosX64()
    macosArm64()
    sourceSets {
        commonMain.dependencies {
            api(project(":verifactu-core"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "dev.verifactu.qr"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
}
