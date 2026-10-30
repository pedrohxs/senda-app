package com.senda.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.senda.app.R
import com.senda.app.data.repository.AppDocumentsRepository
import com.senda.app.domain.about.AppDocumentSection
import com.senda.app.domain.about.ChangelogEntry
import com.senda.app.ui.components.SendaGroupedCard
import com.senda.app.ui.components.SendaMark
import com.senda.app.ui.components.SendaRow
import com.senda.app.ui.components.SendaScreenState
import com.senda.app.ui.components.SendaSectionLabel
import com.senda.app.ui.components.SendaStateHost
import com.senda.app.ui.theme.AccentSage
import com.senda.app.ui.theme.BorderMuted
import com.senda.app.ui.theme.CardSurface
import com.senda.app.ui.theme.TextMutedGrey
import com.senda.app.ui.theme.TextWarmWhite
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class AboutPage { MENU, CHANGELOG, PRIVACY, LICENSES }

private val CHANGELOG_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * Sobre o Senda: versão instalada, novidades, licenças, privacidade e os dois
 * atalhos de saúde do app (diagnóstico e teste de proteção).
 *
 * Os textos vêm de arquivos dentro do APK, então esta tela funciona no modo
 * avião como qualquer outra.
 */
@Composable
fun AboutRoute(
    versionName: String,
    versionCode: Int,
    documents: AppDocumentsRepository,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenProtectionTest: () -> Unit
) {
    var pageName by rememberSaveable { mutableStateOf(AboutPage.MENU.name) }
    val page = runCatching { AboutPage.valueOf(pageName) }.getOrDefault(AboutPage.MENU)

    BackHandler(enabled = page != AboutPage.MENU) { pageName = AboutPage.MENU.name }

    when (page) {
        AboutPage.MENU -> AboutMenu(
            versionName = versionName,
            versionCode = versionCode,
            onBack = onBack,
            onOpenChangelog = { pageName = AboutPage.CHANGELOG.name },
            onOpenPrivacy = { pageName = AboutPage.PRIVACY.name },
            onOpenLicenses = { pageName = AboutPage.LICENSES.name },
            onOpenDiagnostics = onOpenDiagnostics,
            onOpenProtectionTest = onOpenProtectionTest
        )

        AboutPage.CHANGELOG -> ChangelogPage(
            documents = documents,
            onBack = { pageName = AboutPage.MENU.name }
        )

        AboutPage.PRIVACY -> DocumentPage(
            title = stringResource(R.string.about_privacy_title),
            load = documents::privacy,
            onBack = { pageName = AboutPage.MENU.name }
        )

        AboutPage.LICENSES -> DocumentPage(
            title = stringResource(R.string.about_licenses_title),
            load = documents::licenses,
            onBack = { pageName = AboutPage.MENU.name }
        )
    }
}

@Composable
private fun AboutMenu(
    versionName: String,
    versionCode: Int,
    onBack: () -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenProtectionTest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp)
    ) {
        SettingsPageHeader(title = stringResource(R.string.about_title), onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, BorderMuted)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(62.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            SendaMark(size = 54.dp)
                        }
                        Text(
                            text = "Senda",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextWarmWhite
                        )
                        Text(
                            text = stringResource(
                                R.string.about_version,
                                versionName.ifBlank { "?" },
                                versionCode
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMutedGrey
                        )
                        Text(
                            text = stringResource(R.string.about_tagline),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMutedGrey,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = AccentSage.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, AccentSage.copy(alpha = 0.30f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WifiOff,
                            contentDescription = null,
                            tint = AccentSage,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.about_offline_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = TextWarmWhite
                            )
                            Text(
                                text = stringResource(R.string.about_offline_message),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMutedGrey
                            )
                        }
                    }
                }
            }

            item {
                SendaGroupedCard {
                    SendaRow(
                        icon = Icons.Outlined.NewReleases,
                        title = stringResource(R.string.about_changelog_title),
                        subtitle = stringResource(R.string.about_changelog_description),
                        onClick = onOpenChangelog
                    )
                    SendaRow(
                        icon = Icons.Outlined.VerifiedUser,
                        title = stringResource(R.string.about_test_title),
                        subtitle = stringResource(R.string.about_test_description),
                        showDivider = true,
                        onClick = onOpenProtectionTest
                    )
                    SendaRow(
                        icon = Icons.Outlined.Gavel,
                        title = stringResource(R.string.about_licenses_title),
                        subtitle = stringResource(R.string.about_licenses_description),
                        showDivider = true,
                        onClick = onOpenLicenses
                    )
                    SendaRow(
                        icon = Icons.Outlined.PrivacyTip,
                        title = stringResource(R.string.about_privacy_title),
                        subtitle = stringResource(R.string.about_privacy_description),
                        showDivider = true,
                        onClick = onOpenPrivacy
                    )
                    SendaRow(
                        icon = Icons.Outlined.BugReport,
                        title = stringResource(R.string.settings_diagnostics_title),
                        subtitle = stringResource(R.string.settings_diagnostics_description),
                        showDivider = true,
                        onClick = onOpenDiagnostics
                    )
                }
            }
        }
    }
}

@Composable
private fun ChangelogPage(
    documents: AppDocumentsRepository,
    onBack: () -> Unit
) {
    val loaded = produceState<Result<List<ChangelogEntry>>?>(initialValue = null, documents) {
        value = withContext(Dispatchers.IO) { documents.changelog() }
    }
    val document = loaded.value
    val state = when {
        document == null -> SendaScreenState.loading()
        document.isFailure -> SendaScreenState.error(messageRes = R.string.about_document_error)
        else -> SendaScreenState.content()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp)
    ) {
        SettingsPageHeader(title = stringResource(R.string.about_changelog_title), onBack = onBack)
        Spacer(modifier = Modifier.height(8.dp))
        SendaStateHost(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val entries = document?.getOrNull().orEmpty()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(entries, key = { it.version }) { entry ->
                    ChangelogCard(entry = entry, isCurrent = entry == entries.firstOrNull())
                }
            }
        }
    }
}

@Composable
private fun ChangelogCard(entry: ChangelogEntry, isCurrent: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, BorderMuted)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.version,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWarmWhite
                )
                if (isCurrent) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.about_current_version),
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentSage
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatChangelogDate(entry.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMutedGrey
                )
            }
            entry.bullets.forEach { bullet ->
                BulletLine(text = bullet)
            }
        }
    }
}

@Composable
private fun DocumentPage(
    title: String,
    load: () -> Result<List<AppDocumentSection>>,
    onBack: () -> Unit
) {
    val loaded = produceState<Result<List<AppDocumentSection>>?>(initialValue = null, title) {
        value = withContext(Dispatchers.IO) { load() }
    }
    val document = loaded.value
    val state = when {
        document == null -> SendaScreenState.loading()
        document.isFailure -> SendaScreenState.error(messageRes = R.string.about_document_error)
        else -> SendaScreenState.content()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp)
    ) {
        SettingsPageHeader(title = title, onBack = onBack)
        Spacer(modifier = Modifier.height(8.dp))
        SendaStateHost(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val sections = document?.getOrNull().orEmpty()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(sections, key = { section -> section.title ?: section.hashCode().toString() }) { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        section.title?.let { sectionTitle ->
                            Text(
                                text = sectionTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextWarmWhite
                            )
                        }
                        section.paragraphs.forEach { paragraph ->
                            Text(
                                text = paragraph,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMutedGrey
                            )
                        }
                        section.bullets.forEach { bullet ->
                            BulletLine(text = bullet)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BulletLine(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(AccentSage)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = TextMutedGrey
        )
    }
}

private fun formatChangelogDate(raw: String): String = runCatching {
    LocalDate.parse(raw).format(CHANGELOG_DATE_FORMATTER)
}.getOrDefault(raw)
