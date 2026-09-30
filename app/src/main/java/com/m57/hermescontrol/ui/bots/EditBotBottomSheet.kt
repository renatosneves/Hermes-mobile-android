package com.m57.hermescontrol.ui.bots

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.model.BotAvatarMeta
import com.m57.hermescontrol.data.model.ProfileInfo
import com.m57.hermescontrol.theme.parseHexColor
import com.m57.hermescontrol.ui.common.BotAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBotBottomSheet(
    bot: ProfileInfo,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, shape: String, color: String, imageUrl: String?) -> Unit,
    onDelete: () -> Unit,
    currentImage: String? =
        bot
            .botMeta()
            ?.avatar
            ?.image_url
            ?.takeIf { it.isNotBlank() },
    onGenerate: (suspend (String) -> Result<String>)? = null,
) {
    val meta = bot.botMeta()
    var title by remember { mutableStateOf(meta?.title ?: bot.effectiveTitle) }
    var description by remember { mutableStateOf(meta?.description ?: bot.effectiveDescription) }
    var selectedShape by remember { mutableStateOf(meta?.avatar?.shape ?: "circle") }
    var selectedColor by remember { mutableStateOf(BotsPresentation.colorHex(bot)) }
    var selectedImage by remember(currentImage) { mutableStateOf(currentImage) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = stringResource(R.string.bots_edit_delete_confirm_title, bot.effectiveTitle),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(stringResource(R.string.bots_edit_delete_confirm_msg))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                ) {
                    Text(stringResource(R.string.bots_edit_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.bots_edit_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.bots_create_title_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.bots_create_desc_label)) },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            BotAppearanceEditor(
                title = title,
                shape = selectedShape,
                colorHex = selectedColor,
                imageUrl = selectedImage,
                onShapeChange = { selectedShape = it },
                onColorChange = { selectedColor = it },
                onImageChange = { selectedImage = it },
                generatePrompt = BotsPresentation.avatarPrompt(title, description),
                onGenerate = onGenerate,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    onSave(title.trim(), description.trim(), selectedShape, selectedColor, selectedImage)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.bots_edit_save))
            }

            if (bot.name != "default") {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.bots_edit_delete))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
