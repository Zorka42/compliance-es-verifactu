import org.gradle.jvm.application.tasks.CreateStartScripts

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()
    sourceSets {
        jvmMain {
            resources.srcDir(rootProject.file("schemas-aeat"))
            resources.srcDir(rootProject.file("tools/sandbox/examples"))
            resources.include("*.xsd", "offline.properties")
            dependencies {
                implementation(project(":verifactu-aeat"))
                implementation(project(":tools:sandbox"))
            }
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
    description = "Prepares/inspects queries offline; installed launcher additionally supports explicit TEST Keychain consultation."
    workingDir(rootProject.projectDir)
    classpath(jvmCompilation.output.allOutputs, configurations["jvmRuntimeClasspath"])
    mainClass.set("dev.verifactu.tools.query.MainKt")
}

val isMacHost = providers.systemProperty("os.name").map { it.startsWith("Mac") }
val nativeBinary = layout.buildDirectory.file("swift/release/verifactu-query-keychain")

tasks.register<Exec>("buildKeychainHelper") {
    enabled = isMacHost.get()
    inputs.dir("macos/Sources")
    inputs.file("macos/Package.swift")
    outputs.file(nativeBinary)
    commandLine(
        "xcrun",
        "swift",
        "build",
        "--package-path",
        file("macos"),
        "--scratch-path",
        layout.buildDirectory
            .dir("swift")
            .get()
            .asFile,
        "-c",
        "release",
    )
}

tasks.register<Exec>("testKeychainHelper") {
    mustRunAfter("buildKeychainHelper")
    enabled = isMacHost.get()
    commandLine(
        "xcrun",
        "swift",
        "test",
        "--package-path",
        file("macos"),
        "--scratch-path",
        layout.buildDirectory
            .dir("swift")
            .get()
            .asFile,
        "-c",
        "release",
    )
}

tasks.register<CreateStartScripts>("startScripts") {
    dependsOn("jvmJar")
    applicationName = "verifactu-query"
    mainClass.set("dev.verifactu.tools.query.MainKt")
    outputDir =
        layout.buildDirectory
            .dir("scripts")
            .get()
            .asFile
    classpath = files(tasks.named("jvmJar"), configurations["jvmRuntimeClasspath"])
}

tasks.register<CreateStartScripts>("cliStartScripts") {
    dependsOn("jvmJar")
    applicationName = "verifactu"
    mainClass.set("dev.verifactu.tools.query.CliKt")
    outputDir =
        layout.buildDirectory
            .dir("cli-scripts")
            .get()
            .asFile
    classpath = files(tasks.named("jvmJar"), configurations["jvmRuntimeClasspath"])
}

tasks.register<Sync>("installDist") {
    group = "distribution"
    dependsOn("jvmJar", "startScripts", "cliStartScripts", "buildKeychainHelper")
    into(layout.buildDirectory.dir("install/verifactu-query"))
    from(rootProject.file("LICENSE"))
    from(tasks.named("jvmJar")) { into("lib") }
    from(configurations["jvmRuntimeClasspath"]) { into("lib") }
    from(tasks.named("cliStartScripts")) {
        into("bin")
        filePermissions { unix("rwxr-xr-x") }
    }
    from(tasks.named("startScripts")) {
        into("bin")
        filePermissions { unix("rwxr-xr-x") }
    }
    if (isMacHost.get()) {
        from(nativeBinary) {
            into("bin")
            filePermissions { unix("rwxr-xr-x") }
        }
    }
}
