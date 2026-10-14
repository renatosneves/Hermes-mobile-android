package com.m57.hermescontrol.ui.settings.components

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.m57.hermescontrol.R
import com.m57.hermescontrol.notification.push.BotLevel
import com.m57.hermescontrol.notification.push.PushRegistrar
import com.m57.hermescontrol.notification.push.PushStatus
import com.m57.hermescontrol.notification.push.QuietHours
import com.m57.hermescontrol.ui.settings.PushSettingsUiState
import com.m57.hermescontrol.ui.settings.SectionCard

/** Callbacks for [PushSection]; grouped so the composable stays readable. */
internal class PushSectionActions(
    val onApprovals: (Boolean) -> Unit,
    val onTasks: (Boolean) -> Unit,
    val onMessages: (Boolean) -> Unit,
    val onScheduled: (Boolean) -> Unit,
    val onBotLevel: (String, BotLevel) -> Unit,
    val onQuietEnabled: (Boolean) -> Unit,
    val onQuietStart: (Int) -> Unit,
    val onQuietEnd: (Int) -> Unit,
    val onSendTest: () -> Unit,
    val onRefresh: () -> Unit,
)

@Composable
internal fun PushSection(
    state: PushSettingsUiState,
    actions: PushSectionActions,
) {
    val context = LocalContext.current
    // Alerts need POST_NOTIFICATIONS on Android 13+. Ask before the first thing that would post one.
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) afterPermission?.invoke()
            afterPermission = null
        }
    val withNotificationPermission: (() -> Unit) -> Unit = { action ->
        val permission = Manifest.permission.POST_NOTIFICATIONS
        val granted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            afterPermission = action
            permissionLauncher.launch(permission)
        }
    }

    SectionCard {
        Text(
            text = stringResource(R.string.settings_push_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        PushStatusBlock(state, actions, withNotificationPermission)
        SectionDivider()
        PushTypeRow(
            title = stringResource(R.string.settings_push_type_approvals),
            desc = stringResource(R.string.settings_push_type_approvals_desc),
            checked = state.prefs.approvals,
            onChange = { on ->
                if (on) withNotificationPermission { actions.onApprovals(true) } else actions.onApprovals(false)
            },
        )
        Spacer(modifier = Modifier.height(12.dp))
        PushTypeRow(
            title = stringResource(R.string.settings_push_type_tasks),
            desc = stringResource(R.string.settings_push_type_tasks_desc),
            checked = state.prefs.tasks,
            onChange = { on ->
                if (on) withNotificationPermission { actions.onTasks(true) } else actions.onTasks(false)
            },
        )
        Spacer(modifier = Modifier.height(12.dp))
        PushTypeRow(
            title = stringResource(R.string.settings_push_type_messages),
            desc = stringResource(R.string.settings_push_type_messages_desc),
            checked = state.prefs.messages,
            onChange = { on ->
                if (on) withNotificationPermission { actions.onMessages(true) } else actions.onMessages(false)
            },
        )
        Spacer(modifier = Modifier.height(12.dp))
        PushTypeRow(
            title = stringResource(R.string.settings_push_type_scheduled),
            desc = stringResource(R.string.settings_push_type_scheduled_desc),
            checked = state.prefs.scheduled,
            onChange = { on ->
                if (on) withNotificationPermission { actions.onScheduled(true) } else actions.onScheduled(false)
            },
        )
        if (state.bots.isNotEmpty()) {
            SectionDivider()
            PushBotLevels(state, actions)
        }
        SectionDivider()
        PushQuietHours(state, actions)
    }
}

@Composable
private fun SectionDivider() {
    Spacer(modifier = Modifier.height(12.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun PushStatusBlock(
    state: PushSettingsUiState,
    actions: PushSectionActions,
    withNotificationPermission: (() -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val statusText =
        when (val status = state.status) {
            is PushStatus.Active -> stringResource(R.string.settings_push_status_active, status.distributorLabel)
            PushStatus.NeedsDistributor -> stringResource(R.string.settings_push_status_needs_distributor)
            PushStatus.NotRegistered -> stringResource(R.string.settings_push_status_not_registered)
        }
    Text(text = statusText, style = MaterialTheme.typography.bodyLarge)
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        when (state.status) {
            PushStatus.NeedsDistributor -> {
                Button(onClick = {
                    openPlayStore(context)
                    actions.onRefresh()
                }) { Text(stringResource(R.string.settings_push_action_install)) }
            }

            PushStatus.NotRegistered -> {
                Button(onClick = {
                    val activity = context.findActivity() ?: return@Button
                    withNotificationPermission { PushRegistrar.activate(activity) { actions.onRefresh() } }
                }) { Text(stringResource(R.string.settings_push_action_setup)) }
            }

            is PushStatus.Active -> {
                OutlinedButton(onClick = actions.onSendTest, enabled = !state.testing) {
                    Text(
                        stringResource(
                            if (state.testing) R.string.settings_push_testing else R.string.settings_push_action_test,
                        ),
                    )
                }
            }
        }
    }
    state.testResult?.let { result ->
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text =
                if (result.ok) {
                    stringResource(R.string.settings_push_test_ok)
                } else {
                    stringResource(
                        R.string.settings_push_test_failed,
                        result.message ?: result.httpStatus?.toString().orEmpty(),
                    )
                },
            style =
                MaterialTheme.typography.bodySmall.copy(
                    color =
                        if (result.ok) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                ),
        )
    }
}

@Composable
private fun PushTypeRow(
    title: String,
    desc: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PushBotLevels(
    state: PushSettingsUiState,
    actions: PushSectionActions,
) {
    Text(text = stringResource(R.string.settings_push_bots_title), style = MaterialTheme.typography.bodyLarge)
    Text(
        text = stringResource(R.string.settings_push_bots_desc),
        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
    )
    state.bots.forEach { bot ->
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = bot.title, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(4.dp))
        val current = state.prefs.levelFor(bot.name)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            BotLevel.entries.forEachIndexed { index, level ->
                SegmentedButton(
                    selected = current == level,
                    onClick = { actions.onBotLevel(bot.name, level) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = BotLevel.entries.size),
                ) {
                    Text(
                        stringResource(
                            when (level) {
                                BotLevel.ALL -> R.string.settings_push_level_all
                                BotLevel.IMPORTANT -> R.string.settings_push_level_important
                                BotLevel.OFF -> R.string.settings_push_level_off
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PushQuietHours(
    state: PushSettingsUiState,
    actions: PushSectionActions,
) {
    var editing by remember { mutableStateOf<QuietEdge?>(null) }
    PushTypeRow(
        title = stringResource(R.string.settings_push_quiet_title),
        desc = stringResource(R.string.settings_push_quiet_desc),
        checked = state.prefs.quietEnabled,
        onChange = actions.onQuietEnabled,
    )
    if (state.prefs.quietEnabled) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuietTimeButton(
                label = stringResource(R.string.settings_push_quiet_start),
                minute = state.prefs.quietStartMinute,
                modifier = Modifier.weight(1f),
                onClick = { editing = QuietEdge.START },
            )
            QuietTimeButton(
                label = stringResource(R.string.settings_push_quiet_end),
                minute = state.prefs.quietEndMinute,
                modifier = Modifier.weight(1f),
                onClick = { editing = QuietEdge.END },
            )
        }
    }
    editing?.let { edge ->
        QuietTimeDialog(
            initialMinute = if (edge == QuietEdge.START) state.prefs.quietStartMinute else state.prefs.quietEndMinute,
            onDismiss = { editing = null },
            onConfirm = { minute ->
                if (edge == QuietEdge.START) actions.onQuietStart(minute) else actions.onQuietEnd(minute)
                editing = null
            },
        )
    }
}

private enum class QuietEdge { START, END }

@Composable
private fun QuietTimeButton(
    label: String,
    minute: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Text("$label ${QuietHours.format(minute)}")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuietTimeDialog(
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val pickerState =
        rememberTimePickerState(
            initialHour = initialMinute / 60,
            initialMinute = initialMinute % 60,
            is24Hour = true,
        )
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.hour * 60 + pickerState.minute) }) {
                Text(stringResource(R.string.settings_push_time_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_push_time_cancel)) }
        },
    )
}

private fun openPlayStore(context: Context) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${PushRegistrar.NTFY_PACKAGE}"))
    val web =
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=${PushRegistrar.NTFY_PACKAGE}"),
        )
    try {
        context.startActivity(market)
    } catch (_: ActivityNotFoundException) {
        runCatching { context.startActivity(web) }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
