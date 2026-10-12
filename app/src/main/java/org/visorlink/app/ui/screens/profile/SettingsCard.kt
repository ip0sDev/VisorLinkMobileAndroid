package org.visorlink.app.ui.screens.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import org.visorlink.app.ui.components.ProfileAvatar
import org.visorlink.app.ui.theme.VisorLinkTheme

@Composable
fun SettingsCard(icon: ImageVector,title: String,subTitle:String) {
    Card(
        modifier = Modifier.height(height = 48.dp),
        shape = CircleShape
    )
    {
            Row(
                modifier = Modifier.padding(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Settings",
                    modifier = Modifier.clip(CircleShape).size(32.dp)
                )
                Spacer(
                    Modifier.padding(horizontal = 8.dp)
                )
                Column() {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = subTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsCardPreview() {
    VisorLinkTheme {
        SettingsCard(
            icon = Icons.Outlined.Settings,
            title = "Test Title",
            subTitle = "Test SubTitle"
        )
    }
}