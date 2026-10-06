package org.visorlink.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import org.visorlink.app.R
import org.visorlink.app.data.idcard.IdCardPosition
import org.visorlink.app.data.idcard.IdCardRules
import org.visorlink.app.data.idcard.IdMode
import org.visorlink.app.data.repository.IdCardState
import org.visorlink.app.ui.components.VlDialogButton
import org.visorlink.app.ui.components.VlSegmentedControl
import org.visorlink.app.ui.components.VlSettingsSection
import org.visorlink.app.ui.components.VlTopAppBar
import org.visorlink.app.ui.components.idcard.VlIdCard
import org.visorlink.app.ui.idcard.IdSkinInventory
import org.visorlink.app.ui.idcard.MaskSection
import org.visorlink.app.ui.idcard.ModeOption
import org.visorlink.app.ui.idcard.QuietLink
import org.visorlink.app.ui.idcard.SpeciesField
import org.visorlink.app.ui.idcard.formatWait
import org.visorlink.app.ui.idcard.rememberCountdown
import org.visorlink.app.ui.idcard.skinErrorText
import org.visorlink.app.ui.idcard.idCardAccent
import org.visorlink.app.ui.idcard.idCardEmojis
import org.visorlink.app.ui.idcard.idCardPerson
import kotlin.math.max

/**
 * Настройки ID-карты (спека §6). Особые режимы спрятаны за кнопкой и раскрыты сразу, только
 * если уже выбраны — их ищут целенаправленно. Под режимом — инвентарь скинов: «Заменить документ»
 * сервер больше не выполняет, его заменила прокрутка.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdCardSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: IdCardSettingsViewModel = koinViewModel(),
) {
    val profile by viewModel.profile.collectAsState()
    val cardState by viewModel.card.collectAsState()
    val ui by viewModel.ui.collectAsState()
    val cs = MaterialTheme.colorScheme

    Scaffold(
        containerColor = cs.background,
        topBar = {
            VlTopAppBar(
                title = { Text(stringResource(R.string.idcard_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            )
        },
    ) { padding ->
        val card = (cardState as? IdCardState.Ready)?.card
        val me = profile
        if (cardState !is IdCardState.Ready || me == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (cardState is IdCardState.Ready) Text(stringResource(R.string.idcard_tab_no_card)) else CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (card == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.idcard_tab_no_card), color = cs.onSurfaceVariant)
            }
            return@Scaffold
        }

        var showSpecial by rememberSaveable { mutableStateOf(false) }
        // null — показываем сохранённый вид
        var speciesDraft by rememberSaveable(card.species, card.mode) { mutableStateOf<String?>(null) }
        val special = card.mode.isSpecial
        val speciesValue = speciesDraft ?: card.species.orEmpty()
        val speciesDirty = special && speciesValue.trim() != card.species.orEmpty()
        val modeLeft = rememberCountdown(max(IdCardRules.modeAvailableAt(card), ui.serverWaitUntil))

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                VlIdCard(card, me.idCardPerson(), width = 320.dp, accent = me.idCardAccent(), emojis = me.idCardEmojis())
            }

            VlSettingsSection(title = stringResource(R.string.idcard_tab_mode)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val modes = if (showSpecial || special) IdMode.entries else listOf(IdMode.STANDARD)
                    modes.forEach { m ->
                        ModeOption(
                            mode = m,
                            desc = stringResource(
                                when (m) {
                                    IdMode.STANDARD -> R.string.idcard_tab_desc_standard
                                    IdMode.PROTOGEN -> R.string.idcard_tab_desc_protogen
                                    IdMode.BEAST -> R.string.idcard_tab_desc_beast
                                },
                            ),
                            selected = card.mode == m,
                            enabled = !ui.busy && (card.mode == m || modeLeft == 0L),
                            onClick = { if (card.mode != m) viewModel.setMode(m, card.mode, speciesValue) { speciesDraft = null } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (!showSpecial && !special) {
                        QuietLink(stringResource(R.string.idcard_tab_show_special)) { showSpecial = true }
                    }
                    if (modeLeft > 0) {
                        Text(stringResource(R.string.idcard_tab_mode_wait, formatWait(modeLeft)), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    if (special) {
                        SpeciesField(
                            mode = card.mode,
                            value = speciesValue,
                            onChange = { speciesDraft = it.take(IdCardRules.SPECIES_MAX) },
                            modifier = Modifier.fillMaxWidth(),
                            trailing = {
                                VlDialogButton(
                                    onClick = { viewModel.saveSpecies(card.mode, speciesValue) { speciesDraft = null } },
                                    enabled = !ui.busy && speciesDirty && modeLeft == 0L,
                                    isPrimary = true,
                                ) { Text(stringResource(R.string.idcard_tab_save)) }
                            },
                        )
                    }
                }
            }

            VlSettingsSection(title = stringResource(R.string.idskin_title)) {
                IdSkinInventory(card = card, profile = me)
            }

            if (special || org.visorlink.app.ui.idcard.LocalIdModeState.current.mask.active) {
                VlSettingsSection(title = stringResource(R.string.idcard_mask_title)) {
                    MaskSection(Modifier.padding(vertical = 12.dp))
                }
            }

            // Место карты в профиле: не PRO, применяется у всех
            VlSettingsSection(title = stringResource(R.string.idcard_position_title)) {
                val positions = IdCardPosition.entries
                VlSegmentedControl(
                    labels = positions.map {
                        stringResource(
                            when (it) {
                                IdCardPosition.TOP -> R.string.idcard_position_top
                                IdCardPosition.AFTER_HEADER -> R.string.idcard_position_after_header
                                IdCardPosition.BOTTOM -> R.string.idcard_position_bottom
                            },
                        )
                    },
                    selectedIndex = positions.indexOf(IdCardPosition.of(me.customization)),
                    onSelected = { viewModel.setPosition(positions[it]) },
                    modifier = Modifier.padding(16.dp),
                )
            }

            ui.error?.let {
                Text(
                    skinErrorText(it),
                    color = cs.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
    }
}
