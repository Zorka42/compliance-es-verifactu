plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    explicitApi()
    android {
        namespace = "dev.verifactu.xml"
        compileSdk = 36
        minSdk = 26
        withHostTest {}
    }
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
        jvmTest {
            resources.srcDir(rootProject.file("schemas-aeat"))
        }
    }
}
