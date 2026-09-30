package org.visorlink.app.ui.screens.chatlist

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.visorlink.app.R
import org.visorlink.app.ui.components.ProfileAvatar
import org.visorlink.app.ui.screens.auth.AuthScreen
import org.visorlink.app.ui.screens.auth.AuthViewModel
import org.visorlink.app.ui.theme.VisorLinkTheme

@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel = viewModel()
){
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ){
        Text(
            text = "VisorLink",
            modifier = Modifier.align(Alignment.Start),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        val displayName = uiState.userName
        val uid = uiState.uid
        Text(
            text= displayName,
            modifier = Modifier.align(Alignment.Start),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text= uid,
            modifier = Modifier.align(Alignment.Start),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Button(
            onClick = {
                viewModel.getData(
                    ""
                )
            },
            enabled = true,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {}
        ProfileAvatar(avatarUrl = uiState.avatarUrl)
    }
}
@Preview(showBackground = true)
@Composable
private fun ChatListScreenPreview() {
    VisorLinkTheme {
        ChatListScreen()
    }
}