package by.iposdev.visorlink.ui.screens.feed

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.FeedItem
import by.iposdev.visorlink.data.model.isExthruFamily
import by.iposdev.visorlink.ui.components.VlAmbientGlow
import by.iposdev.visorlink.ui.components.VlSurface
import by.iposdev.visorlink.ui.components.CachedImage
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.ui.theme.exthruRaisedShadow
import by.iposdev.visorlink.ui.theme.nmInsetShadow
import by.iposdev.visorlink.ui.theme.rememberExthruStyle
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeedScreen(
    onNavigateBack: () -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenComments: (String, String) -> Unit = { _, _ -> },
    viewModel: FeedViewModel = koinViewModel(),
    themeViewModel: ThemeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val appTheme by themeViewModel.appTheme.collectAsState()
    val isExthru = appTheme.isExthruFamily
    val style = rememberExthruStyle(appTheme)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.1f
    val haptic = rememberHaptic()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = stringResource(R.string.feed_title)
                    Text(
                        if (appTheme.name == "FORGE") "> ${title.uppercase()}_" else title,
                        fontWeight = FontWeight.Bold,
                        fontFamily = if (appTheme.name == "FORGE") FontFamily.Monospace else null
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isExthru) Color.Transparent else MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = if (isExthru) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isExthru) VlAmbientGlow(appTheme = appTheme)

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.onRefresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                if (uiState.isLoading && uiState.items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                                appTheme = appTheme,
                                isDark = isDark,
                                hapticEnabled = hapticEnabled,
                                onLike = { viewModel.toggleLike(item) },
                                onComments = {
                                    item.displayChatId?.let { cid -> onOpenComments(cid, item.id) }
                                },
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
                    .padding(bottom = if (appTheme.name == "FORGE") 80.dp else 96.dp, end = 16.dp)
            ) {
                VlSurface(
                    appTheme = appTheme,
                    isButton = true,
                    customRadius = if (appTheme.name == "FORGE") 0.dp else 22.dp,
                    onClick = {
                        scope.launch {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            listState.animateScrollToItem(0)
                        }
                    },
                    modifier = Modifier.size(50.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.feed_scroll_to_top),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FeedCard(
    item: FeedItem,
    currentUid: String,
    appTheme: AppTheme,
    isDark: Boolean,
    hapticEnabled: Boolean,
    onLike: () -> Unit,
    onComments: () -> Unit,
    onClick: () -> Unit,
    onChannelClick: () -> Unit
) {
    val haptic = rememberHaptic()
    val isLiked = item.displayLikedUids.contains(currentUid)
    val isExthru = appTheme.isExthruFamily
    val isForge = appTheme.name == "FORGE"

    VlSurface(
        appTheme = appTheme,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        onClick = onClick
    ) {
        Column {
            // Header: Author
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .clickable { onChannelClick() },
                    contentAlignment = Alignment.Center
                ) {
                    if (item.displayAuthorAvatarUrl != null) {
                        CachedImage(
                            model = item.displayAuthorAvatarUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            (item.displayAuthorName.firstOrNull() ?: item.displayChatId?.firstOrNull() ?: "?").toString().uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).clickable { onChannelClick() }) {
                    Text(
                        item.displayAuthorName.ifEmpty { item.displayChatId ?: stringResource(R.string.feed_unknown_channel) },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        item.createdAt?.let { formatTime(it.toDate()) } ?: stringResource(R.string.feed_just_now),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { /* More actions */ }) {
                    Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Media (if any)
            val imageUrl = item.displayImageUrl
            if (imageUrl != null) {
                Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                    CachedImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(if (isForge) RectangleShape else RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Content
            Column(Modifier.padding(16.dp)) {
                if (!item.title.isNullOrEmpty()) {
                    Text(
                        item.title!!,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                val displayText = item.text?.takeIf { it.isNotBlank() } ?: item.caption
                if (!displayText.isNullOrEmpty()) {
                    Text(
                        displayText,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Tags
                if (item.tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item.tags.forEach { tag ->
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "#$tag",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // Actions (Like, Comment, etc)
                Row(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ActionButton(
                            icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            label = item.displayLikesCount.toString(),
                            active = isLiked,
                            activeColor = Color(0xFFE11D48),
                            onClick = {
                                haptic.perform(if (isLiked) HapticType.CLICK else HapticType.SUCCESS, hapticEnabled)
                                onLike()
                            }
                        )
                        Spacer(Modifier.width(16.dp))
                        ActionButton(
                            icon = Icons.Outlined.ChatBubbleOutline,
                            label = item.displayCommentsCount.toString(),
                            onClick = {
                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                onComments()
                            }
                        )
                        Spacer(Modifier.width(16.dp))
                        ActionButton(
                            icon = Icons.Default.RemoveRedEye,
                            label = item.displayViewsCount.toString(),
                            enabled = false,
                            onClick = {}
                        )
                    }
                    IconButton(onClick = { /* Share */ }) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val contentColor = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.85f else 1f, label = "btn_scale")

    Row(
        modifier = Modifier
            .scale(scale)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            icon, null,
            modifier = Modifier.size(22.dp),
            tint = contentColor
        )
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

private fun formatTime(date: Date): String {
    val now = Calendar.getInstance()
    val cal = Calendar.getInstance().apply { time = date }
    return when {
        now.get(Calendar.DATE) == cal.get(Calendar.DATE) ->
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(date)
    }
}
