plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    explicitApi()
    androidTarget {
        publishLibraryVariants("release")
    }
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    macosX64()
    macosArm64()
    sourceSets {
        jvmMain { kotlin.srcDir("src/jvmAndAndroidMain/kotlin") }
        androidMain { kotlin.srcDir("src/jvmAndAndroidMain/kotlin") }
        commonMain.dependencies {
            api(project(":verifactu-core"))
            api(project(":verifactu-xml"))
            api(project(":verifactu-qr"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "dev.verifactu.aeat"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
}
