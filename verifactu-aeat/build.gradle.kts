plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    explicitApi()
    android {
        namespace = "dev.verifactu.aeat"
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
        jvmTest {
            resources.srcDir(rootProject.file("schemas-aeat"))
            resources.include("errores.properties")
        }
    }
}
