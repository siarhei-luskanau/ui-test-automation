package convention.environment.setup

import java.io.InputStream

interface ExecWrapper {
    fun exec(
        commandLine: List<String>,
        inputStream: InputStream? = null,
        addToSystemEnvironment: Map<String, String>? = null
    ): String
}
