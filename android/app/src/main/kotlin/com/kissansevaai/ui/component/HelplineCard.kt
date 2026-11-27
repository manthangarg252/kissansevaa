package com.kissansevaai.ui.component

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kissansevaai.BuildConfig
import com.kissansevaai.R
import com.kissansevaai.core.common.HelplineStatus
import com.kissansevaai.core.common.SarvamHelplineConfig
import com.kissansevaai.ui.theme.*

/**
 * Dashboard card exposing the configured external Sarvam AI Farmer Helpline number.
 * Uses Intent.ACTION_DIAL strictly (no CALL_PHONE permission, no automatic call placement),
 * with accessible >= 48dp touch targets and clipboard copy fallback.
 */
@Composable
fun AiHelplineDashboardCard(
    modifier: Modifier = Modifier,
    rawHelplineNumber: String = BuildConfig.SARVAM_HELPLINE_NUMBER,
    onDialLaunch: ((Intent) -> Unit)? = null,
    onNumberCopied: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val status: HelplineStatus = remember(rawHelplineNumber) {
        SarvamHelplineConfig.resolve(rawHelplineNumber)
    }
    var actionFeedback by remember { mutableStateOf<String?>(null) }

    val copiedTemplate = stringResource(R.string.helpline_copied_feedback)
    val dialerMissingTemplate = stringResource(R.string.helpline_dialer_unavailable)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.5.dp, GreenTint),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = GreenTint,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PhoneInTalk,
                            contentDescription = null,
                            tint = DeepEmerald,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.helpline_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextCharcoal,
                    )
                    Surface(
                        shape = RoundedCornerShape(9999.dp),
                        color = GreenBg,
                        border = BorderStroke(1.dp, GreenTint),
                    ) {
                        Text(
                            text = stringResource(R.string.helpline_basic_phone_note),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = DeepEmerald,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.helpline_card_desc),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )

            if (status.isConfigured && status.dialUriString != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GreenBg,
                    border = BorderStroke(1.dp, GreenTint),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = status.displayNumber,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.8.sp,
                            ),
                            fontWeight = FontWeight.ExtraBold,
                            color = DeepEmerald,
                        )
                        Text(
                            text = "24x7 AI Voice Helpline • Dial ${status.displayNumber} from any phone",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextCharcoal,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = {
                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse(status.dialUriString))
                            if (onDialLaunch != null) {
                                onDialLaunch(dialIntent)
                            } else {
                                try {
                                    context.startActivity(dialIntent)
                                } catch (_: ActivityNotFoundException) {
                                    actionFeedback = String.format(dialerMissingTemplate, status.displayNumber)
                                } catch (_: Exception) {
                                    actionFeedback = String.format(dialerMissingTemplate, status.displayNumber)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald),
                        shape = RoundedCornerShape(9999.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.helpline_call_now),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("KissanSevaAI Helpline", status.dialNumber))
                            actionFeedback = String.format(copiedTemplate, status.displayNumber)
                            onNumberCopied?.invoke(status.dialNumber)
                        },
                        border = BorderStroke(1.5.dp, DeepEmerald),
                        shape = RoundedCornerShape(9999.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = DeepEmerald,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.helpline_copy_number),
                            color = DeepEmerald,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AmberTint,
                    border = BorderStroke(1.dp, HarvestAmber),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = SecondaryOrange,
                            modifier = Modifier.size(20.dp),
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.helpline_unconfigured),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextCharcoal,
                            )
                            status.validationError?.let { err ->
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SecondaryOrange,
                                )
                            }
                        }
                    }
                }
            }

            actionFeedback?.let { note ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceContainerLow,
                    border = BorderStroke(1.dp, BorderLight),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelSmall,
                        color = DeepEmerald,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
