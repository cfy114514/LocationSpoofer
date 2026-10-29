package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.data.model.RouteTravelMode
import com.vincenthzr.locationspoofer.ui.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The real dialog receives local test state only; it does not construct the app or start simulation. */
class RouteConfigTestActivity : ComponentActivity() {
    var state by mutableStateOf(AppState(useRealRoute = true))
    var deferSpeedUpdate = false
    var pendingSpeed: Double? = null
    var speedAtStart: Double? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                RouteConfigDialog(uiState = state, onDismiss = {}, onStartRoute = { speedAtStart = state.customSpeedMs },
                    onRunModeChange = { state = state.copy(routeRunMode = it) },
                    onSpeedChange = { state = state.copy(routeSimMode = it) },
                    onCustomSpeedChange = { if (deferSpeedUpdate) pendingSpeed = it else state = state.copy(customSpeedMs = it) },
                    onRouteTravelModeChange = { state = state.copy(routeTravelMode = it) },
                    onSatelliteCountChange = { state = state.copy(satelliteCountInput = it) })
            }
        }
    }
}

@RunWith(AndroidJUnit4::class)
class RouteConfigAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<RouteConfigTestActivity>()

    @Test fun customSpeedAcceptsDecimalsAndBlocksInvalidStart() {
        compose.onNodeWithText(compose.activity.getString(R.string.custom))
            .performScrollTo().performClick()
        val input = compose.onNodeWithText(compose.activity.getString(R.string.route_speed_input))
        val start = compose.onNodeWithText(compose.activity.getString(R.string.start_route_simulation))
        input.performScrollTo().performTextReplacement("1.25")
        compose.runOnIdle { assertEquals(1.25, compose.activity.state.customSpeedMs, 0.00001) }
        start.assertIsEnabled()
        start.performClick()
        compose.runOnIdle { assertEquals(1.25, compose.activity.speedAtStart!!, 0.00001) }
        listOf("", "0", "46", "NaN").forEach { invalid ->
            input.performScrollTo().performTextReplacement(invalid)
            start.assertIsNotEnabled()
            compose.runOnIdle { assertEquals(1.25, compose.activity.state.customSpeedMs, 0.00001) }
        }
        input.performScrollTo().performTextReplacement("0,2")
        compose.runOnIdle { assertEquals(0.2, compose.activity.state.customSpeedMs, 0.00001) }
        start.assertIsEnabled()
        input.performScrollTo().performTextReplacement("45")
        compose.runOnIdle { assertEquals(45.0, compose.activity.state.customSpeedMs, 0.00001) }
    }

    @Test fun delayedParentEchoDoesNotOverwriteIncompleteInput() {
        compose.onNodeWithText(compose.activity.getString(R.string.custom)).performScrollTo().performClick()
        val input = compose.onNodeWithText(compose.activity.getString(R.string.route_speed_input))
        val start = compose.onNodeWithText(compose.activity.getString(R.string.start_route_simulation))
        compose.runOnIdle { compose.activity.deferSpeedUpdate = true }
        input.performScrollTo().performTextReplacement("2")
        input.performTextReplacement("")
        start.assertIsNotEnabled()
        compose.runOnIdle {
            val activity = compose.activity
            activity.state = activity.state.copy(customSpeedMs = activity.pendingSpeed!!)
        }
        start.assertIsNotEnabled()
    }

    @Test fun travelModesAndScrolledTextFieldRemainUsable() {
        val labels = listOf(R.string.walking to RouteTravelMode.WALKING,
            R.string.cycling to RouteTravelMode.CYCLING, R.string.driving to RouteTravelMode.DRIVING)
        labels.forEach { (label, expected) ->
            // Speed presets use some of the same labels above the route-mode chips.
            compose.onAllNodesWithText(compose.activity.getString(label)).onLast()
                .performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(expected, compose.activity.state.routeTravelMode) }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.satellite_count))
            .performScrollTo().assertIsDisplayed().performClick().performTextReplacement("12")
        compose.runOnIdle { assertEquals("12", compose.activity.state.satelliteCountInput) }
    }
}
