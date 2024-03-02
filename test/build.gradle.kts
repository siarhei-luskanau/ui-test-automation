import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.valueOf(libs.versions.build.javaVersion.get())
    targetCompatibility = JavaVersion.valueOf(libs.versions.build.javaVersion.get())
}

dependencies {
    implementation(libs.appium.java.client)
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = libs.versions.build.jvmTarget.get()
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = libs.versions.build.jvmTarget.get()
}
