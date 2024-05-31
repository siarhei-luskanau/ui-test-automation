rootProject.name = "UiTestAutomation"
include(
    "test"
)

pluginManagement {
    includeBuild("convention-android-emulator")
    includeBuild("convention-automation-core")
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
