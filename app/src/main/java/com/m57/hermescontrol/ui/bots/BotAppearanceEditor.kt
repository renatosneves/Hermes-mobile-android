package com.m57.hermescontrol.ui.bots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.R
import com.m57.hermescontrol.theme.BotsPalette
import com.m57.hermescontrol.theme.parseHexColor
import com.m57.hermescontrol.ui.common.resolveAvatarShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Side length of the stored bot image; small enough to live inside the bot's ui_meta. */
private const val BOT_IMAGE_PX = 192

/**
 * Shared look editor for creating and editing a bot: live preview, shape, colour (the same
 * hues the Bots list uses, plus deeper tones) and an optional uploaded image.
 */
@Composable
fun BotAppearanceEditor(
    title: String,
    shape: String,
    colorHex: String,
    imageUrl: String?,
    onShapeChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onImageChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var encoding by remember { mutableStateOf(false) }
    var imageError by remember { mutableStateOf(false) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                encoding = true
                imageError = false
                scope.launch {
                    val encoded = withContext(Dispatchers.IO) { encodeBotImage(context, uri) }
                    encoding = false
                    if (encoded != null) onImageChange(encoded) else imageError = true
                }
            }
        }
    val hue = parseHexColor(colorHex, BotsPalette.Hues[0])

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            BotOrb(
                initials = BotsPresentation.initials(title.ifBlank { "?" }),
                hue = hue,
                size = 72.dp,
                shapeKey = shape,
                imageUrl = imageUrl,
            )
            if (encoding) CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !encoding,
            ) {
                Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (imageUrl == null) R.string.bots_image_upload else R.string.bots_image_change))
            }
            if (imageUrl != null) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { onImageChange(null) }) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.bots_image_remove))
                }
            }
        }
        if (imageError) {
            Text(
                stringResource(R.string.bots_image_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.bots_create_avatar_shape),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotsPresentation.SHAPES.forEach { key ->
                FilterChip(
                    selected = shape == key,
                    onClick = { onShapeChange(key) },
                    label = { Text(key.replaceFirstChar { it.uppercase() }) },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(resolveAvatarShape(key, 14.dp))
                                .background(hue),
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.bots_create_avatar_color),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BotsPresentation.COLOR_OPTIONS.forEach { hex ->
                val swatch = parseHexColor(hex, Color.Transparent)
                val selected = colorHex.equals(hex, ignoreCase = true)
                Box(
                    modifier =
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else BotsPalette.Line,
                                shape = CircleShape,
                            ).clickable { onColorChange(hex) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = if (swatch.luminance() > 0.5f) BotsPalette.Ink else BotsPalette.Fg,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Decode, centre-crop and shrink a picked image into a small JPEG data URL, or null if it can't be read. */
internal fun encodeBotImage(
    context: Context,
    uri: Uri,
): String? =
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val shortest = minOf(bounds.outWidth, bounds.outHeight)
        if (shortest <= 0) return null
        var sample = 1
        while (shortest / (sample * 2) >= BOT_IMAGE_PX) sample *= 2
        val decoded =
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
        val side = minOf(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, BOT_IMAGE_PX, BOT_IMAGE_PX, true)
        val bytes =
            ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
                out.toByteArray()
            }
        "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }.getOrNull()
