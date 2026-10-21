package com.example.spotify_clone

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.spotify_clone.data.remote.FirebaseRepository
import com.example.spotify_clone.playback.PlayerConnection
import com.example.spotify_clone.ui.screens.*
import com.example.spotify_clone.ui.theme.SpotifyColors
import com.example.spotify_clone.ui.theme.SpotifyTheme
import kotlinx.coroutines.launch

enum class AppState { SPLASH, LOGIN, AUTH, GUEST_SETUP, MAIN }

class MainActivity : ComponentActivity() {
    private lateinit var repository: FirebaseRepository
    private lateinit var playerConnection: PlayerConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        repository = FirebaseRepository(this)
        playerConnection = PlayerConnection(this)

        val prefs = getSharedPreferences("spotify_prefs", Context.MODE_PRIVATE)

        setContent {
            SpotifyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SpotifyColors.black
                ) {
                    var appState by remember { mutableStateOf(AppState.SPLASH) }
                    val coroutineScope = rememberCoroutineScope()

                    AnimatedContent(
                        targetState = appState,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "app_navigation"
                    ) { state ->
                        when (state) {
                            AppState.SPLASH -> {
                                SplashScreen(
                                    onComplete = {
                                        val savedName = prefs.getString("user_name", "") ?: ""
                                        val firebaseName = repository.auth.currentUser?.displayName ?: ""
                                        val effectiveName = savedName.ifEmpty { firebaseName }
                                        if (effectiveName.isNotEmpty() && savedName.isEmpty()) {
                                            prefs.edit().putString("user_name", effectiveName).putBoolean("is_registered", true).apply()
                                        }

                                        val isRegistered = prefs.getBoolean("is_registered", false) || effectiveName.isNotEmpty()
                                        if (isRegistered && effectiveName.isNotEmpty()) {
                                            appState = AppState.MAIN
                                        } else if (isRegistered && effectiveName.isEmpty()) {
                                            appState = AppState.GUEST_SETUP
                                        } else {
                                            appState = AppState.LOGIN
                                        }
                                    }
                                )
                            }
                            AppState.LOGIN -> {
                                LoginScreen(
                                    onSignUp = {
                                        appState = AppState.AUTH
                                    },
                                    onGuestLogin = {
                                        coroutineScope.launch {
                                            repository.signInAnonymously()
                                            prefs.edit().putBoolean("is_registered", true).apply()
                                            appState = AppState.GUEST_SETUP
                                        }
                                    }
                                )
                            }
                            AppState.AUTH -> {
                                AuthFlowScreen(
                                    repository = repository,
                                    onBack = { appState = AppState.LOGIN },
                                    onAuthComplete = {
                                        prefs.edit().putBoolean("is_registered", true).apply()
                                        appState = AppState.GUEST_SETUP
                                    }
                                )
                            }
                            AppState.GUEST_SETUP -> {
                                GuestSetupScreen(
                                    onComplete = { name ->
                                        prefs.edit()
                                            .putBoolean("is_registered", true)
                                            .putString("user_name", name)
                                            .apply()
                                        coroutineScope.launch {
                                            repository.updateUserProfile(name)
                                        }
                                        appState = AppState.MAIN
                                    }
                                )
                            }
                            AppState.MAIN -> {
                                MainScreen(
                                    repository = repository,
                                    playerConnection = playerConnection
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
