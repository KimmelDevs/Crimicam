package com.example.crimicam.presentation.main.Admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.crimicam.data.model.Criminal

@Composable
fun AdminScreen(
    viewModel: AdminViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Show error messages
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Criminal",
                    tint = Color.White
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Criminals",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${state.criminals.size} criminals • ${state.criminals.sumOf { it.imageCount }} images",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Add criminal records with multiple photos",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "💡 Add multiple photos from different angles for better identification",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // List of Criminals
                if (state.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (state.criminals.isEmpty()) {
                    EmptyStateView()
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.criminals, key = { it.id }) { criminal ->
                            CriminalCard(criminal = criminal)
                        }
                    }
                }
            }

            // Processing Overlay
            if (state.isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .padding(24.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(60.dp),
                                strokeWidth = 6.dp
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Adding Criminal to Database...",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Add Criminal Dialog
        if (showAddDialog) {
            AddCriminalDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { imageUris, criminalData ->
                    viewModel.addCriminal(
                        context = context,
                        imageUris = imageUris,
                        firstName = criminalData.firstName,
                        lastName = criminalData.lastName,
                        middleName = criminalData.middleName,
                        dateOfBirth = criminalData.dateOfBirth,
                        gender = criminalData.gender,
                        nationality = criminalData.nationality,
                        nationalId = criminalData.nationalId,
                        height = criminalData.height,
                        weight = criminalData.weight,
                        eyeColor = criminalData.eyeColor,
                        hairColor = criminalData.hairColor,
                        build = criminalData.build,
                        skinTone = criminalData.skinTone,
                        lastKnownAddress = criminalData.lastKnownAddress,
                        currentCity = criminalData.currentCity,
                        currentProvince = criminalData.currentProvince,
                        status = criminalData.status,
                        riskLevel = criminalData.riskLevel,
                        isArmed = criminalData.isArmed,
                        isDangerous = criminalData.isDangerous,
                        notes = criminalData.notes
                    )
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun CriminalCard(criminal: Criminal) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with first letter
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getFirstInitial(criminal.firstName, criminal.lastName),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Criminal Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = criminal.fullName.ifEmpty { "Unknown Criminal" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = getAgeDisplay(criminal),
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Status badge
                    Box(
                        modifier = Modifier
                            .background(
                                color = when (criminal.status) {
                                    "Wanted" -> Color.Red.copy(alpha = 0.1f)
                                    "Arrested" -> Color.Yellow.copy(alpha = 0.1f)
                                    "Active" -> Color.Green.copy(alpha = 0.1f)
                                    else -> Color.Gray.copy(alpha = 0.1f)
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = criminal.status.ifEmpty { "Unknown" },
                            fontSize = 12.sp,
                            color = when (criminal.status) {
                                "Wanted" -> Color.Red
                                "Arrested" -> Color.Yellow
                                "Active" -> Color.Green
                                else -> Color.Gray
                            },
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Image count badge
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${criminal.imageCount}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddCriminalDialog(
    onDismiss: () -> Unit,
    onAdd: (List<Uri>, CriminalData) -> Unit
) {
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var middleName by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var nationality by remember { mutableStateOf("") }
    var nationalId by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var eyeColor by remember { mutableStateOf("") }
    var hairColor by remember { mutableStateOf("") }
    var build by remember { mutableStateOf("") }
    var skinTone by remember { mutableStateOf("") }
    var lastKnownAddress by remember { mutableStateOf("") }
    var currentCity by remember { mutableStateOf("") }
    var currentProvince by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Active") }
    var riskLevel by remember { mutableStateOf("Medium") }
    var isArmed by remember { mutableStateOf(false) }
    var isDangerous by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedImageUris = selectedImageUris + it }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                item {
                    Text(
                        text = "Add New Criminal",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Image Album Section
                    Text(
                        text = "Criminal Photos",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Selected Images Grid
                    if (selectedImageUris.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(selectedImageUris) { uri ->
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.LightGray.copy(alpha = 0.3f))
                                ) {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = "Selected image",
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )

                                    // Remove button
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove image",
                                        modifier = Modifier
                                            .size(20.dp)
                                            .align(Alignment.TopEnd)
                                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                            .padding(2.dp)
                                            .clickable {
                                                selectedImageUris = selectedImageUris - uri
                                            },
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Add Image Button
                    OutlinedButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = null,
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (selectedImageUris.isNotEmpty()) "Add More Photos" else "Select Photos",
                                fontSize = 14.sp
                            )
                            if (selectedImageUris.isNotEmpty()) {
                                Text(
                                    text = "${selectedImageUris.size} photo${if (selectedImageUris.size != 1) "s" else ""} selected",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Rest of the form remains the same...
                // [Keep all the existing form fields from your previous code]
                // Personal Information Section
                item {
                    Text(
                        text = "Personal Information",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = firstName,
                            onValueChange = { firstName = it },
                            label = { Text("First Name *") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = lastName,
                            onValueChange = { lastName = it },
                            label = { Text("Last Name *") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    OutlinedTextField(
                        value = middleName,
                        onValueChange = { middleName = it },
                        label = { Text("Middle Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = dateOfBirth,
                            onValueChange = { dateOfBirth = it },
                            label = { Text("Date of Birth *") },
                            placeholder = { Text("YYYY-MM-DD") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = gender,
                            onValueChange = { gender = it },
                            label = { Text("Gender *") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = nationality,
                            onValueChange = { nationality = it },
                            label = { Text("Nationality *") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = nationalId,
                            onValueChange = { nationalId = it },
                            label = { Text("National ID") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Physical Description Section
                item {
                    Text(
                        text = "Physical Description",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = height,
                            onValueChange = { height = it },
                            label = { Text("Height (cm) *") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = weight,
                            onValueChange = { weight = it },
                            label = { Text("Weight (kg) *") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = eyeColor,
                            onValueChange = { eyeColor = it },
                            label = { Text("Eye Color *") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = hairColor,
                            onValueChange = { hairColor = it },
                            label = { Text("Hair Color *") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = build,
                            onValueChange = { build = it },
                            label = { Text("Build *") },
                            placeholder = { Text("Slim, Medium, Heavy") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = skinTone,
                            onValueChange = { skinTone = it },
                            label = { Text("Skin Tone *") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Status Section
                item {
                    Text(
                        text = "Status Information",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = status,
                            onValueChange = { status = it },
                            label = { Text("Status *") },
                            placeholder = { Text("Active, Wanted, Arrested") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = riskLevel,
                            onValueChange = { riskLevel = it },
                            label = { Text("Risk Level *") },
                            placeholder = { Text("Low, Medium, High") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = currentCity,
                            onValueChange = { currentCity = it },
                            label = { Text("Current City") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = currentProvince,
                            onValueChange = { currentProvince = it },
                            label = { Text("Current Province") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    OutlinedTextField(
                        value = lastKnownAddress,
                        onValueChange = { lastKnownAddress = it },
                        label = { Text("Last Known Address") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isArmed,
                                onCheckedChange = { isArmed = it }
                            )
                            Text("Armed", modifier = Modifier.clickable { isArmed = !isArmed })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isDangerous,
                                onCheckedChange = { isDangerous = it }
                            )
                            Text("Dangerous", modifier = Modifier.clickable { isDangerous = !isDangerous })
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Additional Notes") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = false,
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                if (selectedImageUris.isNotEmpty()) {
                                    val criminalData = CriminalData(
                                        firstName = firstName.trim(),
                                        lastName = lastName.trim(),
                                        middleName = middleName.trim(),
                                        dateOfBirth = dateOfBirth.trim(),
                                        gender = gender.trim(),
                                        nationality = nationality.trim(),
                                        nationalId = nationalId.trim(),
                                        height = height.toIntOrNull() ?: 0,
                                        weight = weight.toIntOrNull() ?: 0,
                                        eyeColor = eyeColor.trim(),
                                        hairColor = hairColor.trim(),
                                        build = build.trim(),
                                        skinTone = skinTone.trim(),
                                        lastKnownAddress = lastKnownAddress.trim(),
                                        currentCity = currentCity.trim(),
                                        currentProvince = currentProvince.trim(),
                                        status = status.trim(),
                                        riskLevel = riskLevel.trim(),
                                        isArmed = isArmed,
                                        isDangerous = isDangerous,
                                        notes = notes.trim()
                                    )
                                    onAdd(selectedImageUris, criminalData)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = selectedImageUris.isNotEmpty() &&
                                    firstName.trim().isNotBlank() &&
                                    lastName.trim().isNotBlank() &&
                                    dateOfBirth.trim().isNotBlank() &&
                                    gender.trim().isNotBlank() &&
                                    nationality.trim().isNotBlank() &&
                                    height.trim().isNotBlank() &&
                                    weight.trim().isNotBlank() &&
                                    eyeColor.trim().isNotBlank() &&
                                    hairColor.trim().isNotBlank() &&
                                    build.trim().isNotBlank() &&
                                    skinTone.trim().isNotBlank() &&
                                    status.trim().isNotBlank() &&
                                    riskLevel.trim().isNotBlank()
                        ) {
                            Text("Add Criminal")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color.Gray.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No Criminal Records Yet",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Add criminal records to populate the database",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Helper function to safely get first initial
private fun getFirstInitial(firstName: String, lastName: String): String {
    return when {
        firstName.isNotEmpty() -> firstName.first().uppercase()
        lastName.isNotEmpty() -> lastName.first().uppercase()
        else -> "?"
    }
}

// Helper function to safely display age
private fun getAgeDisplay(criminal: Criminal): String {
    return when {
        criminal.dateOfBirth.isNotEmpty() -> "${criminal.age} years • ${criminal.gender.ifEmpty { "Unknown" }}"
        criminal.gender.isNotEmpty() -> criminal.gender
        else -> "No information"
    }
}

// Data class to hold criminal form data
data class CriminalData(
    val firstName: String,
    val lastName: String,
    val middleName: String = "",
    val dateOfBirth: String,
    val gender: String,
    val nationality: String,
    val nationalId: String = "",
    val height: Int,
    val weight: Int,
    val eyeColor: String,
    val hairColor: String,
    val build: String,
    val skinTone: String,
    val lastKnownAddress: String = "",
    val currentCity: String = "",
    val currentProvince: String = "",
    val status: String,
    val riskLevel: String,
    val isArmed: Boolean = false,
    val isDangerous: Boolean = false,
    val notes: String = ""
)