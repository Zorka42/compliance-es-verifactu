plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()
    sourceSets {
        jvmMain {
            resources.srcDir(rootProject.file("schemas-aeat"))
            resources.include("*.xsd")
        }
        jvmTest {
            resources.srcDir(rootProject.file("schemas-aeat"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

val jvmCompilation =
    kotlin.targets
        .getByName("jvm")
        .compilations
        .getByName("main")

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Prepares query XML or inspects a saved response locally; never connects to AEAT."
    workingDir(rootProject.projectDir)
    classpath(jvmCompilation.output.allOutputs, configurations["jvmRuntimeClasspath"])
    mainClass.set("dev.verifactu.tools.query.MainKt")
}
