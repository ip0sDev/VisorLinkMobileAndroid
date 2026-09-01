package by.iposdev.visorlink.ui.screens.group

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.repository.ChatRepository
import by.iposdev.visorlink.ui.components.VlButton
import by.iposdev.visorlink.ui.components.VlSegmentedControl
import by.iposdev.visorlink.ui.components.VlTextField
import by.iposdev.visorlink.ui.theme.*
import by.iposdev.visorlink.ui.theme.VlTheme
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateChatScreen(
    onNavigateBack: () -> Unit,
    onCreated: (chatId: String) -> Unit,
    chatRepository: ChatRepository = koinInject(),
    themeViewModel: ThemeViewModel = koinViewModel()
) {
    val scope = rememberCoroutineScope()
    var typeIndex by remember { mutableIntStateOf(0) }
    val type = if (typeIndex == 0) "group" else "channel"
    
    var name by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val haptic = rememberHaptic()

    val titleRes = if (type == "group") R.string.create_title_group else R.string.create_title_channel

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.perform(HapticType.CLICK, hapticEnabled)
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            VlSegmentedControl(
                labels = listOf(stringResource(R.string.create_tab_group), stringResource(R.string.create_tab_channel)),
                selectedIndex = typeIndex,
                onSelected = { typeIndex = it },
                hapticEnabled = hapticEnabled
            )

            Spacer(Modifier.height(8.dp))

            VlTextField(
                value = name, onValueChange = { name = it },
                label = stringResource(R.string.create_field_name),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            VlTextField(
                value = tag,
                onValueChange = {
                    tag = it.filter { c -> c.isLetterOrDigit() || c == '_' }.lowercase()
                },
                label = stringResource(R.string.create_field_tag),
                prefix = "@",
                supportingText = stringResource(R.string.create_tag_hint),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            VlTextField(
                value = description, onValueChange = { description = it.take(160) },
                label = stringResource(R.string.create_field_description),
                supportingText = "${description.length}/160",
                maxLines = 3,
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )

            if (type == "channel") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = VlTheme.tokens.shapes.chip
                ) {
                    Text(
                        stringResource(R.string.create_channel_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp))
            }

            val createLabel = stringResource(
                if (type == "group") R.string.create_button_group else R.string.create_button_channel
            )
            val errorDefault = stringResource(R.string.create_error_default)

            VlButton(
                onClick = {
                    scope.launch {
                        haptic.perform(HapticType.CLICK, hapticEnabled)
                        isLoading = true; error = null
                        try {
                            val (chatId, _) = chatRepository.createChat(type, name, tag, description)
                            onCreated(chatId)
                        } catch (e: Exception) {
                            error = e.message ?: errorDefault
                        } finally { isLoading = false }
                    }
                },
                enabled = name.isNotBlank() && tag.length >= 3 && !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading)
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                else Text(createLabel, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
