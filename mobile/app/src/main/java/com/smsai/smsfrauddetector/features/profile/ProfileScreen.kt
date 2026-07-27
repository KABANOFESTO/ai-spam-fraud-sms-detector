package com.smsai.smsfrauddetector.features.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.smsai.smsfrauddetector.core.common.ApiResult
import com.smsai.smsfrauddetector.core.common.SimpleViewModelFactory
import com.smsai.smsfrauddetector.core.designsystem.components.BannerTone
import com.smsai.smsfrauddetector.core.designsystem.components.ErrorStateCard
import com.smsai.smsfrauddetector.core.designsystem.components.FeedbackBanner
import com.smsai.smsfrauddetector.core.designsystem.components.PrimaryButton
import com.smsai.smsfrauddetector.core.designsystem.components.StatusBadge
import com.smsai.smsfrauddetector.core.designsystem.components.SurfaceCard
import com.smsai.smsfrauddetector.data.remote.dto.UserDto
import com.smsai.smsfrauddetector.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val loading: Boolean = true,
    val user: UserDto? = null,
    val error: String? = null,
    val status: String? = null,
)

class ProfileViewModel(private val repository: AppRepository) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            when (val result = repository.fetchCurrentUser()) {
                is ApiResult.Success -> _state.value = ProfileUiState(loading = false, user = result.data)
                is ApiResult.Error -> _state.value = ProfileUiState(loading = false, error = result.message)
                else -> Unit
            }
        }
    }

    fun save(
        username: String,
        firstName: String,
        lastName: String,
        currentPassword: String,
        newPassword: String,
        profilePictureUri: Uri?,
        removeProfilePicture: Boolean,
    ) {
        viewModelScope.launch {
            when (val result = repository.updateProfile(
                username = username.ifBlank { null },
                firstName = firstName.ifBlank { null },
                lastName = lastName.ifBlank { null },
                currentPassword = currentPassword.ifBlank { null },
                newPassword = newPassword.ifBlank { null },
                profilePictureUri = profilePictureUri,
                removeProfilePicture = removeProfilePicture,
            )) {
                is ApiResult.Success -> _state.value = _state.value.copy(user = result.data, status = "Profile updated successfully.")
                is ApiResult.Error -> _state.value = _state.value.copy(status = result.message)
                else -> Unit
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.logout()
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(repository: AppRepository, onLogout: () -> Unit) {
    val viewModel: ProfileViewModel = viewModel(
        factory = remember(repository) { SimpleViewModelFactory { ProfileViewModel(repository) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var currentPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var currentPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var newPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var selectedPhotoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var removePhotoRequested by rememberSaveable { mutableStateOf(false) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var bannerMessage by remember { mutableStateOf<String?>(null) }
    var bannerTone by remember { mutableStateOf(BannerTone.Info) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedPhotoUri = uri?.toString()
        removePhotoRequested = false
        editorOpen = true
    }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.user) {
        state.user?.let {
            username = it.username
            firstName = it.firstName.orEmpty()
            lastName = it.lastName.orEmpty()
        }
    }
    LaunchedEffect(state.status) {
        val status = state.status ?: return@LaunchedEffect
        bannerMessage = status
        bannerTone = if (status.contains("updated", ignoreCase = true)) BannerTone.Success else BannerTone.Error
        if (status.contains("updated", ignoreCase = true)) {
            selectedPhotoUri = null
            removePhotoRequested = false
            editorOpen = false
        }
        kotlinx.coroutines.delay(2400)
        bannerMessage = null
    }

    val displayedPhoto = when {
        removePhotoRequested -> null
        !selectedPhotoUri.isNullOrBlank() -> selectedPhotoUri
        else -> state.user?.profilePictureUrl
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SurfaceCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
                                ),
                            ),
                        )
                        .padding(20.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(104.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), RoundedCornerShape(28.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (displayedPhoto.isNullOrBlank()) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(imageVector = Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = (state.user?.firstName?.firstOrNull()?.uppercaseChar()?.toString()
                                            ?: state.user?.username?.firstOrNull()?.uppercaseChar()?.toString()
                                            ?: "U"),
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            } else {
                                AsyncImage(
                                    model = displayedPhoto,
                                    contentDescription = "Profile picture",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusBadge(text = "Secure account", color = MaterialTheme.colorScheme.secondary, compact = true)
                            Text(text = "Profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Your profile stays one tap away. Edit your details in a compact bottom sheet, upload a photo, or remove it instantly.",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                StatusBadge(text = "Live sync", color = MaterialTheme.colorScheme.primary, compact = true)
                                StatusBadge(text = "Photo ready", color = MaterialTheme.colorScheme.primary, compact = true)
                                StatusBadge(text = "Password change", color = MaterialTheme.colorScheme.primary, compact = true)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                PrimaryButton(
                                    text = "Edit profile",
                                    onClick = { editorOpen = true },
                                    modifier = Modifier.weight(1f),
                                    trailingIcon = true,
                                )
                                PrimaryButton(
                                    text = if (displayedPhoto == null) "Upload photo" else "Change photo",
                                    onClick = { photoPicker.launch(arrayOf("image/*")) },
                                    modifier = Modifier.weight(1f),
                                    trailingIcon = true,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                PrimaryButton(
                                    text = "Remove photo",
                                    onClick = {
                                        selectedPhotoUri = null
                                        removePhotoRequested = true
                                        editorOpen = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = displayedPhoto != null,
                                )
                                PrimaryButton(
                                    text = "Logout",
                                    onClick = { viewModel.logout(onLogout) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.loading) {
                    CircularProgressIndicator()
                }
                state.error?.let { ErrorStateCard(message = it, retryText = "Reload profile", onRetry = { viewModel.load() }) }
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = "Profile summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(text = "@$username", fontWeight = FontWeight.Bold)
                        Text(text = "${firstName.ifBlank { "First name" }} ${lastName.ifBlank { "Last name" }}".trim())
                        Text(
                            text = state.user?.email ?: "Email unavailable",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusBadge(text = state.user?.role ?: "User", color = MaterialTheme.colorScheme.primary, compact = true)
                            StatusBadge(text = state.user?.status ?: "Active", color = MaterialTheme.colorScheme.secondary, compact = true)
                        }
                    }
                }
            }
        }

        if (editorOpen) {
            ModalBottomSheet(
                onDismissRequest = {
                    editorOpen = false
                    selectedPhotoUri = null
                    removePhotoRequested = false
                },
                sheetState = sheetState,
            ) {
                ProfileEditorSheet(
                    username = username,
                    onUsernameChange = { username = it },
                    firstName = firstName,
                    onFirstNameChange = { firstName = it },
                    lastName = lastName,
                    onLastNameChange = { lastName = it },
                    currentPassword = currentPassword,
                    onCurrentPasswordChange = { currentPassword = it },
                    newPassword = newPassword,
                    onNewPasswordChange = { newPassword = it },
                    currentPasswordVisible = currentPasswordVisible,
                    onCurrentPasswordVisibilityChange = { currentPasswordVisible = it },
                    newPasswordVisible = newPasswordVisible,
                    onNewPasswordVisibilityChange = { newPasswordVisible = it },
                    hasPhoto = displayedPhoto != null,
                    onPickPhoto = { photoPicker.launch(arrayOf("image/*")) },
                    onRemovePhoto = {
                        selectedPhotoUri = null
                        removePhotoRequested = true
                    },
                    onClearPhotoRemoval = {
                        removePhotoRequested = false
                    },
                    onSave = {
                        viewModel.save(
                            username = username,
                            firstName = firstName,
                            lastName = lastName,
                            currentPassword = currentPassword,
                            newPassword = newPassword,
                            profilePictureUri = selectedPhotoUri?.let(Uri::parse),
                            removeProfilePicture = removePhotoRequested,
                        )
                    },
                    onDismiss = {
                        editorOpen = false
                        selectedPhotoUri = null
                        removePhotoRequested = false
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = bannerMessage != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        ) {
            bannerMessage?.let { FeedbackBanner(message = it, tone = bannerTone) }
        }
    }
}

@Composable
private fun ProfileEditorSheet(
    username: String,
    onUsernameChange: (String) -> Unit,
    firstName: String,
    onFirstNameChange: (String) -> Unit,
    lastName: String,
    onLastNameChange: (String) -> Unit,
    currentPassword: String,
    onCurrentPasswordChange: (String) -> Unit,
    newPassword: String,
    onNewPasswordChange: (String) -> Unit,
    currentPasswordVisible: Boolean,
    onCurrentPasswordVisibilityChange: (Boolean) -> Unit,
    newPasswordVisible: Boolean,
    onNewPasswordVisibilityChange: (Boolean) -> Unit,
    hasPhoto: Boolean,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onClearPhotoRemoval: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Rounded.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(text = "Edit profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "Update your name, password, or profile photo from this compact panel.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
        }

        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Profile photo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PrimaryButton(
                        text = if (hasPhoto) "Change photo" else "Upload photo",
                        onClick = {
                            onClearPhotoRemoval()
                            onPickPhoto()
                        },
                        modifier = Modifier.weight(1f),
                        trailingIcon = true,
                    )
                    PrimaryButton(
                        text = "Remove photo",
                        onClick = onRemovePhoto,
                        modifier = Modifier.weight(1f),
                        enabled = hasPhoto,
                    )
                }
            }
        }

        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Account details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = username, onValueChange = onUsernameChange, modifier = Modifier.fillMaxWidth(), label = { Text("Username") })
                OutlinedTextField(value = firstName, onValueChange = onFirstNameChange, modifier = Modifier.fillMaxWidth(), label = { Text("First name") })
                OutlinedTextField(value = lastName, onValueChange = onLastNameChange, modifier = Modifier.fillMaxWidth(), label = { Text("Last name") })
            }
        }

        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Password update", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = onCurrentPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Current password") },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Lock, contentDescription = null) },
                    visualTransformation = if (currentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { onCurrentPasswordVisibilityChange(!currentPasswordVisible) }) {
                            Icon(
                                imageVector = if (currentPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (currentPasswordVisible) "Hide current password" else "Show current password",
                            )
                        }
                    },
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = onNewPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("New password") },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Lock, contentDescription = null) },
                    visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { onNewPasswordVisibilityChange(!newPasswordVisible) }) {
                            Icon(
                                imageVector = if (newPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (newPasswordVisible) "Hide new password" else "Show new password",
                            )
                        }
                    },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            PrimaryButton(
                text = "Cancel",
                modifier = Modifier.weight(1f),
                onClick = onDismiss,
            )
            PrimaryButton(
                text = "Save changes",
                modifier = Modifier.weight(1f),
                onClick = onSave,
                trailingIcon = true,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
