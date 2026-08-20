package by.iposdev.visorlink.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.ui.theme.VisorLinkTheme

@Composable
fun ThemePreviewGrid(
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    modifier: Modifier = Modifier
) {
    val themes = listOf(
        AppTheme.MATERIAL3_EXPRESSIVE
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        items(themes) { theme ->
            ThemePreviewItem(
                theme = theme,
                isSelected = selectedTheme == theme,
                onClick = { onThemeSelected(theme) }
            )
        }
    }
}

@Suppress("DEPRECATION")
@Composable
fun ThemePreviewItem(
    theme: AppTheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val themeName = when (theme) {
        AppTheme.MATERIAL3_EXPRESSIVE -> "Material 3"
        else -> theme.name
    }

    VisorLinkTheme(appTheme = theme, setStatusBarColor = false) {
        val cs = MaterialTheme.colorScheme
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clickable(onClick = onClick),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = cs.surface),
            border = if (isSelected) BorderStroke(3.dp, cs.primary) else BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f))
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = themeName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface
                    )
                    
                    // Mini Preview UI
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        VlSurface(
                            modifier = Modifier.size(width = 80.dp, height = 16.dp),
                            isButton = false
                        ) {}
                        
                        VlSurface(
                            modifier = Modifier.size(width = 60.dp, height = 16.dp).align(Alignment.End),
                            isButton = false,
                            overrideColor = cs.primary.copy(alpha = 0.8f)
                        ) {}
                        
                        VlSurface(
                            modifier = Modifier.fillMaxWidth().height(26.dp),
                            isButton = true
                        ) {
                             Box(Modifier.size(30.dp, 4.dp).background(cs.onPrimary, CircleShape).align(Alignment.Center))
                        }
                    }
                }
                
                if (isSelected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(24.dp)
                    )
                }
            }
        }
    }
}
