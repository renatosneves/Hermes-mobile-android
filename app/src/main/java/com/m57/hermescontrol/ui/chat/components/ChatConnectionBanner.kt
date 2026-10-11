package com.m57.hermescontrol.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.ws.ConnectionStatus
import com.m57.hermescontrol.theme.LocalHermesStatusColors
import kotlinx.coroutines.delay

/** A reconnect that finishes within this window never shows the banner. */
private const val RECONNECT_BANNER_GRACE_MS = 1_500L

/**
 * Top-anchored banner shown when the WebSocket connection is in a non-healthy
 * state (RECONNECTING / DISCONNECTED / NO_NETWORK / AUTH_EXPIRED).
 *
 * Extracted from the original ChatScreen.kt — see issue #621.
 */
@Composable
fun ChatConnectionBanner(
    connectionStatus: ConnectionStatus,
    onReconnect: () -> Unit,
    onReloginClick: () -> Unit,
) {
    var reconnectOverdue by remember { mutableStateOf(false) }
    LaunchedEffect(connectionStatus) {
        reconnectOverdue = false
        if (connectionStatus == ConnectionStatus.RECONNECTING) {
            delay(RECONNECT_BANNER_GRACE_MS)
            reconnectOverdue = true
        }
    }
    val isShown =
        when (connectionStatus) {
            ConnectionStatus.CONNECTED, ConnectionStatus.CONNECTING -> false
            ConnectionStatus.RECONNECTING -> reconnectOverdue
            else -> true
        }
    AnimatedVisibility(
        visible = isShown,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = LocalHermesStatusColors.current.errorContainer,
            contentColor = LocalHermesStatusColors.current.error,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (connectionStatus == ConnectionStatus.RECONNECTING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp,
                            color = LocalHermesStatusColors.current.error,
                        )
                    }
                    Text(
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        text =
                            when (connectionStatus) {
                                ConnectionStatus.RECONNECTING -> stringResource(R.string.chat_status_reconnecting)
                                ConnectionStatus.DISCONNECTED -> stringResource(R.string.chat_status_disconnected)
                                ConnectionStatus.NO_NETWORK -> stringResource(R.string.chat_status_no_network)
                                ConnectionStatus.AUTH_EXPIRED -> stringResource(R.string.chat_status_auth_expired)
                                else -> ""
                            },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                when (connectionStatus) {
                    ConnectionStatus.DISCONNECTED -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = onReloginClick,
                                colors =
                                    ButtonDefaults.textButtonColors(
                                        contentColor = LocalHermesStatusColors.current.error,
                                    ),
                            ) {
                                Text(stringResource(R.string.chat_action_relogin))
                            }
                            TextButton(
                                onClick = onReconnect,
                                colors =
                                    ButtonDefaults.textButtonColors(
                                        contentColor = LocalHermesStatusColors.current.error,
                                    ),
                            ) {
                                Text(stringResource(R.string.chat_action_reconnect))
                            }
                        }
                    }

                    ConnectionStatus.NO_NETWORK -> {
                        TextButton(
                            onClick = onReconnect,
                            colors =
                                ButtonDefaults.textButtonColors(
                                    contentColor = LocalHermesStatusColors.current.error,
                                ),
                        ) {
                            Text(stringResource(R.string.chat_action_reconnect))
                        }
                    }

                    ConnectionStatus.AUTH_EXPIRED -> {
                        TextButton(
                            onClick = onReloginClick,
                            colors =
                                ButtonDefaults.textButtonColors(
                                    contentColor = LocalHermesStatusColors.current.error,
                                ),
                        ) {
                            Text(stringResource(R.string.chat_action_relogin))
                        }
                    }

                    ConnectionStatus.RECONNECTING -> {}

                    else -> {}
                }
            }
        }
    }
}
