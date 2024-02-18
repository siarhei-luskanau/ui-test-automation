import convention.android.emulator.AndroidSdkHelper
import convention.android.emulator.ExecWrapper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.Properties
import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    id("androidEmulatorConvention")
}

allprojects {
    apply(from = "$rootDir/ktlint.gradle")
}

val ciGroup = "CI_GRADLE"

tasks.register("ciTest") {
    group = ciGroup
    doLast {
        val execWrapper: ExecWrapper = object : ExecWrapper {
            override fun exec(commandLine: List<String>, inputStream: InputStream?): String =
                runExec(commands = commandLine, inputStream = inputStream)
        }
        AndroidSdkHelper(
            rootDir = rootDir,
            execWrapper = execWrapper
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
            Thread { it.runAndroidEmulator(avdName = avdName) }.start()
            it.waitAndroidEmulator()
            it.killAndroidEmulator()
            it.deleteAndroidEmulator()
        }
    }
}

tasks.register("ciIos") {
    group = ciGroup
    doLast {
        if (Os.isFamily(Os.FAMILY_MAC)) {
            runExec(listOf("brew", "install", "kdoctor"))
            runExec(listOf("kdoctor"))
            val deviceId = runExec(
                listOf(
                    "xcrun",
                    "simctl",
                    "list",
                    "devices",
                    "available"
                )
            )
                .lines()
                .filter {
                    listOf("iphone 15", "iphone 14").any { device -> it.contains(device, true) } &&
                        it.contains("(") && it.contains(")")
                }
                .map { it.substring(startIndex = it.indexOf("(") + 1, endIndex = it.indexOf(")")) }
                .firstOrNull()
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
                    "id=$deviceId",
                    "-allowProvisioningDeviceRegistration",
                    "-allowProvisioningUpdates"
                )
            )
        }
    }
}

fun runExec(commands: List<String>, inputStream: InputStream? = null): String =
    ByteArrayOutputStream().let { resultOutputStream ->
        exec {
            if (System.getenv("JAVA_HOME") == null) {
                System.getProperty("java.home")?.let { javaHome ->
                    environment = environment.toMutableMap().apply {
                        put("JAVA_HOME", javaHome)
                    }
                }
            }
            commandLine = commands
            inputStream?.also { standardInput = inputStream }
            standardOutput = resultOutputStream
            println("commandLine: ${this.commandLine.joinToString(separator = " ")}")
        }.apply { println("ExecResult: $this") }
        String(resultOutputStream.toByteArray()).trim().also { println(it) }
    }

fun gradlew(vararg tasks: String, addToSystemProperties: Map<String, String>? = null) {
    exec {
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
