import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

plugins {
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.valueOf(
        libs.findVersion("build-javaVersion").get().requiredVersion
    )
    targetCompatibility = JavaVersion.valueOf(
        libs.findVersion("build-javaVersion").get().requiredVersion
    )
}

dependencies {
    implementation(libs.findLibrary("appium-java-client").get())
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    jvmTarget.set(JvmTarget.fromTarget(libs.findVersion("build-jvmTarget").get().requiredVersion))
    freeCompilerArgs.add(
        "-Xjdk-release=${JavaVersion.valueOf(
            libs.findVersion("build-javaVersion").get().requiredVersion
        )}"
    )
}

val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.compilerOptions {
    jvmTarget.set(JvmTarget.fromTarget(libs.findVersion("build-jvmTarget").get().requiredVersion))
    freeCompilerArgs.add(
        "-Xjdk-release=${JavaVersion.valueOf(
            libs.findVersion("build-javaVersion").get().requiredVersion
        )}"
    )
}
