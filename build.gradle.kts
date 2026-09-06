import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.jvm.tasks.Jar
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.binary.compatibility) apply false
    alias(libs.plugins.kover) apply false
}

allprojects {
    group = "io.github.zorka42"
    version = "0.1.0-SNAPSHOT"
}

tasks.register("publishJvmPreview") {
    group = "publishing"
    description = "Builds unsigned JVM publications in build/maven-repository without external publication."
    dependsOn(
        listOf("core", "xml", "qr", "aeat", "testkit").map {
            ":verifactu-$it:publishJvmPublicationToLocalPreviewRepository"
        },
    )
}

subprojects {
    if (name == "samples" || name == "tools") return@subprojects
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "org.jetbrains.kotlinx.binary-compatibility-validator")
    apply(plugin = "org.jetbrains.kotlinx.kover")

    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions.configure<KotlinMultiplatformExtension> {
            targets.withType<KotlinJvmTarget>().configureEach {
                compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
            }
        }
    }

    tasks.withType<DokkaTask>().configureEach {
        offlineMode.set(true)
        notCompatibleWithConfigurationCache("Dokka 2.0 V1 tasks retain Gradle configurations during execution.")
    }

    if (name.startsWith("verifactu-")) {
        apply(plugin = "maven-publish")
        tasks.withType<Jar>().configureEach {
            isPreserveFileTimestamps = false
            isReproducibleFileOrder = true
            from(rootProject.file("LICENSE")) { into("META-INF") }
        }
        val javadocJar = tasks.register<Jar>("javadocJar") {
            archiveClassifier.set("javadoc")
            from(tasks.named("dokkaHtml"))
        }
        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "LocalPreview"
                    url = rootProject.layout.buildDirectory.dir("maven-repository").get().asFile.toURI()
                }
            }
            publications.withType<MavenPublication>().configureEach {
                artifact(javadocJar)
                pom {
                    name.set(project.name)
                    description.set("Kotlin Multiplatform VERI*FACTU fiscal records, XML, QR, and optional AEAT integration.")
                    url.set("https://github.com/Zorka42/compliance-es-verifactu")
                    licenses {
                        license {
                            name.set("Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("Zorka42")
                            name.set("Zorka42")
                            url.set("https://github.com/Zorka42")
                        }
                    }
                    scm {
                        url.set("https://github.com/Zorka42/compliance-es-verifactu")
                        connection.set("scm:git:https://github.com/Zorka42/compliance-es-verifactu.git")
                        developerConnection.set("scm:git:ssh://git@github.com/Zorka42/compliance-es-verifactu.git")
                    }
                }
            }
        }
    }

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("detekt.yml"))
        source.setFrom(
            "src/commonMain/kotlin",
            "src/commonTest/kotlin",
            "src/jvmMain/kotlin",
            "src/jvmAndAndroidMain/kotlin",
            "src/jvmTest/kotlin",
            "src/androidMain/kotlin",
            "src/androidHostTest/kotlin",
            "src/appleMain/kotlin",
            "src/appleTest/kotlin",
        )
    }

    if (name == "verifactu-core") {
        extensions.configure<KoverProjectExtension> {
            reports {
                verify {
                    rule {
                        minBound(90, CoverageUnit.LINE, AggregationType.COVERED_PERCENTAGE)
                    }
                }
            }
        }
    }

    tasks.matching { it.name == "check" }.configureEach {
        dependsOn("ktlintCheck", "detekt", "apiCheck")
    }
}
