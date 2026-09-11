plugins {
    application
}

repositories {
    maven {
        url = uri("../../build/maven-repository")
        metadataSources {
            mavenPom()
            ignoreGradleMetadataRedirection()
        }
    }
    mavenCentral()
}

val verifactuVersion: String = providers.gradleProperty("verifactuVersion").orElse("0.1.0-SNAPSHOT").get()

dependencies {
    implementation("io.github.zorka42:verifactu-core-jvm:$verifactuVersion")
    implementation("io.github.zorka42:verifactu-xml-jvm:$verifactuVersion")
    implementation("io.github.zorka42:verifactu-qr-jvm:$verifactuVersion")
    implementation("io.github.zorka42:verifactu-aeat-jvm:$verifactuVersion")
    // Only the offline sample needs a fake transport on its runtime classpath.
    implementation("io.github.zorka42:verifactu-testkit-jvm:$verifactuVersion")
}

sourceSets.main {
    java.setSrcDirs(listOf("../offline/src/jvmMain/java"))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(11)
    options.compilerArgs.add("-Xlint:all")
    options.compilerArgs.add("-Werror")
}

application {
    mainClass.set("dev.verifactu.sample.JavaExample")
}
