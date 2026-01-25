package com.example.crimicam.presentation.main.Profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.crimicam.data.model.FriendRequest
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    navController: NavController,
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    var showProfileDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showFriendCodeDialog by remember { mutableStateOf(false) }
    var showAddFriendPopup by remember { mutableStateOf(false) }
    var showFriendsListPopup by remember { mutableStateOf(false) }
    var showFriendRequestsPopup by remember { mutableStateOf(false) }
    var friendCodeInput by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()
    val logoutState by viewModel.logoutState.collectAsState()
    val friendCode by viewModel.friendCode.collectAsState()
    val isLoadingFriendCode by viewModel.isLoadingFriendCode.collectAsState()
    val friendCodeError by viewModel.friendCodeError.collectAsState()
    val friendsList by viewModel.friendsList.collectAsState()
    val friendRequests by viewModel.friendRequests.collectAsState()
    val sentFriendRequests by viewModel.sentFriendRequests.collectAsState()
    val isLoadingFriends by viewModel.isLoadingFriends.collectAsState()
    val friendsError by viewModel.friendsError.collectAsState()
    val addFriendState by viewModel.addFriendState.collectAsState()
    val friendRequestsLoading by viewModel.friendRequestsLoading.collectAsState()

    val scope = rememberCoroutineScope()

    LaunchedEffect(logoutState) {
        when (logoutState) {
            is LogoutState.Success -> {
                onLogout()
                viewModel.resetLogoutState()
            }
            else -> {}
        }
    }

    LaunchedEffect(addFriendState) {
        when (val state = addFriendState) {
            is AddFriendState.Success -> {
                scope.launch {
                    kotlinx.coroutines.delay(2000)
                    friendCodeInput = ""
                    showAddFriendPopup = false
                    viewModel.resetAddFriendState()
                }
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable {
                    if (friendCode != null) {
                        showFriendCodeDialog = true
                    }
                },
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFE8F5E9)
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Your Friend Code",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF388E3C)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        when {
                            isLoadingFriendCode -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF388E3C)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Loading code...",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                            friendCodeError != null -> {
                                Text(
                                    text = "Error: ${friendCodeError}",
                                    fontSize = 12.sp,
                                    color = Color.Red
                                )
                            }
                            friendCode != null -> {
                                Text(
                                    text = friendCode!!,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1B5E20),
                                    letterSpacing = 2.sp
                                )
                            }
                            else -> {
                                Text(
                                    text = "No friend code found",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    if (friendCode != null) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = Color(0xFF388E3C),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                if (friendCode != null) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Share this code with friends to add them",
                        fontSize = 12.sp,
                        color = Color(0xFF66BB6A),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        SettingsSection(title = "Friends") {
            val pendingRequests = friendRequests.size
            SettingsItemWithBadge(
                icon = Icons.Default.Notifications,
                title = "Friend Requests",
                subtitle = "View and manage friend requests",
                badgeCount = if (pendingRequests > 0) pendingRequests else null,
                onClick = { showFriendRequestsPopup = true }
            )

            SettingsItem(
                icon = Icons.Default.PersonAdd,
                title = "Add Friend",
                subtitle = "Add a friend using their code",
                onClick = { showAddFriendPopup = true }
            )

            SettingsItem(
                icon = Icons.Default.Group,
                title = "My Friends",
                subtitle = "View your friends list (${friendsList.size})",
                onClick = { showFriendsListPopup = true }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        SettingsSection(title = "Account") {
            SettingsItem(
                icon = Icons.Default.Person,
                title = "View Profile",
                subtitle = "See your profile information",
                onClick = { navController.navigate("view_profile") }
            )
            SettingsItem(
                icon = Icons.Default.Person,
                title = "Add a Location Label",
                subtitle = "Whether it be a residence, a shop, and more!",
                onClick = { navController.navigate("location_label") }
            )
            SettingsItem(
                icon = Icons.Default.Info,
                title = "About",
                subtitle = "App version 1.0.0",
                onClick = { }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable { showLogoutDialog = true },
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFFEBEE)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = "Logout",
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Log Out",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFD32F2F)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showAddFriendPopup) {
        AddFriendPopup(
            friendCodeInput = friendCodeInput,
            onFriendCodeInputChange = { friendCodeInput = it },
            addFriendState = addFriendState,
            onAddFriend = {
                if (friendCodeInput.length == 6) {
                    viewModel.addFriendByCode(friendCodeInput)
                }
            },
            onDismiss = {
                showAddFriendPopup = false
                friendCodeInput = ""
                viewModel.resetAddFriendState()
            }
        )
    }

    if (showFriendRequestsPopup) {
        FriendRequestsPopup(
            friendRequests = friendRequests,
            sentFriendRequests = sentFriendRequests,
            isLoading = friendRequestsLoading,
            onAcceptFriend = { requestId ->
                viewModel.acceptFriendRequest(requestId)
            },
            onDeclineFriend = { requestId ->
                viewModel.declineFriendRequest(requestId)
            },
            onCancelRequest = { requestId ->
                viewModel.cancelFriendRequest(requestId)
            },
            onRefresh = {
                viewModel.loadFriendRequests()
                viewModel.loadSentFriendRequests()
            },
            onDismiss = { showFriendRequestsPopup = false }
        )
    }

    if (showFriendsListPopup) {
        FriendsListPopup(
            friendsList = friendsList,
            isLoading = isLoadingFriends,
            error = friendsError,
            onRefresh = { viewModel.loadFriends() },
            onRemoveFriend = { friendId ->
                viewModel.removeFriend(friendId)
            },
            onDismiss = { showFriendsListPopup = false }
        )
    }

    if (showFriendCodeDialog && friendCode != null) {
        FriendCodeDialog(
            friendCode = friendCode!!,
            onDismiss = { showFriendCodeDialog = false }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = null,
                    tint = Color(0xFFD32F2F)
                )
            },
            title = { Text("Log Out") },
            text = { Text("Are you sure you want to log out of your account?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.logout()
                        showLogoutDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F)
                    ),
                    enabled = logoutState != LogoutState.Loading
                ) {
                    if (logoutState == LogoutState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Log Out")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false },
                    enabled = logoutState != LogoutState.Loading
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AddFriendPopup(
    friendCodeInput: String,
    onFriendCodeInputChange: (String) -> Unit,
    addFriendState: AddFriendState,
    onAddFriend: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Add Friend",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Enter your friend's 6-character code",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = friendCodeInput,
                    onValueChange = { newValue ->
                        onFriendCodeInputChange(newValue.take(6).uppercase())
                    },
                    placeholder = { Text("e.g., A1B2C3") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    trailingIcon = {
                        if (friendCodeInput.isNotEmpty()) {
                            IconButton(
                                onClick = { onFriendCodeInputChange("") }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    isError = addFriendState is AddFriendState.Error
                )

                Spacer(modifier = Modifier.height(8.dp))

                when (addFriendState) {
                    is AddFriendState.Loading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Adding friend...",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }
                    }
                    is AddFriendState.Success -> {
                        Text(
                            text = addFriendState.message,
                            fontSize = 14.sp,
                            color = Color.Green,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is AddFriendState.Error -> {
                        Text(
                            text = addFriendState.message,
                            fontSize = 14.sp,
                            color = Color.Red,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    else -> {}
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onAddFriend,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = friendCodeInput.length == 6 && addFriendState !is AddFriendState.Loading
                ) {
                    Text("Add Friend")
                }
            }
        }
    }
}

@Composable
fun FriendRequestsPopup(
    friendRequests: List<FriendRequest>,
    sentFriendRequests: List<FriendRequest>,
    isLoading: Boolean,
    onAcceptFriend: (String) -> Unit,
    onDeclineFriend: (String) -> Unit,
    onCancelRequest: (String) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Friend Requests",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = friendRequests.size.toString(),
                                color = Color.White
                            )
                        }
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh"
                            )
                        }
                    }
                }

                Divider()

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    var selectedTab by remember { mutableStateOf(0) }
                    val tabs = listOf("Incoming (${friendRequests.size})", "Sent (${sentFriendRequests.size})")

                    TabRow(selectedTabIndex = selectedTab) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(title) }
                            )
                        }
                    }

                    when (selectedTab) {
                        0 -> {
                            if (friendRequests.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "No requests",
                                        modifier = Modifier.size(48.dp),
                                        tint = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "No friend requests",
                                        fontSize = 16.sp,
                                        color = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "When someone adds you, they'll appear here",
                                        fontSize = 12.sp,
                                        color = Color.LightGray,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    items(friendRequests) { request ->
                                        FriendRequestItem(
                                            request = request,
                                            isIncoming = true,
                                            onAccept = { onAcceptFriend(request.id) },
                                            onDecline = { onDeclineFriend(request.id) }
                                        )
                                    }
                                }
                            }
                        }
                        1 -> {
                            if (sentFriendRequests.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = "No sent requests",
                                        modifier = Modifier.size(48.dp),
                                        tint = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "No sent requests",
                                        fontSize = 16.sp,
                                        color = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Requests you've sent will appear here",
                                        fontSize = 12.sp,
                                        color = Color.LightGray,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    items(sentFriendRequests) { request ->
                                        FriendRequestItem(
                                            request = request,
                                            isIncoming = false,
                                            onCancel = { onCancelRequest(request.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun FriendsListPopup(
    friendsList: List<com.example.crimicam.data.model.User>,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onRemoveFriend: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "My Friends",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${friendsList.size} friends",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh"
                            )
                        }
                    }
                }

                Divider()

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (error != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            modifier = Modifier.size(48.dp),
                            tint = Color.Red
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Error loading friends",
                            fontSize = 16.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error,
                            fontSize = 12.sp,
                            color = Color.LightGray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onRefresh) {
                            Text("Retry")
                        }
                    }
                } else if (friendsList.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = "No friends",
                            modifier = Modifier.size(48.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No friends yet",
                            fontSize = 16.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add friends using their friend codes",
                            fontSize = 12.sp,
                            color = Color.LightGray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        items(friendsList) { friend ->
                            FriendListItem(
                                user = friend,
                                onRemove = { onRemoveFriend(friend.uid) }
                            )
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun FriendRequestItem(
    request: FriendRequest,
    isIncoming: Boolean,
    onAccept: (() -> Unit)? = null,
    onDecline: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF5F5F5)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    val displayName = if (isIncoming) request.fromUserName else request.toUserName
                    Text(
                        text = displayName.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString(""),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isIncoming) request.fromUserName else request.toUserName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isIncoming) "Wants to be your friend" else "Request sent",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (isIncoming) {
                    Button(
                        onClick = { onDecline?.invoke() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.Red
                        ),
                        border = BorderStroke(1.dp, Color.Red),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Decline")
                    }

                    Button(
                        onClick = { onAccept?.invoke() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Accept")
                    }
                } else {
                    Button(
                        onClick = { onCancel?.invoke() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.Red
                        ),
                        border = BorderStroke(1.dp, Color.Red),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel Request")
                    }
                }
            }
        }
    }
}

@Composable
fun FriendListItem(
    user: com.example.crimicam.data.model.User,
    onRemove: () -> Unit
) {
    var showRemoveDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF5F5F5)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString(""),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                if (user.friendCode.isNotBlank()) {
                    Text(
                        text = "Code: ${user.friendCode}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            IconButton(
                onClick = { showRemoveDialog = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove Friend",
                    tint = Color.Gray
                )
            }
        }
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = Color.Red
                )
            },
            title = { Text("Remove Friend") },
            text = { Text("Are you sure you want to remove ${user.name} from your friends?") },
            confirmButton = {
                Button(
                    onClick = {
                        onRemove()
                        showRemoveDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red
                    )
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRemoveDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsItemWithBadge(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badgeCount: Int?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            if (badgeCount != null && badgeCount > 0) {
                Badge(
                    containerColor = Color.Red,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-4).dp)
                ) {
                    Text(
                        text = badgeCount.toString(),
                        fontSize = 10.sp,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = Color.Gray
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Navigate",
            tint = Color.Gray,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun FriendCodeDialog(
    friendCode: String,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Tag,
                contentDescription = null,
                tint = Color(0xFF388E3C)
            )
        },
        title = { Text("Your Friend Code") },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE8F5E9), RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = friendCode,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B5E20),
                        letterSpacing = 4.sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Share this code with friends so they can add you. Anyone with this code can send you a friend request.",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(friendCode))
                    scope.launch {
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF388E3C)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy Code")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            content()
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = Color.Gray
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Navigate",
            tint = Color.Gray,
            modifier = Modifier.size(20.dp)
        )
    }
}