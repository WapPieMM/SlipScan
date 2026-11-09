package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Expense
import com.example.data.model.UserBudget
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ExpenseRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeExpenses(userId: String = requireUserId()): Flow<List<Expense>> = flow {
        val path = "expenses"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Expense::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }.sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getUserExpenses(userId: String = requireUserId()): Result<List<Expense>> {
        val path = "expenses"
        return try {
            val snapshot = db.collection(path)
                .whereEqualTo("userId", userId)
                .get()
                .await()
            val list = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Expense::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }.sortedByDescending { it.createdAt?.seconds ?: 0L }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun getExpenseById(expenseId: String): Result<Expense?> {
        val path = "expenses/$expenseId"
        return try {
            val doc = db.collection("expenses").document(expenseId).get().await()
            Result.success(doc.toObject(Expense::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun addExpense(
        merchantName: String,
        bankName: String,
        amount: Double,
        category: String,
        transactionDate: String,
        referenceNo: String,
        memo: String,
        isFromSlip: Boolean
    ): Result<String> {
        val uid = requireUserId()
        val docRef = db.collection("expenses").document()
        val cleanMerchant = merchantName.trim().take(120).ifBlank { "ไม่ระบุชื่อร้านค้า" }
        val cleanBank = bankName.trim().take(80).ifBlank { "ธนาคาร/พร้อมเพย์" }
        val safeAmount = amount.coerceIn(0.01, 100000000.0)
        val cleanDate = transactionDate.trim().take(50).ifBlank { "วันนี้" }
        val cleanRef = referenceNo.trim().take(100)
        val cleanMemo = memo.trim().take(300)

        val payload = mapOf(
            "userId" to uid,
            "merchantName" to cleanMerchant,
            "bankName" to cleanBank,
            "amount" to safeAmount,
            "category" to category,
            "transactionDate" to cleanDate,
            "referenceNo" to cleanRef,
            "memo" to cleanMemo,
            "isFromSlip" to isFromSlip,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateExpense(expense: Expense): Result<Unit> {
        val uid = requireUserId()
        require(expense.id.isNotBlank()) { "Expense ID must not be blank for update" }
        requireNotNull(expense.createdAt) { "Expense createdAt must not be null for update" }
        val docRef = db.collection("expenses").document(expense.id)

        val payload = mapOf(
            "userId" to uid,
            "merchantName" to expense.merchantName.trim().take(120).ifBlank { "ไม่ระบุชื่อร้านค้า" },
            "bankName" to expense.bankName.trim().take(80).ifBlank { "ธนาคาร/พร้อมเพย์" },
            "amount" to expense.amount.coerceIn(0.01, 100000000.0),
            "category" to expense.category,
            "transactionDate" to expense.transactionDate.trim().take(50).ifBlank { "วันนี้" },
            "referenceNo" to expense.referenceNo.trim().take(100),
            "memo" to expense.memo.trim().take(300),
            "isFromSlip" to expense.isFromSlip,
            "createdAt" to expense.createdAt,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteExpense(expenseId: String): Result<Unit> {
        requireUserId()
        val docRef = db.collection("expenses").document(expenseId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeBudget(userId: String = requireUserId()): Flow<UserBudget?> = flow {
        val path = "budgets/$userId"
        emitAll(
            db.collection("budgets").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(UserBudget::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveMonthlyBudget(monthlyLimit: Double): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("budgets").document(uid)
        val safeLimit = monthlyLimit.coerceIn(0.0, 100000000.0)
        val payload = mapOf(
            "userId" to uid,
            "monthlyLimit" to safeLimit,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }
}
