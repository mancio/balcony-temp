package com.balcony.temp

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Espresso smoke test for the main screen.
 *
 * Uses the real public Firebase endpoint, so it needs a network connection on the device.
 * Run with: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityEspressoTest {

    @Test
    fun theScreenShowsItsControls() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText(R.string.title)).check(matches(isDisplayed()))
            onView(withId(R.id.refreshButton)).check(matches(isDisplayed()))
            onView(withId(R.id.addWidgetButton)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun tappingRefreshDoesNotCrash() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.refreshButton)).perform(click())
            onView(withId(R.id.refreshButton)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun theReadingOrAnErrorIsEventuallyShown() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val deadline = System.currentTimeMillis() + 20_000
            var settled = false
            while (System.currentTimeMillis() < deadline && !settled) {
                scenario.onActivity { activity ->
                    val content = activity.findViewById<android.view.View>(R.id.content)
                    val error = activity.findViewById<android.view.View>(R.id.errorText)
                    settled = content.visibility == android.view.View.VISIBLE ||
                        error.visibility == android.view.View.VISIBLE
                }
                if (!settled) Thread.sleep(250)
            }
            org.junit.Assert.assertTrue("Neither a reading nor an error appeared", settled)
        }
    }
}
