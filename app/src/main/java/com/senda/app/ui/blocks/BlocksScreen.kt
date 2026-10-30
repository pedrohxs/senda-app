package com.senda.app.ui.blocks

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.senda.app.R
import com.senda.app.core.UrlMatcher
import com.senda.app.data.local.entity.GroupItemEntity
import com.senda.app.data.local.entity.TargetType
import com.senda.app.ui.components.SendaDashedButton
import com.senda.app.ui.components.SendaIconBadge
import com.senda.app.ui.components.SendaScreenState
import com.senda.app.ui.components.SendaSectionLabel
import com.senda.app.ui.components.SendaStateHost
import com.senda.app.ui.theme.AccentSage
import com.senda.app.ui.theme.BorderMuted
import com.senda.app.ui.theme.CardSurface
import com.senda.app.ui.theme.IconContainer
import com.senda.app.ui.theme.TextMutedGrey
import com.senda.app.ui.theme.TextWarmWhite

@Composable
fun BlocksScreen(
    state: BlocksUiState,
    onSelectGroup: (String) -> Unit,
    onSelectType: (TargetType) -> Unit,
    onAddWebsite: (String) -> Unit,
    onAddKeyword: (String) -> Unit,
    onDeleteItem: (GroupItemEntity) -> Unit,
    onRetry: () -> Unit = {}
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 28.dp)
    ) {
        Text(
            text = stringResource(R.string.blocks_title),
            style = MaterialTheme.typography.headlineLarge,
            color = TextWarmWhite
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.blocks_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMutedGrey
        )

        Spacer(modifier = Modifier.height(22.dp))

        Spacer(modifier = Modifier.height(24.dp))

        SendaSectionLabel(text = stringResource(R.string.blocks_group_label))
        Spacer(modifier = Modifier.height(10.dp))

        SendaStateHost(
            state = when {
                state.isLoading -> SendaScreenState.loading()
                state.loadFailed -> SendaScreenState.error(
                    messageRes = R.string.blocks_error,
                    onRetry = onRetry
                )
                state.groups.isEmpty() -> SendaScreenState.empty(
                    titleRes = R.string.blocks_no_groups_title,
                    messageRes = R.string.blocks_no_groups_description
                )
                else -> SendaScreenState.content()
            },
            modifier = Modifier.fillMaxWidth(),
            minHeight = 140.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.groups.forEach { group ->
                        SelectPill(
                            text = group.name,
                            selected = state.selectedGroupId == group.id,
                            onClick = { onSelectGroup(group.id) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectPill(
                        text = stringResource(R.string.blocks_type_websites),
                        selected = state.selectedType == TargetType.WEBSITE,
                        onClick = { onSelectType(TargetType.WEBSITE) },
                        modifier = Modifier.weight(1f)
                    )
                    SelectPill(
                        text = stringResource(R.string.blocks_type_keywords),
                        selected = state.selectedType == TargetType.KEYWORD,
                        onClick = { onSelectType(TargetType.KEYWORD) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                SendaDashedButton(
                    text = stringResource(
                        if (state.selectedType == TargetType.WEBSITE) {
                            R.string.blocks_add_website
                        } else {
                            R.string.blocks_add_keyword
                        }
                    ),
                    onClick = {
                        showAddDialog = true
                        input = ""
                        dialogError = null
                    },
                    enabled = state.selectedGroupId != null
                )

                if (state.errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                SendaSectionLabel(
                    text = if (state.selectedType == TargetType.WEBSITE) {
                        stringResource(R.string.blocks_type_websites)
                    } else {
                        stringResource(R.string.blocks_type_keywords)
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (state.items.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, BorderMuted)
                    ) {
                        Text(
                            text = stringResource(
                                if (state.selectedType == TargetType.WEBSITE) {
                                    R.string.blocks_empty_websites
                                } else {
                                    R.string.blocks_empty_keywords
                                }
                            ),
                            modifier = Modifier.padding(18.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMutedGrey
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        shape = RoundedCornerShape(20.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, BorderMuted)
                    ) {
                        Column {
                            state.items.forEachIndexed { index, item ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 68.dp),
                                        color = BorderMuted
                                    )
                                }
                                BlockItemRow(
                                    item = item,
                                    onDelete = { onDeleteItem(item) }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(104.dp))
    }

    if (showAddDialog) {
        val isWebsite = state.selectedType == TargetType.WEBSITE
        val invalidDomain = stringResource(R.string.blocks_dialog_invalid_domain)
        val invalidKeyword = stringResource(R.string.blocks_dialog_invalid_keyword)

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = CardSurface,
            titleContentColor = TextWarmWhite,
            textContentColor = TextMutedGrey,
            title = {
                Text(
                    text = stringResource(
                        if (isWebsite) {
                            R.string.blocks_dialog_website_title
                        } else {
                            R.string.blocks_dialog_keyword_title
                        }
                    )
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            dialogError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                stringResource(
                                    if (isWebsite) {
                                        R.string.blocks_dialog_website_label
                                    } else {
                                        R.string.blocks_dialog_keyword_label
                                    }
                                )
                            )
                        },
                        placeholder = {
                            Text(
                                stringResource(
                                    if (isWebsite) {
                                        R.string.blocks_dialog_website_placeholder
                                    } else {
                                        R.string.blocks_dialog_keyword_placeholder
                                    }
                                )
                            )
                        },
                        singleLine = true
                    )

                    if (dialogError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = dialogError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isWebsite) {
                            val host = UrlMatcher.extractHost(input)
                            if (host == null) {
                                dialogError = invalidDomain
                            } else {
                                onAddWebsite(host)
                                showAddDialog = false
                            }
                        } else {
                            val value = input.trim()
                            if (value.isEmpty()) {
                                dialogError = invalidKeyword
                            } else {
                                onAddKeyword(value)
                                showAddDialog = false
                            }
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.blocks_dialog_add),
                        color = AccentSage
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(
                        text = stringResource(R.string.blocks_dialog_cancel),
                        color = TextMutedGrey
                    )
                }
            }
        )
    }
}

@Composable
private fun SelectPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = if (selected) IconContainer else CardSurface,
        border = BorderStroke(
            1.dp,
            if (selected) AccentSage.copy(alpha = 0.45f) else BorderMuted
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) TextWarmWhite else TextMutedGrey,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BlockItemRow(
    item: GroupItemEntity,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SendaIconBadge(
            icon = if (item.targetType == TargetType.WEBSITE) {
                Icons.Outlined.Language
            } else {
                Icons.Outlined.Tag
            },
            tint = AccentSage,
            size = 40.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = TextWarmWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.targetIdentifier != item.displayName) {
                Text(
                    text = item.targetIdentifier,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Surface(
            onClick = onDelete,
            shape = CircleShape,
            color = CardSurface
        ) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = stringResource(R.string.blocks_remove),
                tint = TextMutedGrey,
                modifier = Modifier
                    .padding(8.dp)
                    .size(18.dp)
            )
        }
    }
}