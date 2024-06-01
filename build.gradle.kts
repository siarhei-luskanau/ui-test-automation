import convention.android.emulator.AndroidSdkHelper
import convention.environment.setup.EnvironmentSetupManager
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

tasks.register("devAll") {
    group = ciGroup
    doLast {
        gradlew(
            "clean",
            "ktlintFormat"
        )
        gradlew(
            "ciLint",
            "ciUnitTest",
            "ciAndroid",
            "ciDesktop",
            "ciIos",
            "ciBrowser"
        )
        gradlew("ciAutomationTest")
    }
}

tasks.register("ciAutomationTest") {
    group = ciGroup
    doLast {
        val environmentSetupExecWrapper: convention.environment.setup.ExecWrapper =
            object : convention.environment.setup.ExecWrapper {
                override fun exec(
                    commandLine: List<String>,
                    inputStream: InputStream?,
                    addToSystemEnvironment: Map<String, String>?
                ): String = runExec(
                    commands = commandLine,
                    inputStream = inputStream,
                    addToSystemEnvironment = addToSystemEnvironment
                )
            }
        EnvironmentSetupManager(execWrapper = environmentSetupExecWrapper).setupDependencies(
            appiumVersion = libs.versions.appium.npm.get()
        )

        runCatching { runExec(commands = listOf("pkill", "-9", "-f", "appium")) }
        Thread {
            runCatching {
                runExec(
                    commands = listOf(
                        "appium",
                        "--address",
                        "127.0.0.1",
                        "--port",
                        "4723"
                    )
                )
            }
        }.start()

        val androidEmulatorExecWrapper: convention.android.emulator.ExecWrapper =
            object : convention.android.emulator.ExecWrapper {
                override fun exec(
                    commandLine: List<String>,
                    inputStream: InputStream?,
                    addToSystemEnvironment: Map<String, String>?
                ): String = runExec(
                    commands = commandLine,
                    inputStream = inputStream,
                    addToSystemEnvironment = addToSystemEnvironment
                )
            }
        val androidSdkHelper = AndroidSdkHelper(
            rootDir = rootDir,
            execWrapper = androidEmulatorExecWrapper
        ).also { androidSdkHelper ->
            androidSdkHelper.setupAndroidCmdlineTools()
            val avdName = when (val osArch = System.getProperty("os.arch")) {
                "x86", "i386", "ia-32", "i686", "x86_64", "amd64", "x64", "x86-64" ->
                    "test_android_emulator_x86_64"

                "arm", "arm-v7", "armv7", "arm32", "arm64", "arm-v8", "aarch64" ->
                    "test_android_emulator_arm64-v8a"

                else ->
                    throw Error("Unexpected System.getProperty(\"os.arch\") = $osArch")
            }
            androidSdkHelper.setupAndroidSDK(avdName = avdName)
            androidSdkHelper.setupAndroidEmulator(avdName = avdName)
            Thread { androidSdkHelper.runAndroidEmulator(avdName = avdName) }.start()
            androidSdkHelper.waitAndroidEmulator()
        }
        try {
            gradlew("test")
        } finally {
            androidSdkHelper.killAndroidEmulator()
            androidSdkHelper.deleteAndroidEmulator()
            runCatching { runExec(commands = listOf("pkill", "-9", "-f", "appium")) }
        }
    }
}

tasks.register("ciLint") {
    group = ciGroup
    doLast {
        gradlew(
            "lint",
            workingDirectory = File(rootDir, "Multiplatform-App")
        )
    }
}

tasks.register("ciUnitTest") {
    group = ciGroup
    doLast {
        gradlew(
            ":composeApp:jvmTest",
            // ":composeApp:jsBrowserTest",
            // ":composeApp:wasmJsBrowserTest",
            // ":composeApp:testReleaseUnitTest",
            workingDirectory = File(rootDir, "Multiplatform-App")
        )
        when (val osArch = System.getProperty("os.arch")) {
            "x86", "i386", "ia-32", "i686",
            "x86_64", "amd64", "x64", "x86-64" -> ":composeApp:iosSimulatorX64Test"

            "arm", "arm-v7", "armv7", "arm32",
            "arm64", "arm-v8", "aarch64" -> ":composeApp:iosSimulatorArm64Test"

            else -> throw Error(
                "Unexpected System.getProperty(\"os.arch\") = $osArch"
            )
        }.let {
            gradlew(it, workingDirectory = File(rootDir, "Multiplatform-App"))
        }
    }
}

tasks.register("ciDesktop") {
    group = ciGroup
    doLast {
        gradlew(
            ":composeApp:jvmJar",
            workingDirectory = File(rootDir, "Multiplatform-App")
        )
    }
}

tasks.register("ciBrowser") {
    group = ciGroup
    doLast {
        gradlew(
            ":composeApp:wasmJsMainClasses",
            workingDirectory = File(rootDir, "Multiplatform-App")
        )
    }
}

tasks.register("ciAndroid") {
    group = ciGroup
    doLast {
        gradlew(
            "clean",
            "assembleDebug",
            workingDirectory = File(rootDir, "Multiplatform-App")
        )
    }
}

tasks.register("ciIos") {
    group = ciGroup
    doLast {
        if (Os.isFamily(Os.FAMILY_MAC)) {
            runExec(listOf("brew", "install", "kdoctor"))
            runExec(listOf("kdoctor"))
            val devicesJson = runExec(
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
            runExec(
                listOf(
                    "xcodebuild",
                    "-project",
                    "${rootDir.path}/Multiplatform-App/iosApp/iosApp.xcodeproj",
                    "-scheme",
                    "iosApp",
                    "-configuration",
                    "Debug",
                    "OBJROOT=${rootDir.path}/build/ios",
                    "SYMROOT=${rootDir.path}/build/ios",
                    "-destination",
                    "id=${device?.get("udid")}",
                    "-allowProvisioningDeviceRegistration",
                    "-allowProvisioningUpdates"
                )
            )
        }
    }
}

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
    @Suppress("DEPRECATION")
    exec {
        commandLine = commands
        workingDir = rootDir
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
    workingDirectory: File? = null
) {
    providers.exec {
        executable = File(
            project.rootDir,
            if (Os.isFamily(Os.FAMILY_WINDOWS)) "gradlew.bat" else "gradlew"
        )
            .also { it.setExecutable(true) }
            .absolutePath
        args = mutableListOf<String>().also { mutableArgs ->
            mutableArgs.addAll(tasks)
            addToSystemProperties?.toList()?.map { "-D${it.first}=${it.second}" }?.let {
                mutableArgs.addAll(it)
            }
            mutableArgs.add("--stacktrace")
        }
        workingDirectory?.also { workingDir = workingDirectory }
        val sdkDirPath = Properties().apply {
            val propertiesFile = File(rootDir, "local.properties")
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
    }.apply { println("ExecResult: $this") }
}
