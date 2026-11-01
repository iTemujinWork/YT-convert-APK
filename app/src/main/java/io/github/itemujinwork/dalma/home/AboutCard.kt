package io.github.itemujinwork.dalma.home

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.itemujinwork.dalma.BuildConfig
import io.github.itemujinwork.dalma.R
import io.github.itemujinwork.dalma.data.Diagnostics
import io.github.itemujinwork.dalma.engine.Engine
import io.github.itemujinwork.dalma.ui.DragHandle
import io.github.itemujinwork.dalma.ui.theme.DalmaColors
import io.github.itemujinwork.dalma.update.AppUpdateCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The hidden card behind a long press: version, engine, licences, and after a failure its log. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutCard(update: AppUpdateCheck.Available?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engineVersion by produceState<String?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { runCatching { Engine(context).version() }.getOrNull() }
    }
    val lastFailure by produceState<String?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { runCatching { Diagnostics(context).lastFailure() }.getOrNull() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DalmaColors.DeepGreen,
        contentColor = DalmaColors.Milk,
        dragHandle = { DragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 28.dp)
                .navigationBarsPadding(),
        ) {
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.titleMedium)
            Text(
                text = engineVersion?.let { stringResource(R.string.about_engine, it) } ?: stringResource(R.string.about_engine_unknown),
                style = MaterialTheme.typography.bodyMedium,
            )
            lastFailure?.let { failure ->
                // One tap shares the whole log: the way to ask for help without a computer.
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.about_last_failure, failure.take(160)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DalmaColors.Mint,
                    modifier = Modifier.clickable { scope.launch { shareLog(context) } },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.about_how), style = MaterialTheme.typography.bodyLarge)
            if (update != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.about_update),
                    style = MaterialTheme.typography.bodyLarge,
                    color = DalmaColors.Mint,
                    modifier = Modifier.clickable { openUrl(context, update.url) },
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.about_licenses), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            listOf(
                R.string.about_license_ytdlp,
                R.string.about_license_ytdl_android,
                R.string.about_license_ffmpeg,
                R.string.about_license_python,
                R.string.about_license_quickjs,
                R.string.about_license_fonts,
                R.string.about_license_compose,
            ).forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.about_source), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private suspend fun shareLog(context: Context) {
    val text = withContext(Dispatchers.IO) { runCatching { Diagnostics(context).read() }.getOrNull() } ?: return
    val title = context.getString(R.string.about_log_title)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, title)
        .putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
