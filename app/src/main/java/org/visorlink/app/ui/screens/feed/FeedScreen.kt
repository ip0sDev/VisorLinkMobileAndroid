package org.visorlink.app.ui.screens.feed

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.visorlink.app.R
import org.visorlink.app.data.model.CuratedChannel
import org.visorlink.app.data.model.FeedMedia
import org.visorlink.app.data.model.FeedPost
import org.visorlink.app.data.model.ForwardableMessage
import org.visorlink.app.ui.components.LiquidPullRefreshLayout
import org.visorlink.app.ui.components.VlAmbientGlow
import org.visorlink.app.ui.components.VlFab
import org.visorlink.app.ui.components.VlSegmentedControl
import org.visorlink.app.ui.components.VlTopAppBar
import org.visorlink.app.ui.components.chat.ReportContentDialog
import org.visorlink.app.ui.components.feed.CuratedChannelCard
import org.visorlink.app.ui.components.feed.FeedPostAction
import org.visorlink.app.ui.components.feed.FeedPostCard
import org.visorlink.app.ui.screens.chat.ForwardPickerDialog
import org.visorlink.app.ui.screens.chatlist.ChatListViewModel
import org.visorlink.app.ui.theme.ThemeViewModel
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.utils.ImageCache

/** Пост засчитывается просмотренным, когда хотя бы половина его пробыла на экране столько. */
private const val VIEW_DWELL_MS = 1_000L

/**
 * Лента «Каналы» (вкладка Discover), как `FeedWindow.jsx` в вебе: «Подписки» — посты моих
 * каналов, «Рекомендуемые» — курируемый каталог с подпиской.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onNavigateBack: () -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenComments: (String, String) -> Unit = { _, _ -> },
    onOpenImageViewer: (String, String) -> Unit = { _, _ -> },
    viewModel: FeedViewModel = koinViewModel(),
    themeViewModel: ThemeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var forwarding by remember { mutableStateOf<FeedPost?>(null) }
    var reporting by remember { mutableStateOf<FeedPost?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val text = when (event) {
                FeedEvent.LikeFailed -> context.getString(R.string.feed_like_failed)
                is FeedEvent.JoinFailed -> event.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.feed_join_failed)
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    val postsListState = rememberLazyListState()
    val curatedListState = rememberLazyListState()
    val showScrollToTop by remember {
        derivedStateOf { uiState.tab == FeedTab.SUBSCRIPTIONS && postsListState.firstVisibleItemIndex > 2 }
    }

    TrackPostViews(postsListState, onSeen = viewModel::onPostsSeen)

    fun handle(post: FeedPost, action: FeedPostAction) {
        when (action) {
            FeedPostAction.OPEN_CHANNEL -> onOpenChannel(post.channel.id)
            FeedPostAction.FORWARD -> forwarding = post
            FeedPostAction.SHARE -> sharePost(context, post)
            FeedPostAction.COPY_TEXT -> post.body?.let { copyText(context, it) }
            FeedPostAction.SAVE_IMAGE -> (post.media as? FeedMedia.Images)?.let { images ->
                scope.launch {
                    val saved = images.urls.count { ImageCache.saveImageToGallery(context, it) }
                    Toast.makeText(
                        context,
                        context.getString(if (saved == images.urls.size) R.string.feed_saved else R.string.feed_save_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            FeedPostAction.REPORT -> reporting = post
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            VlTopAppBar(
                title = { Text(stringResource(R.string.feed_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            VlAmbientGlow()

            Column(Modifier.fillMaxSize()) {
                VlSegmentedControl(
                    labels = listOf(stringResource(R.string.feed_tab_subscriptions), stringResource(R.string.feed_tab_curated)),
                    selectedIndex = uiState.tab.ordinal,
                    onSelected = { viewModel.selectTab(FeedTab.entries[it]) },
                    hapticEnabled = hapticEnabled,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )

                LiquidPullRefreshLayout(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refresh,
                    hapticEnabled = hapticEnabled,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (uiState.tab) {
                        FeedTab.SUBSCRIPTIONS -> SubscriptionsList(
                            state = uiState,
                            listState = postsListState,
                            currentUid = viewModel.currentUid,
                            hapticEnabled = hapticEnabled,
                            onLike = viewModel::toggleLike,
                            onComments = { onOpenComments(it.channel.id, it.message.id) },
                            onOpenChannel = { onOpenChannel(it.channel.id) },
                            onOpenImage = { url -> onOpenImageViewer(url, "image") },
                            onAction = ::handle,
                            onRetry = viewModel::retry,
                            onBrowseCurated = { viewModel.selectTab(FeedTab.CURATED) },
                        )
                        FeedTab.CURATED -> CuratedList(
                            state = uiState,
                            listState = curatedListState,
                            onSubscribe = viewModel::join,
                            onOpen = { onOpenChannel(it.id) },
                            onRetry = viewModel::retry,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showScrollToTop,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 80.dp, end = 16.dp),
            ) {
                VlFab(
                    onClick = { scope.launch { postsListState.animateScrollToItem(0) } },
                    icon = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.feed_scroll_to_top),
                    size = 50.dp,
                    hapticEnabled = hapticEnabled,
                )
            }
        }
    }

    forwarding?.let { post ->
        val chatListViewModel: ChatListViewModel = koinViewModel()
        val chats by chatListViewModel.chats.collectAsState()
        ForwardPickerDialog(
            message = ForwardableMessage.fromMessage(post.message, post.channel.id, post.channel.name),
            chats = chats,
            currentUid = viewModel.currentUid,
            onDismiss = { forwarding = null },
            onForwarded = {
                forwarding = null
                Toast.makeText(context, context.getString(R.string.toast_message_forwarded), Toast.LENGTH_SHORT).show()
            },
        )
    }

    reporting?.let { post ->
        ReportContentDialog(
            targetType = "post",
            targetId = post.message.id,
            chatId = post.channel.id,
            onDismiss = { reporting = null },
            onReportSubmitted = {
                reporting = null
                Toast.makeText(context, context.getString(R.string.report_submitted_toast), Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun SubscriptionsList(
    state: FeedUiState,
    listState: LazyListState,
    currentUid: String,
    hapticEnabled: Boolean,
    onLike: (FeedPost) -> Unit,
    onComments: (FeedPost) -> Unit,
    onOpenChannel: (FeedPost) -> Unit,
    onOpenImage: (String) -> Unit,
    onAction: (FeedPost, FeedPostAction) -> Unit,
    onRetry: () -> Unit,
    onBrowseCurated: () -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            state.posts.isNotEmpty() -> items(state.posts, key = { it.key }) { post ->
                FeedPostCard(
                    post = post,
                    currentUid = currentUid,
                    hapticEnabled = hapticEnabled,
                    onLike = { onLike(post) },
                    onComments = { onComments(post) },
                    onOpenChannel = { onOpenChannel(post) },
                    onOpenImage = onOpenImage,
                    onAction = { onAction(post, it) },
                )
            }
            state.postsLoading -> item(key = "loading") { LoadingState() }
            state.postsError -> item(key = "error") {
                EmptyState(stringResource(R.string.feed_load_error), action = stringResource(R.string.feed_retry), onAction = onRetry)
            }
            else -> item(key = "empty") {
                EmptyState(
                    stringResource(if (state.subscribedIds.isEmpty()) R.string.feed_no_subscriptions else R.string.feed_empty),
                    action = stringResource(R.string.feed_browse_curated),
                    onAction = onBrowseCurated,
                )
            }
        }
    }
}

@Composable
private fun CuratedList(
    state: FeedUiState,
    listState: LazyListState,
    onSubscribe: (CuratedChannel) -> Unit,
    onOpen: (CuratedChannel) -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            state.curated.isNotEmpty() -> {
                item(key = "caption") {
                    Text(
                        stringResource(R.string.feed_curated_caption),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
                items(state.curated, key = { it.id }) { channel ->
                    CuratedChannelCard(
                        channel = channel,
                        subscribed = channel.id in state.subscribedIds,
                        joining = channel.id in state.joining,
                        onSubscribe = { onSubscribe(channel) },
                        onOpen = { onOpen(channel) },
                    )
                }
            }
            state.curatedLoading -> item(key = "loading") { LoadingState() }
            state.curatedError -> item(key = "error") {
                EmptyState(stringResource(R.string.feed_load_error), action = stringResource(R.string.feed_retry), onAction = onRetry)
            }
            else -> item(key = "empty") { EmptyState(stringResource(R.string.feed_curated_empty)) }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxWidth().padding(top = 96.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clip(VlTheme.tokens.shapes.pill)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * Просмотры: пост засчитывается, когда не меньше половины его (или половины экрана, если пост
 * выше экрана) провело на экране [VIEW_DWELL_MS] без смены набора видимых постов. При
 * прокрутке таймер перезапускается — пролистанное мимо не считается.
 */
@Composable
private fun TrackPostViews(listState: LazyListState, onSeen: (Set<String>) -> Unit) {
    val currentOnSeen by rememberUpdatedState(onSeen)
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val top = info.viewportStartOffset
            val bottom = info.viewportEndOffset
            val viewport = bottom - top
            info.visibleItemsInfo.mapNotNullTo(HashSet()) { item ->
                val key = item.key as? String ?: return@mapNotNullTo null
                val shown = minOf(item.offset + item.size, bottom) - maxOf(item.offset, top)
                key.takeIf { shown >= minOf(item.size, viewport) / 2 }
            }
        }
            .distinctUntilChanged()
            .collectLatest { keys ->
                if (keys.isEmpty()) return@collectLatest
                delay(VIEW_DWELL_MS)
                currentOnSeen(keys)
            }
    }
}

private fun sharePost(context: Context, post: FeedPost) {
    val signature = context.getString(R.string.feed_share_signature, post.channel.name) +
        post.channel.tag.takeIf { it.isNotBlank() }?.let { " (@${it.removePrefix("@")})" }.orEmpty()
    val text = listOfNotNull(post.body, signature).joinToString("\n\n")
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.feed_share_external)))
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("post", text))
    // С Android 13 система сама показывает, что скопировано
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, context.getString(R.string.feed_copied), Toast.LENGTH_SHORT).show()
    }
}
