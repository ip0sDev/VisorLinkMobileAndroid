package org.visorlink.app.ui.screens.chatlist

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.visorlink.app.ui.components.ProfileAvatar
import org.visorlink.app.ui.theme.VisorLinkTheme

@Composable
fun ChatItem(avatarUrl: String,title: String) {
    Card(
        modifier = Modifier.height(height = 64.dp),
        shape = CircleShape
    )
     {
        Row(
            modifier = Modifier.padding(16.dp)
        ) {
            ProfileAvatar(avatarUrl, size = 32.dp)
            Spacer(
                Modifier.padding(horizontal = 16.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
@Preview(showBackground = true)
@Composable
private fun ChatItemPreview() {
    VisorLinkTheme {
        ChatItem(avatarUrl = "",title="Text")
    }
}