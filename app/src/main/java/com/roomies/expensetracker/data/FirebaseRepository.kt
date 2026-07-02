package com.roomies.expensetracker.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.roomies.expensetracker.model.AppSettings
import com.roomies.expensetracker.model.ShoppingItem
import com.roomies.expensetracker.model.Expense
import com.roomies.expensetracker.model.RecurringExpense
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * All reads are real-time Firestore listeners (callbackFlow), so when Person A
 * adds an expense, Person B's phone updates automatically as long as both
 * devices are online and pointed at the same Firebase project.
 */
object FirestoreRepository {
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val expensesRef by lazy { db.collection("expenses") }
    private val recurringRef by lazy { db.collection("recurring") }
    private val settingsDoc by lazy { db.collection("config").document("settings") }
    private val shoppingRef by lazy { db.collection("shopping_list") }

    fun observeExpenses(): Flow<List<Expense>> = callbackFlow {
        val listener = expensesRef
            .orderBy("dateMillis", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Expense::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun addExpense(expense: Expense) {
        expensesRef.add(expense).await()
    }

    suspend fun updateExpense(expense: Expense) {
        expensesRef.document(expense.id).set(expense).await()
    }

    suspend fun deleteExpense(id: String) {
        expensesRef.document(id).delete().await()
    }

    fun observeRecurring(): Flow<List<RecurringExpense>> = callbackFlow {
        val listener = recurringRef.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            val list = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(RecurringExpense::class.java)?.apply { id = doc.id }
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    suspend fun addRecurring(r: RecurringExpense) {
        recurringRef.add(r).await()
    }

    suspend fun deleteRecurring(id: String) {
        recurringRef.document(id).delete().await()
    }

    fun observeSettings(): Flow<AppSettings> = callbackFlow {
        val listener = settingsDoc.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            val settings = snapshot?.toObject(AppSettings::class.java) ?: AppSettings()
            trySend(settings)
        }
        awaitClose { listener.remove() }
    }

    suspend fun updateSettings(settings: AppSettings) {
        settingsDoc.set(settings).await()
    }

    fun observeShoppingList(): Flow<List<ShoppingItem>> = callbackFlow {
        val listener = shoppingRef
            .orderBy("addedAtMillis", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ShoppingItem::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun addShoppingItem(item: ShoppingItem) {
        shoppingRef.add(item).await()
    }

    suspend fun updateShoppingItem(item: ShoppingItem) {
        shoppingRef.document(item.id).set(item).await()
    }

    suspend fun deleteShoppingItem(id: String) {
        shoppingRef.document(id).delete().await()
    }
}