package org.visorlink.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun ProfileAvatar(avatarUrl: String) {
    AsyncImage(
        model = avatarUrl, // Сюда передаешь ту самую ссылку из Firebase
        contentDescription = "Аватар пользователя",

        // Модификаторы для красоты: делаем размер 100х100 и круглую форму
        modifier = Modifier
            .size(100.dp)
            .clip(CircleShape),

        // Если картинка не квадратная, она обрежется (заполнит круг)
        contentScale = ContentScale.Crop
    )
}