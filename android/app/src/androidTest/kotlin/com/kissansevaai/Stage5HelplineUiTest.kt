package com.kissansevaai

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kissansevaai.ui.component.AiHelplineDashboardCard
import com.kissansevaai.ui.theme.KissanSevaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Stage5HelplineUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun helplineCardDisplaysConfiguredNumberLaunchesActionDialAndCopiesNumber() {
        var launchedIntent: Intent? = null
        var copiedNumber: String? = null

        composeRule.setContent {
            KissanSevaTheme {
                AiHelplineDashboardCard(
                    rawHelplineNumber = "+917965861355",
                    onDialLaunch = { launchedIntent = it },
                    onNumberCopied = { copiedNumber = it },
                )
            }
        }

        composeRule.onNodeWithText("Talk to Our AI Farming Assistant").assertIsDisplayed()
        composeRule.onNodeWithText("Get farming assistance by calling our AI helpline. Available on smartphones and basic mobile phones.").assertIsDisplayed()
        composeRule.onNodeWithText("+917965861355").assertIsDisplayed()
        composeRule.onNodeWithText("Call Now").assertIsDisplayed()
        composeRule.onNodeWithText("Copy Number").assertIsDisplayed()

        composeRule.onNodeWithText("Call Now").performClick()
        assertNotNull(launchedIntent)
        assertEquals(Intent.ACTION_DIAL, launchedIntent!!.action)
        assertEquals("tel:+917965861355", launchedIntent!!.data?.toString())

        composeRule.onNodeWithText("Copy Number").performClick()
        assertEquals("+917965861355", copiedNumber)
        val clipboard = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        assertEquals("+917965861355", clipText)
    }

    @Test
    fun helplineCardShowsConfigurationRequiredWhenUnset() {
        composeRule.setContent {
            KissanSevaTheme {
                AiHelplineDashboardCard(rawHelplineNumber = "")
            }
        }

        composeRule.onNodeWithText("Talk to Our AI Farming Assistant").assertIsDisplayed()
        composeRule.onNodeWithText("Helpline configuration required: SARVAM_HELPLINE_NUMBER is missing or invalid.").assertIsDisplayed()
        composeRule.onNodeWithText("Call Now").assertDoesNotExist()
    }
}
