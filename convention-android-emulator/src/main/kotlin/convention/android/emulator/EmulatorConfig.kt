package convention.android.emulator

data class EmulatorConfig(
    val avdName: String,
    val sdkId: String,
    val deviceType: String,
    val port: String
)

val ANDROID_EMULATORS = listOf(
    EmulatorConfig(
        avdName = "test_android_emulator_x86_64",
        sdkId = "system-images;android-28;google_apis;x86_64",
        deviceType = "21",
        port = "5574"
    ),
    EmulatorConfig(
        avdName = "test_android_emulator_arm64-v8a",
        sdkId = "system-images;android-28;google_apis;arm64-v8a",
        deviceType = "21",
        port = "5578"
    )
)
