package by.iposdev.visorlink.ui.screens.feed

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.ui.components.VlAmbientGlow
import by.iposdev.visorlink.ui.components.VlFab
import by.iposdev.visorlink.ui.components.VlTopAppBar
import by.iposdev.visorlink.ui.components.feed.*
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onNavigateBack: () -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenComments: (String, String) -> Unit = { _, _ -> },
    onOpenImageViewer: (String, String) -> Unit = { _, _ -> },
    viewModel: FeedViewModel = koinViewModel(),
    themeViewModel: ThemeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = rememberHaptic()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            VlTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.feed_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            VlAmbientGlow()

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.onRefresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                if (uiState.isLoading && uiState.items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.verify_email_checking), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else if (uiState.items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📰", fontSize = 48.sp)
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.feed_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(uiState.items, key = { it.id }) { item ->
                            FeedCard(
                                item = item,
                                currentUid = viewModel.currentUid,
                                hapticEnabled = hapticEnabled,
                                onLike = { viewModel.toggleLike(item) },
                                onComments = {
                                    item.displayChatId?.let { cid -> onOpenComments(cid, item.id) }
                                },
                                onOpenImageViewer = onOpenImageViewer,
                                onClick = { viewModel.onItemClick(item) },
                                onChannelClick = { item.displayChatId?.let { onOpenChannel(it) } }
                            )
                        }
                    }
                }
            }

            // Scroll to Top Button
            AnimatedVisibility(
                visible = showScrollToTop,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 80.dp, end = 16.dp)
            ) {
                VlFab(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    icon = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.feed_scroll_to_top),
                    size = 50.dp,
                    hapticEnabled = hapticEnabled
                )
            }
        }
    }
}
