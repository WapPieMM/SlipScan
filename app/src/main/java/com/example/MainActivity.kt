package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.repository.ExpenseRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOut
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExpenseFormDialog
import com.example.ui.screens.SlipScannerScreen
import com.example.ui.theme.SlipNoteTheme
import com.example.ui.viewmodel.SlipNoteViewModel
import com.example.ui.viewmodel.UiState
import com.example.util.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory Step 1: Configure App Check with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            SlipNoteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        val uid = user.uid
        key(uid) {
            SlipNoteMainApp(
                currentUserId = uid,
                userEmail = user.email ?: user.displayName ?: ""
            )
        }
    }
}

enum class AppTab(val label: String) {
    DASHBOARD("หน้าแรก"),
    SCANNER("ดึงรูปสลิป"),
    ANALYTICS("สรุปงบ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlipNoteMainApp(
    currentUserId: String,
    userEmail: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: SlipNoteViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                SlipNoteViewModel(
                    repository = ExpenseRepository(db),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val expensesState by viewModel.expensesState.collectAsStateWithLifecycle()
    val budget by viewModel.budgetState.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val slipScanState by viewModel.slipScanState.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(AppTab.DASHBOARD) }
    var showManualDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    if (currentTab != AppTab.DASHBOARD) {
        BackHandler {
            currentTab = AppTab.DASHBOARD
        }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    // Zero-permission Android Photo Picker (supports picking up to 10 bank slips at once)
    val multipleSlipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            currentTab = AppTab.SCANNER
            scope.launch {
                val bitmaps = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri -> decodeBitmapFromUri(context, uri) }
                }
                viewModel.analyzeSlipBitmaps(bitmaps)
            }
        }
    }

    val launchSlipPicker = {
        multipleSlipPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "SlipNote",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (userEmail.isNotBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "• ${userEmail.substringBefore("@")}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            signOut(
                                credentialManager = credentialManager,
                                scope = scope
                            )
                        },
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "ออกจากระบบ"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (currentTab == AppTab.DASHBOARD) {
                ExtendedFloatingActionButton(
                    onClick = launchSlipPicker,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = "สแกนรูปสลิป"
                        )
                    },
                    text = {
                        Text(
                            text = "ดึงรูปสลิป",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_pick_slip")
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == AppTab.DASHBOARD,
                    onClick = { currentTab = AppTab.DASHBOARD },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == AppTab.DASHBOARD) Icons.Filled.Home else Icons.Outlined.Home,
                            contentDescription = AppTab.DASHBOARD.label
                        )
                    },
                    label = { Text(AppTab.DASHBOARD.label) },
                    modifier = Modifier.testTag("nav_tab_dashboard")
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.SCANNER,
                    onClick = { currentTab = AppTab.SCANNER },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == AppTab.SCANNER) Icons.Filled.DocumentScanner else Icons.Outlined.DocumentScanner,
                            contentDescription = AppTab.SCANNER.label
                        )
                    },
                    label = { Text(AppTab.SCANNER.label) },
                    modifier = Modifier.testTag("nav_tab_scanner")
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.ANALYTICS,
                    onClick = { currentTab = AppTab.ANALYTICS },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == AppTab.ANALYTICS) Icons.Filled.PieChart else Icons.Outlined.PieChart,
                            contentDescription = AppTab.ANALYTICS.label
                        )
                    },
                    label = { Text(AppTab.ANALYTICS.label) },
                    modifier = Modifier.testTag("nav_tab_analytics")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AppTab.DASHBOARD -> {
                    DashboardScreen(
                        expensesState = expensesState,
                        budget = budget,
                        selectedCategory = selectedCategory,
                        searchQuery = searchQuery,
                        onCategorySelected = viewModel::setCategoryFilter,
                        onSearchQueryChange = viewModel::setSearchQuery,
                        onPickSlipsFromGallery = launchSlipPicker,
                        onNavigateToScanner = { currentTab = AppTab.SCANNER },
                        onOpenManualAdd = { showManualDialog = true },
                        onEditExpense = { updated -> viewModel.updateExistingExpense(updated) },
                        onDeleteExpense = { id -> viewModel.deleteExpense(id) },
                        onSaveBudget = { limit -> viewModel.saveMonthlyBudget(limit) }
                    )
                }

                AppTab.SCANNER -> {
                    SlipScannerScreen(
                        scanState = slipScanState,
                        onPickSlipsFromGallery = launchSlipPicker,
                        onSelectSamplePreset = { preset ->
                            viewModel.analyzeSampleSlipPreset(preset)
                        },
                        onUpdateReviewedSlip = viewModel::updateReviewedSlip,
                        onRemoveReviewedSlip = viewModel::removeReviewedSlip,
                        onSaveAllReviewed = {
                            viewModel.saveAllReviewedSlips(
                                onSavedSuccess = { currentTab = AppTab.DASHBOARD }
                            )
                        },
                        onResetScan = viewModel::resetSlipScanState
                    )
                }

                AppTab.ANALYTICS -> {
                    val allExpenses = (expensesState as? UiState.Success)?.data.orEmpty()
                    AnalyticsScreen(
                        expenses = allExpenses,
                        budget = budget
                    )
                }
            }
        }
    }

    if (showManualDialog) {
        ExpenseFormDialog(
            initialExpense = null,
            onDismiss = { showManualDialog = false },
            onSave = { merchant, bank, amount, cat, date, ref, memo ->
                viewModel.addManualExpense(
                    merchantName = merchant,
                    bankName = bank,
                    amount = amount,
                    category = cat,
                    transactionDate = date,
                    referenceNo = ref,
                    memo = memo,
                    onComplete = { showManualDialog = false }
                )
            }
        )
    }
}

private fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    } catch (e: Exception) {
        null
    }
}
