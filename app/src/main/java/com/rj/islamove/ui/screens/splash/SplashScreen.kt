package com.rj.islamove.ui.screens.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rj.islamove.R

@Composable
fun SplashScreen(
    onNavigateToOnboarding: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToHome: (String) -> Unit,
    onNavigateToUserTypeSelection: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Handle navigation based on authentication state
    LaunchedEffect(uiState) {
        if (!uiState.isLoading) {
            when {
                uiState.needsOnboarding -> {
                    onNavigateToOnboarding()
                }
                !uiState.isUserLoggedIn -> {
                    onNavigateToLogin()
                }
                uiState.needsUserTypeSelection -> {
                    onNavigateToUserTypeSelection()
                }
                uiState.userType != null -> {
                    onNavigateToHome(uiState.userType!!.name)
                }
            }
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header white space
        Spacer(modifier = Modifier.height(32.dp))

        Image(
            painter = painterResource(id = R.drawable.splash),
            contentDescription = "Splash Screen",
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentScale = ContentScale.Crop
        )

        // Footer white space
        Spacer(modifier = Modifier.height(32.dp))
    }
}