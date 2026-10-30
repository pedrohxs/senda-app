package com.senda.app.ui.focus.editor

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.senda.app.R
import com.senda.app.data.repository.InstalledApp
import com.senda.app.ui.components.AppIcon
import com.senda.app.ui.theme.AccentSage
import com.senda.app.ui.theme.CardSurface
import com.senda.app.ui.theme.TextMutedGrey
import com.senda.app.ui.theme.TextWarmWhite

@Composable
fun AppPickerDialog(
    title: String,
    apps: List<InstalledApp>,
    selectedPackages: Set<String>,
    isLoading: Boolean,
    singleSelect: Boolean = false,
    onToggle: (InstalledApp) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filteredApps = remember(apps, query) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            apps
        } else {
            apps.filter { app ->
                app.label.contains(normalizedQuery, ignoreCase = true) ||
                    app.packageName.contains(normalizedQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        titleContentColor = TextWarmWhite,
        textContentColor = TextMutedGrey,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.app_picker_search)) },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AccentSage)
                        }
                    }

                    filteredApps.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.app_picker_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMutedGrey
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                        ) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onToggle(app)
                                            if (singleSelect) onDismiss()
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppIcon(
                                        packageName = app.packageName,
                                        size = 32.dp
                                    )
                                    Spacer(modifier = Modifier.size(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.label,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextWarmWhite
                                        )
                                        Text(
                                            text = app.packageName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextMutedGrey
                                        )
                                    }

                                    if (singleSelect) {
                                        RadioButton(
                                            selected = app.packageName in selectedPackages,
                                            onClick = {
                                                onToggle(app)
                                                onDismiss()
                                            }
                                        )
                                    } else {
                                        Checkbox(
                                            checked = app.packageName in selectedPackages,
                                            onCheckedChange = { onToggle(app) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(
                        if (singleSelect) {
                            R.string.app_picker_cancel
                        } else {
                            R.string.app_picker_done
                        }
                    ),
                    color = AccentSage
                )
            }
        },
        dismissButton = if (singleSelect) {
            null
        } else {
            {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.app_picker_cancel),
                        color = TextMutedGrey
                    )
                }
            }
        }
    )
}
