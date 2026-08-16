import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.binary.compatibility) apply false
    alias(libs.plugins.kover) apply false
}

allprojects {
    group = "dev.verifactu"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "org.jetbrains.kotlinx.binary-compatibility-validator")
    apply(plugin = "org.jetbrains.kotlinx.kover")

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("detekt.yml"))
        source.setFrom(
            "src/commonMain/kotlin",
            "src/commonTest/kotlin",
            "src/jvmMain/kotlin",
            "src/jvmTest/kotlin",
            "src/androidMain/kotlin",
            "src/androidUnitTest/kotlin",
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
