package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.ThaiBank
import com.example.data.model.UserBudget
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.viewmodel.UiState
import java.text.NumberFormat
import java.util.Locale

fun formatBaht(amount: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    return "฿${formatter.format(amount)}"
}

fun categoryIcon(category: ExpenseCategory): ImageVector {
    return when (category) {
        ExpenseCategory.FOOD -> Icons.Default.Fastfood
        ExpenseCategory.SHOPPING -> Icons.Default.ShoppingBag
        ExpenseCategory.TRANSPORT -> Icons.Default.DirectionsCar
        ExpenseCategory.BILLS -> Icons.AutoMirrored.Filled.ReceiptLong
        ExpenseCategory.TRANSFER -> Icons.Default.SwapHoriz
        ExpenseCategory.ENTERTAINMENT -> Icons.Default.Movie
        ExpenseCategory.HEALTH -> Icons.Default.Favorite
        ExpenseCategory.OTHER -> Icons.Default.MoreHoriz
    }
}

@Composable
fun DashboardScreen(
    expensesState: UiState<List<Expense>>,
    budget: UserBudget,
    selectedCategory: ExpenseCategory?,
    searchQuery: String,
    onCategorySelected: (ExpenseCategory?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onPickSlipsFromGallery: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onOpenManualAdd: () -> Unit,
    onEditExpense: (Expense) -> Unit,
    onDeleteExpense: (String) -> Unit,
    onSaveBudget: (Double) -> Unit
) {
    var showBudgetDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }

    val allExpenses = (expensesState as? UiState.Success)?.data.orEmpty()
    val totalSpent = allExpenses.sumOf { it.amount }
    val slipCount = allExpenses.count { it.isFromSlip }

    val filteredExpenses = remember(allExpenses, selectedCategory, searchQuery) {
        allExpenses.filter { exp ->
            val matchesCat = selectedCategory == null || exp.category == selectedCategory.name
            val q = searchQuery.trim().lowercase()
            val matchesSearch = q.isEmpty() ||
                exp.merchantName.lowercase().contains(q) ||
                exp.bankName.lowercase().contains(q) ||
                exp.memo.lowercase().contains(q) ||
                exp.referenceNo.lowercase().contains(q)
            matchesCat && matchesSearch
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hero Summary & Quick Slip Import Card
        item {
            HeroSpendingCard(
                totalSpent = totalSpent,
                monthlyLimit = budget.monthlyLimit,
                totalTransactions = allExpenses.size,
                slipTransactions = slipCount,
                onPickSlipsFromGallery = onPickSlipsFromGallery,
                onNavigateToScanner = onNavigateToScanner,
                onEditBudgetClick = { showBudgetDialog = true }
            )
        }

        // 2. Search & Quick Add Manual Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            text = "ค้นหาร้านค้า ธนาคาร หรือบันทึก...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "ค้นหา"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "ล้างคำค้นหา"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_expense_input")
                )

                Surface(
                    onClick = onOpenManualAdd,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("manual_add_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "จดรายจ่ายเอง",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "จดเอง",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // 3. Category Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onCategorySelected(null) },
                    label = { Text("ทั้งหมด (${allExpenses.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                ExpenseCategory.entries.forEach { cat ->
                    val count = allExpenses.count { it.category == cat.name }
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = {
                            onCategorySelected(if (selectedCategory == cat) null else cat)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = categoryIcon(cat),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                if (count > 0) "${cat.labelTh} ($count)" else cat.labelTh
                            )
                        }
                    )
                }
            }
        }

        // 4. Expenses List or Empty/Loading State
        when (expensesState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            is UiState.Error -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = expensesState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            is UiState.Success -> {
                if (filteredExpenses.isEmpty()) {
                    item {
                        EmptyExpensesCard(
                            hasAnyExpenses = allExpenses.isNotEmpty(),
                            onPickSlipsFromGallery = onPickSlipsFromGallery,
                            onNavigateToScanner = onNavigateToScanner
                        )
                    }
                } else {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "รายการรายจ่ายล่าสุด (${filteredExpenses.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "รวม ${formatBaht(filteredExpenses.sumOf { it.amount })}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    items(filteredExpenses, key = { it.id }) { expense ->
                        ExpenseItemCard(
                            expense = expense,
                            onEditClick = { editingExpense = expense },
                            onDeleteClick = { onDeleteExpense(expense.id) }
                        )
                    }
                }
            }
        }
    }

    if (showBudgetDialog) {
        BudgetEditDialog(
            currentLimit = budget.monthlyLimit,
            onDismiss = { showBudgetDialog = false },
            onSave = { newLimit ->
                onSaveBudget(newLimit)
                showBudgetDialog = false
            }
        )
    }

    if (editingExpense != null) {
        ExpenseFormDialog(
            initialExpense = editingExpense,
            onDismiss = { editingExpense = null },
            onSave = { merchant, bank, amount, cat, date, ref, memo ->
                val updated = editingExpense!!.copy(
                    merchantName = merchant,
                    bankName = bank,
                    amount = amount,
                    category = cat.name,
                    transactionDate = date,
                    referenceNo = ref,
                    memo = memo
                )
                onEditExpense(updated)
                editingExpense = null
            }
        )
    }
}

@Composable
private fun HeroSpendingCard(
    totalSpent: Double,
    monthlyLimit: Double,
    totalTransactions: Int,
    slipTransactions: Int,
    onPickSlipsFromGallery: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onEditBudgetClick: () -> Unit
) {
    val progress = if (monthlyLimit > 0) (totalSpent / monthlyLimit).toFloat().coerceIn(0f, 1f) else 0f
    val remaining = (monthlyLimit - totalSpent)
    val isOverBudget = monthlyLimit > 0 && totalSpent > monthlyLimit

    Card(
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF064E3B),
                            Color(0xFF047857),
                            Color(0xFF0F766E)
                        )
                    )
                )
                .padding(22.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ยอดใช้จ่ายสะสมทั้งหมด",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFA7F3D0)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatBaht(totalSpent),
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontFamily = SpaceGroteskFontFamily
                            ),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        onClick = onEditBudgetClick,
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("edit_budget_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "แก้ไขงบรายเดือน",
                                tint = Color(0xFFFDE68A),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "งบ ${formatBaht(monthlyLimit)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    color = if (isOverBudget) Color(0xFFF87171) else Color(0xFFFBBF24),
                    trackColor = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isOverBudget) {
                            "เกินงบที่ตั้งไว้ ${formatBaht(-remaining)}"
                        } else {
                            "คงเหลือใช้งบได้อีก ${formatBaht(remaining)}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isOverBudget) Color(0xFFFECACA) else Color(0xFFD1FAE5)
                    )
                    Text(
                        text = "สแกนสลิป $slipTransactions / $totalTransactions รายการ",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFD1FAE5)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onPickSlipsFromGallery,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFBBF24),
                            contentColor = Color(0xFF451A03)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(50.dp)
                            .testTag("hero_pick_slip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ดึงรูปสลิปจากอัลบั้ม",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onNavigateToScanner,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(0.9f)
                            .height(50.dp)
                            .testTag("hero_sample_slip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFFFDE68A),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "สลิปตัวอย่าง",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyExpensesCard(
    hasAnyExpenses: Boolean,
    onPickSlipsFromGallery: () -> Unit,
    onNavigateToScanner: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DocumentScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (hasAnyExpenses) "ไม่พบรายการที่ตรงกับการค้นหา" else "ยังไม่มีรายการรายจ่าย",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (hasAnyExpenses) {
                    "ลองเปลี่ยนคำค้นหาหรือตัวกรองหมวดหมู่ด้านบน"
                } else {
                    "เลือกรูปสลิปโอนเงินจากธนาคารในเครื่องของคุณ หรือทดลองสแกนด้วยสลิปตัวอย่างเพื่อให้ AI จดรายจ่ายให้อัตโนมัติ"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!hasAnyExpenses) {
                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onPickSlipsFromGallery,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("เลือกรูปสลิป")
                    }
                    OutlinedButton(
                        onClick = onNavigateToScanner,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ลองสลิปตัวอย่าง")
                    }
                }
            }
        }
    }
}

@Composable
fun ExpenseItemCard(
    expense: Expense,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val cat = ExpenseCategory.fromKey(expense.category)
    val catColor = Color(cat.colorHex)
    val bankColor = Color(ThaiBank.matchBankColor(expense.bankName))
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .testTag("expense_card_${expense.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(catColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon(cat),
                        contentDescription = cat.labelTh,
                        tint = catColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = expense.merchantName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (expense.isFromSlip) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "สลิป AI",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = bankColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = expense.bankName,
                                style = MaterialTheme.typography.labelMedium,
                                color = bankColor,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "• ${expense.transactionDate}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "-${formatBaht(expense.amount)}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = SpaceGroteskFontFamily
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = cat.labelTh,
                        style = MaterialTheme.typography.labelMedium,
                        color = catColor
                    )
                }
            }

            if (expense.memo.isNotBlank() || expense.referenceNo.isNotBlank() || expanded) {
                AnimatedVisibility(visible = expanded || expense.memo.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        if (expense.memo.isNotBlank()) {
                            Text(
                                text = "บันทึก: ${expense.memo}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (expanded) {
                            if (expense.referenceNo.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "เลขที่อ้างอิงสลิป: ${expense.referenceNo}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = onEditClick) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "แก้ไข",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("แก้ไขรายการ")
                                }
                                TextButton(
                                    onClick = onDeleteClick,
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "ลบ",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ลบ")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BudgetEditDialog(
    currentLimit: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var limitInput by remember { mutableStateOf(currentLimit.toInt().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ตั้งงบประมาณรายเดือน") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "กำหนดเป้าหมายการใช้จ่ายต่อเดือนเพื่อช่วยควบคุมรายจ่ายจากสลิปธนาคาร",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = limitInput,
                    onValueChange = { limitInput = it },
                    label = { Text("งบประมาณต่อเดือน (บาท)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("budget_limit_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = limitInput.toDoubleOrNull() ?: currentLimit
                    onSave(parsed.coerceAtLeast(100.0))
                },
                modifier = Modifier.testTag("save_budget_button")
            ) {
                Text("บันทึกงบ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ยกเลิก")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseFormDialog(
    initialExpense: Expense? = null,
    onDismiss: () -> Unit,
    onSave: (
        merchantName: String,
        bankName: String,
        amount: Double,
        category: ExpenseCategory,
        transactionDate: String,
        referenceNo: String,
        memo: String
    ) -> Unit
) {
    var merchantName by remember { mutableStateOf(initialExpense?.merchantName ?: "") }
    var bankName by remember { mutableStateOf(initialExpense?.bankName ?: ThaiBank.KBANK.displayName) }
    var amountText by remember {
        mutableStateOf(if (initialExpense != null) initialExpense.amount.toString() else "")
    }
    var category by remember {
        mutableStateOf(
            if (initialExpense != null) ExpenseCategory.fromKey(initialExpense.category)
            else ExpenseCategory.FOOD
        )
    }
    var transactionDate by remember { mutableStateOf(initialExpense?.transactionDate ?: "9 พ.ย. 69") }
    var referenceNo by remember { mutableStateOf(initialExpense?.referenceNo ?: "") }
    var memo by remember { mutableStateOf(initialExpense?.memo ?: "") }
    var catExpanded by remember { mutableStateOf(false) }
    var bankExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialExpense == null) "จดรายจ่ายใหม่" else "แก้ไขรายการรายจ่าย")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = merchantName,
                    onValueChange = { merchantName = it },
                    label = { Text("ชื่อร้านค้า / ผู้รับโอน") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("form_merchant_input")
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("จำนวนเงิน (บาท)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("form_amount_input")
                )

                ExposedDropdownMenuBox(
                    expanded = categoryExpanded(catExpanded),
                    onExpandedChange = { catExpanded = !catExpanded }
                ) {
                    OutlinedTextField(
                        value = category.labelTh,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("หมวดหมู่รายจ่าย") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = catExpanded,
                        onDismissRequest = { catExpanded = false }
                    ) {
                        ExpenseCategory.entries.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.labelTh) },
                                onClick = {
                                    category = item
                                    catExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = bankExpanded,
                    onExpandedChange = { bankExpanded = !bankExpanded }
                ) {
                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it },
                        label = { Text("ธนาคาร / ช่องทางชำระ") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = bankExpanded)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = bankExpanded,
                        onDismissRequest = { bankExpanded = false }
                    ) {
                        ThaiBank.entries.forEach { bank ->
                            DropdownMenuItem(
                                text = { Text(bank.displayName) },
                                onClick = {
                                    bankName = bank.displayName
                                    bankExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = transactionDate,
                    onValueChange = { transactionDate = it },
                    label = { Text("วันที่และเวลา") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("บันทึกช่วยจำ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (amt > 0 && merchantName.isNotBlank()) {
                        onSave(
                            merchantName,
                            bankName,
                            amt,
                            category,
                            transactionDate,
                            referenceNo,
                            memo
                        )
                    }
                },
                enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0 && merchantName.isNotBlank(),
                modifier = Modifier.testTag("form_save_button")
            ) {
                Text("บันทึก")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ยกเลิก")
            }
        }
    )
}

private fun categoryExpanded(value: Boolean): Boolean = value
