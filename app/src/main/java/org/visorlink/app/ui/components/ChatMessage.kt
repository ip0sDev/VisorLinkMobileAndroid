package org.visorlink.app.ui.components

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.visorlink.app.ui.theme.VisorLinkTheme

@Composable
fun ChatMessage(text: String,chatId:String,isSupported: Boolean,type:String,fromMe:Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = if (fromMe) Alignment.CenterEnd else Alignment.CenterStart
    ){
    Card(
        shape = CircleShape,
        modifier = Modifier.padding(8.dp)
    )
    {
        Row(
            modifier = Modifier
                .padding(8.dp)
        ) {
            if (isSupported) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    text = "Сообщение типа $type не поддерживается в данной версии приложения",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
}
@Preview(showBackground = true)
@Composable
private fun ChatItemPreview() {
    VisorLinkTheme {
        ChatMessage(text = "Text",chatId="test", isSupported = true,type="text", fromMe = true)
    }
}