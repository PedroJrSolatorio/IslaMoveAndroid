package com.rj.islamove.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rj.islamove.data.models.UserType
import com.rj.islamove.ui.theme.IslamovePrimary
import com.rj.islamove.R

@Composable
fun UserTypeSelectionScreen(
    onUserTypeSelected: (String) -> Unit,
    viewModel: UserTypeSelectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedUserType by remember { mutableStateOf<String?>(null) }
    
    // Handle success navigation
    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            selectedUserType?.let { userType ->
                onUserTypeSelected(userType)
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, top = 80.dp, bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Header white space
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.select_user_type),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Choose how you want to use IslaMove",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Passenger option
        UserTypeCard(
            title = stringResource(R.string.passenger),
            description = "Book rides and travel with ease",
            icon = Icons.Default.Person,
            isSelected = selectedUserType == "PASSENGER",
            onClick = { selectedUserType = "PASSENGER" }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Driver option
        UserTypeCard(
            title = stringResource(R.string.driver),
            description = "Drive and earn money on your schedule",
            icon = Icons.Default.Person, // Using Person icon for now, can be changed later
            isSelected = selectedUserType == "DRIVER",
            onClick = { selectedUserType = "DRIVER" }
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Error message
        uiState.errorMessage?.let { errorMessage ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = errorMessage,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Continue button
        Button(
            onClick = {
                selectedUserType?.let { userType ->
                    val userTypeEnum = when (userType) {
                        "DRIVER" -> UserType.DRIVER
                        "PASSENGER" -> UserType.PASSENGER
                        else -> UserType.PASSENGER
                    }
                    viewModel.selectUserType(userTypeEnum)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedUserType != null && !uiState.isLoading
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Saving...")
            } else {
                Text(
                    text = "Continue",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        // Footer white space
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserTypeCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) IslamovePrimary.copy(alpha = 0.1f) 
                           else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) 
                    androidx.compose.foundation.BorderStroke(2.dp, IslamovePrimary) 
                 else null,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(48.dp),
                tint = if (isSelected) IslamovePrimary 
                      else MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.width(20.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) IslamovePrimary 
                           else MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isSelected) {
                RadioButton(
                    selected = true,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = IslamovePrimary
                    )
                )
            }
        }
    }
}