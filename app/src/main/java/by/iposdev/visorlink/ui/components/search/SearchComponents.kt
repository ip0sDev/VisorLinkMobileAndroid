package by.iposdev.visorlink.ui.components.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.TagSearchResult
import by.iposdev.visorlink.data.model.UserProfile
import by.iposdev.visorlink.ui.components.AvatarWithPresence
import by.iposdev.visorlink.ui.components.VlCard
import by.iposdev.visorlink.ui.components.chatlist.GroupChannelAvatar

@Composable
fun UserResultCard(
    user: UserProfile,
    onChatClick: () -> Unit
) {
    VlCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AvatarWithPresence(avatarUrl = user.avatarUrl, displayName = user.displayName,
                isOnline = user.online, size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(user.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (user.bio.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(user.bio, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
            Spacer(Modifier.width(12.dp))

            Button(onClick = onChatClick, shape = MaterialTheme.shapes.medium) {
                Icon(Icons.Default.Chat, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.search_action_chat))
            }
        }
    }
}

@Composable
fun GroupResultCard(
    result: TagSearchResult,
    isJoining: Boolean,
    onJoinClick: () -> Unit
) {
    VlCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GroupChannelAvatar(
                    avatarUrl = result.avatarUrl,
                    name = result.name,
                    isChannel = result.type == "channel",
                    size = 48.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(result.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("@${result.tag}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (result.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(result.description, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text("${result.memberCount} members", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onJoinClick,
                enabled = result.joinByTag && !isJoining,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                if (isJoining) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(if (result.joinByTag) "Join" else "Join disabled")
            }
        }
    }
}

@Composable
fun InviteTokenCard(
    isJoining: Boolean,
    onJoinClick: () -> Unit
) {
    VlCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Link, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)

            Spacer(Modifier.height(12.dp))
            Text("Nothing found by tag.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Try to join using this as an invite token?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onJoinClick,
                enabled = !isJoining,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                if (isJoining) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Join via invite link")
            }
        }
    }
}
