package io.github.itemujinwork.dalma.home

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.itemujinwork.dalma.BuildConfig
import io.github.itemujinwork.dalma.R
import io.github.itemujinwork.dalma.engine.Engine
import io.github.itemujinwork.dalma.ui.DragHandle
import io.github.itemujinwork.dalma.ui.theme.DalmaColors
import io.github.itemujinwork.dalma.update.AppUpdateCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The hidden card behind a long press: version, engine and licences. Nothing to read or manage. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutCard(update: AppUpdateCheck.Available?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val engineVersion by produceState<String?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { runCatching { Engine(context).version() }.getOrNull() }
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
                R.string.about_license_mutagen,
                R.string.about_license_quickjs,
                R.string.about_license_fonts,
                R.string.about_license_compose,
            ).forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.about_source), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
