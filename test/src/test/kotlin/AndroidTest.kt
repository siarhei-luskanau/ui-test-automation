import io.appium.java_client.AppiumBy
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.options.UiAutomator2Options
import java.net.URL
import kotlin.test.Test
import org.openqa.selenium.WebElement

class AndroidTest {

    @Test
    fun test() {
        val options = UiAutomator2Options()
            .setApp(
                "/Users/se/git/temp/ui-test-automation/" +
                    "Multiplatform-App/composeApp/build/outputs/apk/debug/composeApp-debug.apk"
            ).also { options ->
                options.setCapability("ANDROID_HOME", System.getenv("ANDROID_HOME"))
            }
        // The default URL in Appium 1 is http://127.0.0.1:4723/wd/hub
        val androidDriver = AndroidDriver(URL("http://127.0.0.1:4723"), options)
        try {
            val el: WebElement = androidDriver.findElement(
                AppiumBy.xpath("//*[contains(@text, 'Open github')]")
            )
            el.click()
            androidDriver.pageSource
        } finally {
            androidDriver.quit()
        }
    }
}
