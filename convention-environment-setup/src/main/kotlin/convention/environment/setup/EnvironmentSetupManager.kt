package convention.environment.setup

fun setupDependencies(runExec: (List<String>) -> String, appiumVersion: String) {
    checkDependency(
        onSuccessMessage = "brew is installed",
        onFailureMessage = "brew is required to install npm and appium",
        checking = { runExec(listOf("brew", "-v")).also { println("brew version: $it") } },
        installing = { throw Error("Install Homebrew https://brew.sh/") }
    )

    println("installing the npm...")
    checkDependency(
        onSuccessMessage = "npm is installed",
        onFailureMessage = "npm is NOT installed",
        checking = { runExec(listOf("npm", "-v")).also { println("npm version: $it") } },
        installing = { runExec(listOf("brew", "install", "node")) }
    )

    println("installing appium...")
    checkDependency(
        onSuccessMessage = "appium is installed",
        onFailureMessage = "appium is NOT installed",
        checking = {
            val output = runExec(listOf("appium", "-v"))
            println("appium version: $output")
            if (output.contains(appiumVersion).not()) {
                println("unexpected appium version $output")
                runExec(listOf("npm", "uninstall", "-g", "appium"))
                runExec(listOf("appium", "-v"))
            }
        },
        installing = { runExec(listOf("npm", "install", "-g", "appium@$appiumVersion")) }
    )

    println("installing appium driver list...")
    val appiumDriverList = listOf("xcuitest", "uiautomator2", "espresso")
    checkDependency(
        onSuccessMessage = "appium driver list is installed",
        onFailureMessage = "appium driver list is NOT installed",
        checking = {
            val output = runExec(listOf("appium", "driver", "list", "--json", "--installed"))
            println("appium driver list: $output")
            appiumDriverList.forEach {
                if (output.contains(it).not()) {
                    throw Error("appium driver $it is NOT installed")
                }
            }
        },
        installing = {
            val output = runExec(listOf("appium", "driver", "list", "--json", "--installed"))
            appiumDriverList.forEach {
                if (output.contains(it).not()) {
                    runExec(listOf("appium", "driver", "install", it))
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
