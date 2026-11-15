package app.planner

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsNotificationsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun str(id: Int) = rule.activity.getString(id)

    @Test fun settingsShowWhatThePhoneMustAllowAndOfferATestNotification() {
        rule.onNodeWithContentDescription(str(R.string.settings)).performClick()
        rule.onNodeWithText(str(R.string.notifications_section)).assertExists()
        rule.onNodeWithText(str(R.string.perm_notifications)).assertExists()
        rule.onNodeWithText(str(R.string.perm_exact)).assertExists()
        rule.onNodeWithText(str(R.string.perm_battery)).assertExists()
        rule.onNodeWithText(str(R.string.diag_test)).performScrollTo().assertExists()
        rule.onNodeWithText(str(R.string.diag_reschedule)).performScrollTo().assertExists()
    }
}
