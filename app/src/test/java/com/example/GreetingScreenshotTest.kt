package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34]) // Use SDK 34 for robust stability
class GreetingScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun greeting_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Render sample scan result cards to test visual rendering of status badges
                    ResultItem(
                        result = ScanResult(
                            ip = "192.168.1.1",
                            port = 80,
                            statusCode = 200,
                            statusText = "OK",
                            responseTimeMs = 120,
                            isLive = true,
                            protocol = "http"
                        )
                    )
                    ResultItem(
                        result = ScanResult(
                            ip = "192.168.1.5",
                            port = 443,
                            statusCode = 404,
                            statusText = "Not Found",
                            responseTimeMs = 240,
                            isLive = true,
                            protocol = "https"
                        )
                    )
                    ResultItem(
                        result = ScanResult(
                            ip = "10.0.0.1",
                            port = 8080,
                            statusCode = 0,
                            statusText = "Timeout",
                            responseTimeMs = 4000,
                            isLive = false,
                            error = "Read Timeout",
                            protocol = "http"
                        )
                    )
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
    }
}
