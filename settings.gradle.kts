pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "verifactu-kmp"

include(
    ":verifactu-core",
    ":verifactu-xml",
    ":verifactu-qr",
    ":verifactu-aeat",
    ":verifactu-testkit",
)
