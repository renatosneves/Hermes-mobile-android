package com.m57.hermescontrol.diagnostics

import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import com.m57.hermescontrol.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** After a freeze or crash, offers to share what the app recorded, so it can be fixed. */
@Composable
fun FreezeReportPrompt() {
    val context = LocalContext.current
    var report by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(Unit) {
        report = withContext(Dispatchers.IO) { runCatching { FreezeReporter.pendingReport(context) }.getOrNull() }
    }
    val file = report ?: return
    val dismiss = {
        FreezeReporter.clearReport()
        report = null
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.freeze_report_title)) },
        text = { Text(stringResource(R.string.freeze_report_body)) },
        confirmButton = {
            TextButton(onClick = {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val send =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, file.name)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                context.startActivity(Intent.createChooser(send, null))
                report = null
            }) { Text(stringResource(R.string.freeze_report_share)) }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.freeze_report_dismiss)) } },
    )
}
