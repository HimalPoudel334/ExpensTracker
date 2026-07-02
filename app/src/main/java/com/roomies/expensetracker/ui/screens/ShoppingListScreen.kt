package com.roomies.expensetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.roomies.expensetracker.model.ShoppingItem
import com.roomies.expensetracker.ui.navigation.Screen
import com.roomies.expensetracker.util.DeviceConfig
import com.roomies.expensetracker.viewmodel.MainViewModel

@Composable
fun ShoppingListScreen(viewModel: MainViewModel, navController: NavController) {

    val context = LocalContext.current

    val items by viewModel.shoppingList.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ShoppingItem?>(null) }
    var purchasedItem by remember { mutableStateOf<ShoppingItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Shopping List", style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add item")
            }
        }
        Text(
            "Shared with both phones in real-time",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (items.isEmpty()) {
            Text("No items yet — tap + to add")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    ShoppingItemRow(
                        item = item,
                        onEdit = { editingItem = item },
                        onDelete = { viewModel.deleteShoppingItem(item.id) },
                        onPurchased = { purchasedItem = item }
                    )
                }
            }
        }
    }

    val expenses by viewModel.expenses.collectAsState()

    if (showAddDialog) {
        AddShoppingItemDialog(
            personNames = listOf(settings.personAName, settings.personBName),
            existingItems = items,
            expenseItems = expenses.map { it.item },
            onAdd = { name, qty, note, addedBy ->
                viewModel.addShoppingItem(
                    ShoppingItem(
                        name = name,
                        quantity = qty,
                        note = note,
                        addedBy = addedBy,
                        addedByMe = DeviceConfig.isMyDevice(context)
                    )
                )
            },
            onDismiss = { showAddDialog = false }
        )
    }

    editingItem?.let { item ->
        EditShoppingItemDialog(
            item = item,
            onSave = { viewModel.updateShoppingItem(it) },
            onDismiss = { editingItem = null }
        )
    }

    // Purchased confirmation dialog
    purchasedItem?.let { item ->
        AlertDialog(
            onDismissRequest = { purchasedItem = null },
            title = { Text("Item Purchased") },
            text = { Text("Save \"${item.name}\" as an expense?") },
            confirmButton = {
                Button(onClick = {
                    // Do NOT delete here — AddExpenseScreen deletes after save
                    viewModel.setPendingShoppingItem(item)
                    purchasedItem = null
                    navController.navigate(Screen.Add.route) {
                        launchSingleTop = true
                    }
                }) { Text("Yes, Add Expense") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        // "Just remove" — delete immediately, no expense needed
                        viewModel.deleteShoppingItem(item.id)
                        purchasedItem = null
                    }) { Text("No, Just Remove") }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { purchasedItem = null }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
fun ShoppingItemRow(
    item: ShoppingItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPurchased: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                if (item.quantity.isNotBlank())
                    Text("Qty: ${item.quantity}", style = MaterialTheme.typography.bodySmall)
                if (item.note.isNotBlank())
                    Text(item.note, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Added by: ${item.addedBy}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = onPurchased) {
                Icon(Icons.Filled.ShoppingCartCheckout, contentDescription = "Purchased",
                    tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun AddShoppingItemDialog(
    personNames: List<String>,
    existingItems: List<ShoppingItem>,
    expenseItems: List<String>,
    onAdd: (name: String, qty: String, note: String, addedBy: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var addedBy by remember { mutableStateOf(personNames.firstOrNull() ?: "") }

    val nameSuggestions = remember(existingItems, expenseItems, name) {
        val query = name.trim()
        if (query.isBlank()) emptyList()
        else (existingItems.map { it.name } + expenseItems)
            .distinctBy { it.lowercase() }
            .filter { it.contains(query, ignoreCase = true) && !it.equals(query, ignoreCase = true) }
            .sortedBy { it.lowercase() }
            .take(6)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Shopping Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AutocompleteTextField(
                    label = "Item name",
                    value = name,
                    suggestions = nameSuggestions,
                    onValueChange = { name = it },
                    onSuggestionSelected = { name = it }
                )
                OutlinedTextField(
                    value = qty, onValueChange = { qty = it },
                    label = { Text("Quantity (optional)") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth()
                )
                DropdownField("Added by", personNames, addedBy) { addedBy = it }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) {
                    onAdd(name.trim(), qty.trim(), note.trim(), addedBy)
                    onDismiss()
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun EditShoppingItemDialog(
    item: ShoppingItem,
    onSave: (ShoppingItem) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var qty by remember { mutableStateOf(item.quantity) }
    var note by remember { mutableStateOf(item.note) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Item name") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = qty, onValueChange = { qty = it },
                    label = { Text("Quantity") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Note") }, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) {
                    onSave(item.copy(name = name.trim(), quantity = qty.trim(), note = note.trim()))
                    onDismiss()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}