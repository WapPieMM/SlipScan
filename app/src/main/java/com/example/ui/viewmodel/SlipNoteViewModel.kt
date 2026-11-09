package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.SlipAiAnalyzer
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.SampleSlipGenerator
import com.example.data.model.SampleSlipPreset
import com.example.data.model.SlipExtractionResult
import com.example.data.model.UserBudget
import com.example.data.repository.ExpenseRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface SlipScanState {
    data object Idle : SlipScanState
    data class Scanning(val currentIndex: Int, val totalCount: Int) : SlipScanState
    data class ReviewQueue(val extractedSlips: List<SlipExtractionResult>) : SlipScanState
    data class Error(val message: String) : SlipScanState
}

class SlipNoteViewModel(
    private val repository: ExpenseRepository,
    private val currentUserId: String,
    private val aiAnalyzer: SlipAiAnalyzer = SlipAiAnalyzer()
) : ViewModel() {

    val expensesState: StateFlow<UiState<List<Expense>>> = repository.observeExpenses(currentUserId)
        .map<List<Expense>, UiState<List<Expense>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing expenses", rootError)
            emit(UiState.Error(rootError.message ?: "ไม่สามารถโหลดรายการรายจ่ายได้"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val budgetState: StateFlow<UserBudget> = repository.observeBudget(currentUserId)
        .map { it ?: UserBudget(userId = currentUserId, monthlyLimit = 15000.0) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing budget", firestoreError ?: error)
            emit(UserBudget(userId = currentUserId, monthlyLimit = 15000.0))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UserBudget(userId = currentUserId, monthlyLimit = 15000.0)
        )

    private val _selectedCategory = MutableStateFlow<ExpenseCategory?>(null)
    val selectedCategory: StateFlow<ExpenseCategory?> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _slipScanState = MutableStateFlow<SlipScanState>(SlipScanState.Idle)
    val slipScanState: StateFlow<SlipScanState> = _slipScanState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun setCategoryFilter(category: ExpenseCategory?) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun resetSlipScanState() {
        _slipScanState.value = SlipScanState.Idle
    }

    /**
     * Analyzes one or more bitmaps selected from the user's photo gallery using Firebase AI Logic.
     */
    fun analyzeSlipBitmaps(bitmaps: List<Bitmap>) {
        if (bitmaps.isEmpty()) return
        viewModelScope.launch {
            val extracted = mutableListOf<SlipExtractionResult>()
            var lastError: String? = null

            for ((index, bitmap) in bitmaps.withIndex()) {
                _slipScanState.value = SlipScanState.Scanning(
                    currentIndex = index + 1,
                    totalCount = bitmaps.size
                )
                val result = aiAnalyzer.analyzeSlipBitmap(bitmap)
                result.onSuccess { item ->
                    extracted.add(item)
                }.onFailure { err ->
                    lastError = err.localizedMessage ?: "ไม่สามารถอ่านข้อมูลสลิปได้"
                }
            }

            if (extracted.isNotEmpty()) {
                _slipScanState.value = SlipScanState.ReviewQueue(extracted)
                if (lastError != null) {
                    _snackbarMessage.value = "อ่านสลิปสำเร็จ ${extracted.size} รูป (บางรูปอ่านไม่สำเร็จ)"
                }
            } else {
                _slipScanState.value = SlipScanState.Error(
                    lastError ?: "ไม่สามารถอ่านข้อมูลจากรูปสลิปที่เลือกได้ กรุณาลองใหม่อีกครั้ง"
                )
            }
        }
    }

    /**
     * Renders a realistic Thai bank e-slip bitmap from a preset and passes it through
     * the real Gemini AI multimodal analyzer so users can test AI slip extraction anytime.
     */
    fun analyzeSampleSlipPreset(preset: SampleSlipPreset) {
        viewModelScope.launch {
            _slipScanState.value = SlipScanState.Scanning(currentIndex = 1, totalCount = 1)
            val bitmap = withContext(Dispatchers.Default) {
                SampleSlipGenerator.renderSlipBitmap(preset)
            }
            val result = aiAnalyzer.analyzeSlipBitmap(bitmap)
            result.onSuccess { extracted ->
                _slipScanState.value = SlipScanState.ReviewQueue(listOf(extracted))
            }.onFailure { err ->
                _slipScanState.value = SlipScanState.Error(
                    err.localizedMessage ?: "วิเคราะห์สลิปตัวอย่างไม่สำเร็จ"
                )
            }
        }
    }

    fun updateReviewedSlip(index: Int, updated: SlipExtractionResult) {
        val current = _slipScanState.value
        if (current is SlipScanState.ReviewQueue) {
            val list = current.extractedSlips.toMutableList()
            if (index in list.indices) {
                list[index] = updated
                _slipScanState.value = SlipScanState.ReviewQueue(list)
            }
        }
    }

    fun removeReviewedSlip(index: Int) {
        val current = _slipScanState.value
        if (current is SlipScanState.ReviewQueue) {
            val list = current.extractedSlips.toMutableList()
            if (index in list.indices) {
                list.removeAt(index)
                if (list.isEmpty()) {
                    _slipScanState.value = SlipScanState.Idle
                } else {
                    _slipScanState.value = SlipScanState.ReviewQueue(list)
                }
            }
        }
    }

    fun saveAllReviewedSlips(onSavedSuccess: () -> Unit = {}) {
        val current = _slipScanState.value
        if (current !is SlipScanState.ReviewQueue || current.extractedSlips.isEmpty()) return
        viewModelScope.launch {
            var successCount = 0
            var failMessage: String? = null
            for (slip in current.extractedSlips) {
                val res = repository.addExpense(
                    merchantName = slip.merchantName,
                    bankName = slip.bankName,
                    amount = slip.amount,
                    category = slip.category.name,
                    transactionDate = slip.transactionDate,
                    referenceNo = slip.referenceNo,
                    memo = slip.memo,
                    isFromSlip = true
                )
                if (res.isSuccess) {
                    successCount++
                } else {
                    failMessage = res.exceptionOrNull()?.localizedMessage
                }
            }
            if (successCount > 0) {
                _slipScanState.value = SlipScanState.Idle
                _snackbarMessage.value = "บันทึกสลิปลงบัญชีรายจ่ายเรียบร้อย $successCount รายการ"
                onSavedSuccess()
            } else {
                _snackbarMessage.value = failMessage ?: "บันทึกรายจ่ายไม่สำเร็จ"
            }
        }
    }

    fun addManualExpense(
        merchantName: String,
        bankName: String,
        amount: Double,
        category: ExpenseCategory,
        transactionDate: String,
        referenceNo: String,
        memo: String,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repository.addExpense(
                merchantName = merchantName,
                bankName = bankName,
                amount = amount,
                category = category.name,
                transactionDate = transactionDate,
                referenceNo = referenceNo,
                memo = memo,
                isFromSlip = false
            )
            res.onSuccess {
                _snackbarMessage.value = "บันทึกรายจ่ายเรียบร้อยแล้ว"
                onComplete()
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "บันทึกรายจ่ายไม่สำเร็จ"
            }
        }
    }

    fun updateExistingExpense(
        expense: Expense,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repository.updateExpense(expense)
            res.onSuccess {
                _snackbarMessage.value = "อัปเดตรายการรายจ่ายเรียบร้อยแล้ว"
                onComplete()
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "อัปเดตรายการไม่สำเร็จ"
            }
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            val res = repository.deleteExpense(expenseId)
            res.onSuccess {
                _snackbarMessage.value = "ลบรายการรายจ่ายแล้ว"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "ลบรายการไม่สำเร็จ"
            }
        }
    }

    fun saveMonthlyBudget(newLimit: Double) {
        viewModelScope.launch {
            val res = repository.saveMonthlyBudget(newLimit)
            res.onSuccess {
                _snackbarMessage.value = "ตั้งงบประมาณรายเดือนเรียบร้อยแล้ว"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "ตั้งงบประมาณไม่สำเร็จ"
            }
        }
    }

    private companion object {
        const val TAG = "SlipNoteViewModel"
    }
}
