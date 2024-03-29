rootProject.name = "UiTestAutomation"
include(
    "test"
)

pluginManagement {
    includeBuild("convention-android-emulator")
    includeBuild("convention-environment-setup")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
