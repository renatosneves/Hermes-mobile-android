package com.m57.hermescontrol.ui.chat.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m57.hermescontrol.R
import com.m57.hermescontrol.theme.BotsPalette
import com.m57.hermescontrol.ui.bots.BotOrb
import com.m57.hermescontrol.ui.chat.VoiceLiveCalls
import com.m57.hermescontrol.voice.VoiceAsk
import com.m57.hermescontrol.voice.VoiceLivePhase

/**
 * Full-screen GPT-Live voice chat over the open conversation: one big orb that listens,
 * thinks and speaks, live captions, and mute / end. Requests go to the chat's bot as messages.
 */
@Composable
fun VoiceLiveOverlay(call: VoiceLiveCalls.Call) {
    val context = LocalContext.current
    val controller = call.controller
    val title = call.title
    val ui by controller.ui.collectAsStateWithLifecycle()
    var micDenied by remember { mutableStateOf(false) }
    val hue = MaterialTheme.colorScheme.primary

    val micPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) controller.start() else micDenied = true
        }

    // The call lives in VoiceLiveCalls, so a fold or unfold that rebuilds this screen carries on.
    LaunchedEffect(call) {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) controller.startIfIdle() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    val close = { VoiceLiveCalls.hangUp() }

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(BotsPalette.PaneTop, BotsPalette.Ink)))
                    .drawBehind {
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(hue.copy(alpha = 0.28f), Color.Transparent),
                                center = Offset(size.width / 2f, size.height * 0.4f),
                                radius = size.minDimension * 0.7f,
                            ),
                        )
                    }.statusBarsPadding()
                    .navigationBarsPadding()
                    .testTag("voice_live_overlay"),
        ) {
            IconButton(onClick = close, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.voice_live_close),
                    tint = BotsPalette.Fg,
                )
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, color = BotsPalette.Fg, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(
                    stringResource(R.string.voice_live_badge),
                    color = BotsPalette.Muted,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.weight(1f))
                LiveOrb(phase = ui.phase, hue = hue, initials = title.take(2).uppercase(), imageUrl = call.imageUrl)
                Spacer(Modifier.height(24.dp))
                Text(
                    text =
                        when {
                            micDenied -> stringResource(R.string.voice_live_mic_denied)
                            else -> phaseLabel(ui.phase, ui.working)
                        },
                    color = if (ui.phase == VoiceLivePhase.SPEAKING) hue else BotsPalette.Fg,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                if (ui.message != null && ui.phase in setOf(VoiceLivePhase.UNAVAILABLE, VoiceLivePhase.ENDED)) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        ui.message.orEmpty(),
                        color = BotsPalette.Muted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 420.dp),
                    )
                }
                if (ui.phase == VoiceLivePhase.UNAVAILABLE) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.voice_live_setup_hint),
                        color = BotsPalette.Faint,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 420.dp),
                    )
                }
                ui.ask?.let { ask ->
                    Spacer(Modifier.height(16.dp))
                    AskCard(
                        ask = ask,
                        title = title,
                        hue = hue,
                        onApprove = { call.owner.respondToApproval("approve") },
                        onDeny = { call.owner.respondToApproval("deny") },
                        onOpenChat = { VoiceLiveCalls.setMinimised(true) },
                    )
                }
                Spacer(Modifier.weight(1f))
                Captions(user = ui.userCaption, voice = ui.voiceCaption, hue = hue)
                Spacer(Modifier.height(24.dp))
                Controls(
                    phase = ui.phase,
                    muted = ui.muted,
                    onMute = controller::toggleMute,
                    onEnd = close,
                    onRetry = controller::start,
                )
            }
        }
    }
}

@Composable
private fun phaseLabel(
    phase: VoiceLivePhase,
    working: String?,
): String =
    when (phase) {
        VoiceLivePhase.CHECKING -> {
            stringResource(R.string.voice_live_checking)
        }

        VoiceLivePhase.UNAVAILABLE -> {
            stringResource(R.string.voice_live_unavailable)
        }

        VoiceLivePhase.CONNECTING -> {
            stringResource(R.string.voice_live_connecting)
        }

        VoiceLivePhase.LISTENING -> {
            stringResource(R.string.voice_live_listening)
        }

        VoiceLivePhase.THINKING -> {
            working?.let { stringResource(R.string.voice_live_working, it) }
                ?: stringResource(R.string.voice_live_thinking)
        }

        VoiceLivePhase.SPEAKING -> {
            stringResource(R.string.voice_live_speaking)
        }

        VoiceLivePhase.ENDED -> {
            stringResource(R.string.voice_live_ended)
        }
    }

@Composable
private fun LiveOrb(
    phase: VoiceLivePhase,
    hue: Color,
    initials: String,
    imageUrl: String?,
) {
    val transition = rememberInfiniteTransition(label = "live-orb")
    val breathe by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec =
            infiniteRepeatable(
                tween(if (phase == VoiceLivePhase.SPEAKING) 380 else 1800),
                RepeatMode.Reverse,
            ),
        label = "live-orb-scale",
    )
    val active = phase in setOf(VoiceLivePhase.LISTENING, VoiceLivePhase.SPEAKING, VoiceLivePhase.THINKING)
    Box(
        modifier = Modifier.size(200.dp).scale(if (active) breathe else 1f),
        contentAlignment = Alignment.Center,
    ) {
        BotOrb(
            initials = initials,
            hue = if (active || phase == VoiceLivePhase.CONNECTING) hue else BotsPalette.Idle,
            size = 150.dp,
            working = phase == VoiceLivePhase.THINKING || phase == VoiceLivePhase.CONNECTING,
            imageUrl = imageUrl,
        )
    }
}

@Composable
private fun Captions(
    user: String,
    voice: String,
    hue: Color,
) {
    Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (user.isNotBlank()) {
            Text(
                user.takeLast(240),
                color = BotsPalette.Muted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (voice.isNotBlank()) {
            Text(
                voice.takeLast(320),
                color = BotsPalette.Fg,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.drawBehind { drawCircle(hue.copy(alpha = 0f)) },
            )
        }
    }
}

@Composable
private fun Controls(
    phase: VoiceLivePhase,
    muted: Boolean,
    onMute: () -> Unit,
    onEnd: () -> Unit,
    onRetry: () -> Unit,
) {
    if (phase == VoiceLivePhase.ENDED || phase == VoiceLivePhase.UNAVAILABLE) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (phase == VoiceLivePhase.ENDED) {
                OutlinedButton(onClick = onRetry, modifier = Modifier.testTag("voice_live_retry")) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.voice_live_again), modifier = Modifier.padding(start = 6.dp))
                }
            }
            OutlinedButton(onClick = onEnd) { Text(stringResource(R.string.voice_live_close)) }
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.Top) {
        LabelledControl(
            label = stringResource(if (muted) R.string.voice_live_muted else R.string.voice_live_mute),
        ) {
            FilledIconButton(
                onClick = onMute,
                modifier = Modifier.size(64.dp).testTag("voice_live_mute"),
                shape = CircleShape,
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (muted) BotsPalette.Fg else BotsPalette.Deck2,
                        contentColor = if (muted) BotsPalette.Ink else BotsPalette.Fg,
                    ),
            ) {
                Icon(
                    if (muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription =
                        stringResource(
                            if (muted) R.string.voice_live_unmute else R.string.voice_live_mute,
                        ),
                )
            }
        }
        LabelledControl(label = stringResource(R.string.voice_live_end)) {
            FilledIconButton(
                onClick = onEnd,
                modifier = Modifier.size(64.dp).testTag("voice_live_end"),
                shape = CircleShape,
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = BotsPalette.Offline,
                        contentColor = BotsPalette.Ink,
                    ),
            ) {
                Icon(Icons.Filled.CallEnd, contentDescription = stringResource(R.string.voice_live_end))
            }
        }
    }
}

@Composable
private fun LabelledControl(
    label: String,
    button: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        button()
        Spacer(Modifier.height(6.dp))
        Text(label, color = BotsPalette.Muted, fontSize = 12.sp)
    }
}

/** What the bot stopped to ask, with the answer buttons (a question is answered in the chat). */
@Composable
private fun AskCard(
    ask: VoiceAsk,
    title: String,
    hue: Color,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onOpenChat: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .background(BotsPalette.Deck2, RoundedCornerShape(16.dp))
                .padding(16.dp)
                .testTag("voice_live_ask"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text =
                when (ask.kind) {
                    VoiceAsk.Kind.APPROVAL -> stringResource(R.string.voice_live_ask_approval, title, ask.text)
                    VoiceAsk.Kind.QUESTION -> stringResource(R.string.voice_live_ask_question, title, ask.text)
                    VoiceAsk.Kind.SECRET -> stringResource(R.string.voice_live_ask_secret, title)
                },
            color = BotsPalette.Fg,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (ask.kind == VoiceAsk.Kind.APPROVAL) {
                OutlinedButton(onClick = onDeny, modifier = Modifier.testTag("voice_live_deny")) {
                    Text(stringResource(R.string.voice_live_deny))
                }
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = hue, contentColor = BotsPalette.Ink),
                    modifier = Modifier.testTag("voice_live_approve"),
                ) { Text(stringResource(R.string.voice_live_approve)) }
            } else {
                Button(
                    onClick = onOpenChat,
                    colors = ButtonDefaults.buttonColors(containerColor = hue, contentColor = BotsPalette.Ink),
                    modifier = Modifier.testTag("voice_live_open_chat"),
                ) { Text(stringResource(R.string.voice_live_answer_in_chat)) }
            }
        }
    }
}
