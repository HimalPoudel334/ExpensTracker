package com.roomies.expensetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group as GroupIcon
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.roomies.expensetracker.model.Member
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
    var groupToViewMembers by remember { mutableStateOf<Group?>(null) }
    var memberToRemove by remember { mutableStateOf<Pair<Group, Member>?>(null) }
    var leaveBlockedMessage by remember { mutableStateOf<String?>(null) }

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
                            onInvite = { groupToInvite = group },
                            onMembers = { groupToViewMembers = group }
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
                    viewModel.leaveGroup(group) { message -> leaveBlockedMessage = message }
                    groupToLeave = null
                }) { Text("Leave", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { groupToLeave = null }) { Text("Cancel") }
            }
        )
    }

    leaveBlockedMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { leaveBlockedMessage = null },
            title = { Text("Can't leave yet") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { leaveBlockedMessage = null }) { Text("Got it") }
            }
        )
    }

    groupToInvite?.let { group ->
        InviteDialog(
            group = group,
            onDismiss = { groupToInvite = null },
            onGenerate = { liableForThisMonth, onDone ->
                viewModel.createInvite(group.id, liableForThisMonth, onDone)
            },
            onCopy = { code ->
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(android.content.ClipData.newPlainText("Invite code", code)))
                }
            }
        )
    }

    groupToViewMembers?.let { group ->
        val currentUserId by viewModel.currentUserId.collectAsState()
        val memberDetails by remember(group.id, group.members) {
            viewModel.observeMemberDetails(group.members)
        }.collectAsState(initial = emptyList())

        MembersSheet(
            group = group,
            members = memberDetails,
            isCreator = group.createdBy == currentUserId,
            onDismiss = { groupToViewMembers = null },
            onRemove = { member -> memberToRemove = group to member }
        )
    }

    memberToRemove?.let { (group, member) ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text("Remove ${member.displayName}?") },
            text = { Text("They'll lose access to \"${group.name}\" and its expenses unless invited back.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeMember(group, member.uid)
                    memberToRemove = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) { Text("Cancel") }
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
    onInvite: () -> Unit,
    onMembers: () -> Unit
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
            IconButton(onClick = onMembers) {
                Icon(Icons.Filled.GroupIcon, contentDescription = "View members")
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
    onGenerate: (Boolean, (Result<String>) -> Unit) -> Unit,
    onCopy: (String) -> Unit
) {
    // Asked BEFORE any code is generated so it's the admin's decision alone --
    // whoever joins with the code can't see or change this choice.
    var liableForThisMonth by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite to \"${group.name}\"") },
        text = {
            Column {
                when {
                    code == null -> {
                        Text(
                            "Should whoever joins with this code share this month's " +
                                "already-logged expenses, or only expenses from now on?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !liableForThisMonth,
                                onClick = { liableForThisMonth = false }
                            )
                            Text("Only new expenses from now on")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = liableForThisMonth,
                                onClick = { liableForThisMonth = true }
                            )
                            Text("Include this month's shared expenses too")
                        }
                        if (loading) {
                            CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                        }
                        error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    else -> {
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
            if (code == null) {
                TextButton(
                    onClick = {
                        loading = true
                        error = null
                        onGenerate(liableForThisMonth) { result ->
                            loading = false
                            result.onSuccess { code = it }
                            result.onFailure { error = it.message ?: "Couldn't create invite" }
                        }
                    },
                    enabled = !loading
                ) { Text("Generate Code") }
            } else {
                TextButton(onClick = { code?.let(onCopy) }) { Text("Copy") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (code == null) "Cancel" else "Close") }
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

/**
 * Lists every member's name and email in a bottom sheet rather than an
 * AlertDialog -- AlertDialog's content area doesn't scroll well and gets
 * cramped once a group has more than a handful of members, whereas a sheet
 * with a LazyColumn (plus a search field) stays usable as the list grows.
 * Only the group's creator sees the remove action, and never for
 * themselves -- creators leave via deleting or leaving the group elsewhere,
 * not by "removing" themselves here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MembersSheet(
    group: Group,
    members: List<Member>,
    isCreator: Boolean,
    onDismiss: () -> Unit,
    onRemove: (Member) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }

    val filtered = remember(members, query) {
        if (query.isBlank()) members
        else members.filter {
            it.displayName.contains(query, ignoreCase = true) || it.email.contains(query, ignoreCase = true)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                "Members of \"${group.name}\"",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (members.size > 6) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search members") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
            }

            when {
                members.isEmpty() -> Text(
                    "Loading members…",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
                filtered.isEmpty() -> Text(
                    "No members match \"$query\"",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
                ) {
                    items(filtered, key = { it.uid }) { member ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(member.displayName, style = MaterialTheme.typography.titleSmall)
                                    if (member.uid == group.createdBy) {
                                        Icon(
                                            Icons.Filled.AdminPanelSettings,
                                            contentDescription = "Creator",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 6.dp)
                                        )
                                    }
                                }
                                if (member.email.isNotBlank()) {
                                    Text(member.email, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (isCreator && member.uid != group.createdBy) {
                                IconButton(onClick = { onRemove(member) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Remove ${member.displayName}",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }

            TextButton(onClick = onDismiss, modifier = Modifier.padding(vertical = 12.dp)) {
                Text("Close")
            }
        }
    }
}