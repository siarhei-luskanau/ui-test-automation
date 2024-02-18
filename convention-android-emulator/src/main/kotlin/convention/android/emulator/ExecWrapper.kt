package convention.android.emulator

import java.io.InputStream

interface ExecWrapper {
    fun exec(commandLine: List<String>, inputStream: InputStream? = null): String
}
