package org.visorlink.app.ui.components.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.R
import org.visorlink.app.ui.components.VlAlertDialog
import org.visorlink.app.ui.components.VlDialogButton
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlInset

@Composable
fun MusicOnboardingDialog(
    onEnable: () -> Unit,
    onDisable: () -> Unit
) {
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme

    VlAlertDialog(
        onDismissRequest = onDisable,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val iconShape = tokens.shapes.indicator
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .then(
                            if (tokens.structure.enabled) Modifier.vlInset(tokens.structure, iconShape)
                            else Modifier
                        )
                        .clip(iconShape)
                        .background(cs.primary.copy(alpha = 0.12f), iconShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🎧",
                        fontSize = 32.sp
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.music_onboarding_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = cs.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Text(
                text = stringResource(R.string.music_onboarding_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VlDialogButton(
                    onClick = onDisable,
                    isPrimary = false
                ) {
                    Text(stringResource(R.string.music_onboarding_disable))
                }
                VlDialogButton(
                    onClick = onEnable,
                    isPrimary = true
                ) {
                    Text(stringResource(R.string.music_onboarding_enable))
                }
            }
        }
    )
}

