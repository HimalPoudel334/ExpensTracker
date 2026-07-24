package com.roomies.expensetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import com.roomies.expensetracker.model.Group
import com.roomies.expensetracker.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun GroupsScreen(viewModel: MainViewModel) {
    val groups by viewModel.groups.collectAsState()
    val activeGroup by viewModel.activeGroup.collectAsState()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var groupToRename by remember { mutableStateOf<Group?>(null) }
    var groupToLeave by remember { mutableStateOf<Group?>(null) }
    var groupToInvite by remember { mutableStateOf<Group?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Create group")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Your Groups", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Tap a group to switch to it.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 12.dp, top = 4.dp)
            )

            OutlinedButton(
                onClick = { showJoinDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.GroupAdd, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Join a group with a code")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            if (groups.isEmpty()) {
                Text(
                    "You're not in any group yet. Create one, or join with a code from a roommate.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(groups, key = { it.id }) { group ->
                        GroupRow(
                            group = group,
                            isActive = group.id == activeGroup?.id,
                            onSelect = { viewModel.selectGroup(group.id) },
                            onRename = { groupToRename = group },
                            onLeave = { groupToLeave = group },
                            onInvite = { groupToInvite = group }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateGroupDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                viewModel.createGroup(name)
                showCreateDialog = false
            }
        )
    }

    if (showJoinDialog) {
        JoinGroupDialog(
            onDismiss = { showJoinDialog = false },
            onJoin = { code, onDone -> viewModel.joinGroup(code, onDone) },
            onJoined = { showJoinDialog = false }
        )
    }

    groupToRename?.let { group ->
        RenameGroupDialog(
            currentName = group.name,
            onDismiss = { groupToRename = null },
            onRename = { newName ->
                viewModel.renameGroup(group.id, newName)
                groupToRename = null
            }
        )
    }

    groupToLeave?.let { group ->
        AlertDialog(
            onDismissRequest = { groupToLeave = null },
            title = { Text("Leave \"${group.name}\"?") },
            text = { Text("You'll lose access to this group's expenses unless someone invites you back.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.leaveGroup(group.id)
                    groupToLeave = null
                }) { Text("Leave", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { groupToLeave = null }) { Text("Cancel") }
            }
        )
    }

    groupToInvite?.let { group ->
        InviteDialog(
            group = group,
            onDismiss = { groupToInvite = null },
            onGenerate = { onDone -> viewModel.createInvite(group.id, onDone) },
            onCopy = { code ->
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(android.content.ClipData.newPlainText("Invite code", code)))
                }
            }
        )
    }
}

@Composable
private fun GroupRow(
    group: Group,
    isActive: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onLeave: () -> Unit,
    onInvite: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = isActive, onClick = onSelect)
            Column(modifier = Modifier.weight(1f)) {
                Text(group.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${group.members.size} member${if (group.members.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onInvite) {
                Icon(Icons.Filled.Share, contentDescription = "Invite to group")
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Filled.Edit, contentDescription = "Rename group")
            }
            TextButton(onClick = onLeave) {
                Text("Leave")
            }
        }
    }
}

@Composable
private fun CreateGroupDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create group") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Group name") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun RenameGroupDialog(currentName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename group") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Group name") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(name) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun InviteDialog(
    group: Group,
    onDismiss: () -> Unit,
    onGenerate: ((Result<String>) -> Unit) -> Unit,
    onCopy: (String) -> Unit
) {
    var code by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Generate a code as soon as the dialog opens.
    LaunchedEffect(group.id) {
        loading = true
        onGenerate { result ->
            loading = false
            result.onSuccess { code = it }
            result.onFailure { error = it.message ?: "Couldn't create invite" }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite to \"${group.name}\"") },
        text = {
            Column {
                when {
                    loading -> CircularProgressIndicator()
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    code != null -> {
                        Text(
                            "Share this code. It's valid for 72 hours and works for anyone who has it:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            code!!,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { code?.let(onCopy) },
                enabled = code != null
            ) { Text("Copy") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun JoinGroupDialog(
    onDismiss: () -> Unit,
    onJoin: (String, (Result<Unit>) -> Unit) -> Unit,
    onJoined: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join a group") },
        text = {
            Column {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it; error = null },
                    label = { Text("Invite code") },
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.padding(horizontal = 16.dp))
            } else {
                TextButton(
                    onClick = {
                        loading = true
                        onJoin(code) { result ->
                            loading = false
                            result.onSuccess { onJoined() }
                            result.onFailure { error = it.message ?: "Couldn't join group" }
                        }
                    },
                    enabled = code.isNotBlank()
                ) { Text("Join") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}