package com.roomies.expensetracker.model

import com.google.firebase.firestore.Exclude

data class Group(
    @get:Exclude var id: String = "",
    var name: String = "",
    var members: List<String> = emptyList(),
    var createdBy: String = "",
    // Extra members (beyond createdBy, who is always privileged) allowed to
    // pick "Paid By" freely when adding/editing an expense. Empty on groups
    // created before this field existed -- canManagePayers() below falls
    // back to createdBy so those groups still work with no migration.
    var admins: List<String> = emptyList()
    // createdAt intentionally omitted — not needed client-side yet, and mapping
    // Firestore Timestamp requires extra handling we don't need until Phase 5.
)

/**
 * Whether this uid may pick any member as "Paid By" when adding/editing an
 * expense. Everyone else's expenses are always attributed to themselves --
 * this is what stops one member from logging an expense as paid by someone
 * else without permission.
 */
fun Group.canManagePayers(uid: String): Boolean = uid == createdBy || uid in admins
