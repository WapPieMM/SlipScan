package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.ExpenseCategory
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ExpenseRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun addExpense_andObserveExpenses_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = ExpenseRepository(firestore)

        val addResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addExpense(
                merchantName = "Cafe Amazon",
                bankName = "KBank",
                amount = 65.0,
                category = ExpenseCategory.FOOD.name,
                transactionDate = "9 พ.ย. 69 08:45 น.",
                referenceNo = "KB014312",
                memo = "Iced Latte",
                isFromSlip = true
            )
        }
        assertTrue("Expected addExpense to succeed: ${addResult.exceptionOrNull()}", addResult.isSuccess)
        val expenseId = addResult.getOrThrow()
        assertTrue(expenseId.isNotEmpty())

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeExpenses(uid).first { list -> list.any { it.id == expenseId } }
        }
        assertTrue(emitted.any { it.id == expenseId && it.merchantName == "Cafe Amazon" })
    }

    @Test
    fun getExpenseById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = ExpenseRepository(firestore)
        val expenseId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addExpense(
                merchantName = "Lotus's",
                bankName = "SCB",
                amount = 489.5,
                category = ExpenseCategory.SHOPPING.name,
                transactionDate = "9 พ.ย. 69 12:30 น.",
                referenceNo = "SCB884210",
                memo = "Groceries",
                isFromSlip = true
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = ExpenseRepository(firestore)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getExpenseById(expenseId)
        }
        assertTrue("Expected cross-user access to fail", result.isFailure)
        val firestoreEx = result.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeExpenses_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = ExpenseRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeExpenses("unauth_user_123").first()
            }
            fail("Expected observeExpenses to throw exception when unauthenticated")
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
        }
    }

    @Test
    fun saveAndObserveMonthlyBudget_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = ExpenseRepository(firestore)

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveMonthlyBudget(18000.0)
        }
        assertTrue("Expected saveMonthlyBudget to succeed", saveResult.isSuccess)

        val budget = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeBudget(uid).first { it != null && it.monthlyLimit == 18000.0 }
        }
        assertNotNull(budget)
        assertEquals(18000.0, budget!!.monthlyLimit, 0.01)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@slipnote.test"
        const val BOB_EMAIL = "bob@slipnote.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
