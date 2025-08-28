import convention.android.emulator.AndroidSdkHelper
import convention.environment.setup.setupDependencies
import groovy.json.JsonSlurper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.Properties
import org.apache.tools.ant.taskdefs.condition.Os

println("gradle.startParameter.taskNames: ${gradle.startParameter.taskNames}")
System.getProperties().forEach { key, value -> println("System.getProperties(): $key=$value") }
System.getenv().forEach { (key, value) -> println("System.getenv(): $key=$value") }

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("androidEmulatorConvention")
    id("environmentSetupConvention")
}

allprojects {
    apply(from = "$rootDir/ktlint.gradle")
}

val ciGroup = "CI_GRADLE"

tasks.register("ciAutomationTest") {
    group = ciGroup
    val injected = project.objects.newInstance<Injected>()
    val libs = project.extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
    doLast {
        setupDependencies(
            runExec = { injected.runExec(commands = it) },
            appiumVersion = libs.findVersion("appium-npm").get().requiredVersion
        )

        AndroidSdkHelper(
            projectLayout = injected.projectLayout,
            execWrapper = object : convention.android.emulator.ExecWrapper {
                override fun exec(
                    commandLine: List<String>,
                    inputStream: InputStream?,
                    addToSystemEnvironment: Map<String, String>?
                ): String = injected.runExec(
                    commands = commandLine,
                    inputStream = inputStream,
                    addToSystemEnvironment = addToSystemEnvironment
                )
            }
        ).also {
            it.setupAndroidCmdlineTools()
            val avdName = when (val osArch = System.getProperty("os.arch")) {
                "x86", "i386", "ia-32", "i686", "x86_64", "amd64", "x64", "x86-64" ->
                    "test_android_emulator_x86_64"

                "arm", "arm-v7", "armv7", "arm32", "arm64", "arm-v8", "aarch64" ->
                    "test_android_emulator_arm64-v8a"

                else ->
                    throw Error("Unexpected System.getProperty(\"os.arch\") = $osArch")
            }
            it.setupAndroidSDK(avdName = avdName)
            it.setupAndroidEmulator(avdName = avdName)
            val emulatorThread = Thread { it.runAndroidEmulator(avdName = avdName) }
            emulatorThread.start()
            it.waitAndroidEmulator()
            it.killAndroidEmulator()
            it.deleteAndroidEmulator()
            emulatorThread.join()
        }
    }
}

tasks.register("ciLint") {
    group = ciGroup
    val injected = project.objects.newInstance<Injected>()
    doLast {
        injected.gradlew(
            "lint",
            workingDirectory = "Multiplatform-App"
        )
    }
}

tasks.register("ciDesktop") {
    group = ciGroup
    val injected = project.objects.newInstance<Injected>()
    doLast {
        injected.gradlew(
            ":composeApp:jvmJar",
            workingDirectory = "Multiplatform-App"
        )
    }
}

tasks.register("ciBrowser") {
    group = ciGroup
    val injected = project.objects.newInstance<Injected>()
    doLast {
        injected.gradlew(
            ":composeApp:wasmJsMainClasses",
            workingDirectory = "Multiplatform-App"
        )
    }
}

tasks.register("ciAndroid") {
    group = ciGroup
    val injected = project.objects.newInstance<Injected>()
    doLast {
        injected.gradlew(
            "assembleDebug",
            workingDirectory = "Multiplatform-App"
        )
    }
}

tasks.register("ciIos") {
    group = ciGroup
    val injected = project.objects.newInstance<Injected>()
    doLast {
        if (Os.isFamily(Os.FAMILY_MAC)) {
            injected.runExec(listOf("brew", "install", "kdoctor"))
            injected.runExec(listOf("kdoctor"))
            val devicesJson = injected.runExec(
                listOf(
                    "xcrun",
                    "simctl",
                    "list",
                    "devices",
                    "available",
                    "-j"
                )
            )

            @Suppress("UNCHECKED_CAST")
            val devicesList = (JsonSlurper().parseText(devicesJson) as Map<String, *>)
                .let { it["devices"] as Map<String, *> }
                .let { devicesMap ->
                    devicesMap.keys
                        .filter { it.startsWith("com.apple.CoreSimulator.SimRuntime.iOS") }
                        .map { devicesMap[it] as List<*> }
                }
                .map { jsonArray -> jsonArray.map { it as Map<String, *> } }
                .flatten()
                .filter { it["isAvailable"] as Boolean }
                .filter {
                    listOf("iphone 1").any { device ->
                        (it["name"] as String).contains(device, true)
                    }
                }
            println("Devices:${devicesList.joinToString { "\n" + it["udid"] + ": " + it["name"] }}")
            val device = devicesList.firstOrNull()
            println("Selected:\n${device?.get("udid")}: ${device?.get("name")}")
            val rootDirPath = injected.projectLayout.projectDirectory.asFile.path
            injected.runExec(
                listOf(
                    "xcodebuild",
                    "-project",
                    "$rootDirPath/Multiplatform-App/iosApp/iosApp.xcodeproj",
                    "-scheme",
                    "iosApp",
                    "-configuration",
                    "Debug",
                    "OBJROOT=$rootDirPath/build/ios",
                    "SYMROOT=$rootDirPath/build/ios",
                    "-destination",
                    "id=${device?.get("udid")}",
                    "-allowProvisioningDeviceRegistration",
                    "-allowProvisioningUpdates"
                )
            )
        }
    }
}

abstract class Injected {

    @get:Inject
    abstract val fs: FileSystemOperations

    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Inject
    abstract val projectLayout: ProjectLayout

    fun runExec(
        commands: List<String>,
        inputStream: InputStream? = null,
        addToSystemEnvironment: Map<String, String>? = null
    ): String = object : ByteArrayOutputStream() {
        override fun write(p0: ByteArray, p1: Int, p2: Int) {
            print(String(p0, p1, p2))
            super.write(p0, p1, p2)
        }
    }.let { resultOutputStream ->
        execOperations.exec {
            commandLine = commands
            workingDir = projectLayout.projectDirectory.asFile
            environment = environment.toMutableMap().apply {
                System.getenv("HOME")?.also { put("HOME", it) }
                if (System.getenv("JAVA_HOME") == null) {
                    System.getProperty("java.home")?.let { javaHome ->
                        put("JAVA_HOME", javaHome)
                    }
                }
                addToSystemEnvironment?.also { putAll(addToSystemEnvironment) }
            }
            inputStream?.also { standardInput = inputStream }
            standardOutput = resultOutputStream
            println("commandLine: ${this.commandLine.joinToString(separator = " ")}")
        }.apply { println("ExecResult: $this") }
        String(resultOutputStream.toByteArray())
    }

    fun gradlew(
        vararg tasks: String,
        addToSystemProperties: Map<String, String>? = null,
        workingDirectory: String? = null
    ) {
        execOperations.exec {
            commandLine = mutableListOf<String>().also { mutableArgs ->
                mutableArgs.add(
                    "./" +
                        // if (workingDirectory != null) "$workingDirectory/" else ""     +
                        if (Os.isFamily(Os.FAMILY_WINDOWS)) "gradlew.bat" else "gradlew"
                )
                mutableArgs.addAll(tasks)
                addToSystemProperties?.toList()?.map { "-D${it.first}=${it.second}" }?.let {
                    mutableArgs.addAll(it)
                }
                mutableArgs.add("--stacktrace")
            }
            workingDirectory?.also {
                workingDir = File(projectLayout.projectDirectory.asFile, workingDirectory)
            }
            val sdkDirPath = Properties().apply {
                val propertiesFile = projectLayout.projectDirectory.file("local.properties").asFile
                if (propertiesFile.exists()) {
                    load(propertiesFile.inputStream())
                }
            }.getProperty("sdk.dir")
            if (sdkDirPath != null) {
                val platformToolsDir = "$sdkDirPath${File.separator}platform-tools"
                val pathEnvironment = System.getenv("PATH").orEmpty()
                if (!pathEnvironment.contains(platformToolsDir)) {
                    environment = environment.toMutableMap().apply {
                        put("PATH", "$platformToolsDir:$pathEnvironment")
                    }
                }
            }
            if (System.getenv("JAVA_HOME") == null) {
                System.getProperty("java.home")?.let { javaHome ->
                    environment = environment.toMutableMap().apply {
                        put("JAVA_HOME", javaHome)
                    }
                }
            }
            if (System.getenv("ANDROID_HOME") == null) {
                environment = environment.toMutableMap().apply {
                    put("ANDROID_HOME", sdkDirPath)
                }
            }
            println("commandLine: ${this.commandLine}")
        }.apply { println("ExecResult: ${this.exitValue}") }
    }
}
