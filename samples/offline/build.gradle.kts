plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    macosArm64()
    sourceSets {
        commonMain.dependencies {
            implementation(project(":verifactu-core"))
            implementation(project(":verifactu-xml"))
            implementation(project(":verifactu-qr"))
            implementation(project(":verifactu-aeat"))
            implementation(project(":verifactu-testkit"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(11)
}

val jvmCompilation =
    kotlin.targets
        .getByName("jvm")
        .compilations
        .getByName("main")

tasks.register<JavaExec>("runKotlinSample") {
    group = "application"
    description = "Runs registration and cancellation through a fake transport without network or credentials."
    classpath(jvmCompilation.output.allOutputs, configurations["jvmRuntimeClasspath"])
    mainClass.set("dev.verifactu.sample.MainKt")
}

tasks.register<JavaExec>("runJavaSample") {
    group = "application"
    description = "Compiles and runs the Java consumer example without network or credentials."
    classpath(jvmCompilation.output.allOutputs, configurations["jvmRuntimeClasspath"])
    mainClass.set("dev.verifactu.sample.JavaExample")
}

tasks.named("check") {
    dependsOn("runKotlinSample", "runJavaSample")
}
