import org.gradle.jvm.application.tasks.CreateStartScripts

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()
    sourceSets {
        jvmMain {
            resources.srcDir(rootProject.file("schemas-aeat"))
            resources.include("*.xsd")
            dependencies {
                implementation(project(":verifactu-aeat"))
                implementation(project(":verifactu-testkit"))
            }
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
    description = "Prepares sandbox scenarios, previews XML, simulates responses, or explicitly sends to AEAT TEST."
    workingDir(rootProject.projectDir)
    classpath(jvmCompilation.output.allOutputs, configurations["jvmRuntimeClasspath"])
    mainClass.set("dev.verifactu.tools.sandbox.MainKt")
    standardInput = System.`in`
}

tasks.register<CreateStartScripts>("startScripts") {
    dependsOn("jvmJar")
    applicationName = "verifactu-sandbox"
    mainClass.set("dev.verifactu.tools.sandbox.MainKt")
    outputDir =
        layout.buildDirectory
            .dir("scripts")
            .get()
            .asFile
    classpath = files(tasks.named("jvmJar"), configurations["jvmRuntimeClasspath"])
}

tasks.register<Sync>("installDist") {
    group = "distribution"
    description = "Installs a standalone Java 11+ sandbox CLI without publishing artifacts."
    dependsOn("jvmJar", "startScripts")
    into(layout.buildDirectory.dir("install/verifactu-sandbox"))
    from(rootProject.file("LICENSE"))
    from(tasks.named("jvmJar")) { into("lib") }
    from(configurations["jvmRuntimeClasspath"]) { into("lib") }
    from(tasks.named("startScripts")) {
        into("bin")
        filePermissions { unix("rwxr-xr-x") }
    }
}
