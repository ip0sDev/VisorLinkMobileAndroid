package org.visorlink.app.ui.components

import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import org.visorlink.app.ui.theme.BiolumeBrandStyle
import org.visorlink.app.ui.theme.VlTheme

/**
 * Название приложения.
 *
 * В Biolume рендерится Space Grotesk — единственное место, где эта гарнитура
 * применяется: в ней нет кириллицы, а `app_name` («VisorLink») латинский в обеих
 * локалях и не переводится. Остальной интерфейс идёт на Inter, см. `Type.kt`.
 *
 * В MATERIAL3 — обычный `headlineLarge`, чтобы «чистая» тема осталась чистой.
 */
@Composable
fun VlBrandText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    fontSize: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
) {
    val tokens = VlTheme.tokens
    val style = if (tokens.isBiolume) BiolumeBrandStyle else MaterialTheme.typography.headlineLarge

    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = if (fontSize != TextUnit.Unspecified) style.copy(fontSize = fontSize) else style,
        textAlign = textAlign,
    )
}


/**
 * Название приложения в шапке экрана. В Biolume — фирменный вордмарк, в
 * остальных темах — обычный заголовок панели. Раньше эту развилку делал сам
 * ChatListScreen, а экраны о теме знать не должны.
 */
@Composable
fun VlBarBrandText(
    text: String,
    modifier: Modifier = Modifier,
) {
    if (VlTheme.tokens.isBiolume) {
        VlBrandText(
            text = text,
            modifier = modifier,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
        )
    } else {
        Text(
            text,
            modifier = modifier,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}
