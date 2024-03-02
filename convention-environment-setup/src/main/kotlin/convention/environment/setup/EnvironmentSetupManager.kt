package convention.environment.setup

class EnvironmentSetupManager(
    private val execWrapper: ExecWrapper
) {
    fun setupDependencies(
        appiumVersion: String
    ) {
        checkDependency(
            onSuccessMessage = "brew is installed",
            onFailureMessage = "brew is required to install npm and appium",
            checking = {
                execWrapper.exec(listOf("brew", "-v")).also { println("brew version: $it") }
            },
            installing = { throw Error("Install Homebrew https://brew.sh/") }
        )

        println("installing the npm...")
        checkDependency(
            onSuccessMessage = "npm is installed",
            onFailureMessage = "npm is NOT installed",
            checking = {
                execWrapper.exec(listOf("npm", "-v")).also { println("npm version: $it") }
            },
            installing = { execWrapper.exec(listOf("brew", "install", "node")) }
        )

        println("installing appium...")
        checkDependency(
            onSuccessMessage = "appium is installed",
            onFailureMessage = "appium is NOT installed",
            checking = {
                val output = execWrapper.exec(listOf("appium", "-v"))
                println("appium version: $output")
                if (output.contains(appiumVersion).not()) {
                    println("unexpected appium version $output")
                    execWrapper.exec(listOf("npm", "uninstall", "-g", "appium"))
                    execWrapper.exec(listOf("appium", "-v"))
                }
            },
            installing = { execWrapper.exec(listOf("npm", "install", "-g", "appium@$appiumVersion")) }
        )

        println("installing appium driver list...")
        val appiumDriverList = listOf("xcuitest", "uiautomator2", "espresso")
        checkDependency(
            onSuccessMessage = "appium driver list is installed",
            onFailureMessage = "appium driver list is NOT installed",
            checking = {
                val output = execWrapper.exec(listOf("appium", "driver", "list", "--json", "--installed"))
                println("appium driver list: $output")
                appiumDriverList.forEach {
                    if (output.contains(it).not()) {
                        throw Error("appium driver $it is NOT installed")
                    }
                }
            },
            installing = {
                val output = execWrapper.exec(listOf("appium", "driver", "list", "--json", "--installed"))
                appiumDriverList.forEach {
                    if (output.contains(it).not()) {
                        execWrapper.exec(listOf("appium", "driver", "install", it))
                    }
                }
            }
        )
    }

    private fun checkDependency(
        onSuccessMessage: String? = null,
        onFailureMessage: String? = null,
        checking: () -> Unit,
        installing: () -> Unit
    ) {
        runCatching { checking.invoke() }
            .onFailure { originalError ->
                originalError.printStackTrace()
                runCatching {
                    installing.invoke()
                    // double check if dependency is installed
                    checking.invoke()
                }
                    .onFailure { error -> throw Error(onFailureMessage, error) }
                    .onSuccess { onSuccessMessage?.also { println(it) } }
            }
            .onSuccess { onSuccessMessage?.also { println(onSuccessMessage) } }
    }
}