pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "dessin-android"
include(":app")
// ロジック層(可動域・スイング/ツイスト・ピッキング用のレイ計算)。Android非依存の純Kotlin
include(":core")
