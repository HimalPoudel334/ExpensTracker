package com.roomies.expensetracker.model

import com.google.firebase.firestore.Exclude

data class Group(
    @get:Exclude var id: String = "",
    var name: String = "",
    var members: List<String> = emptyList(),
    var createdBy: String = ""
    // createdAt intentionally omitted — not needed client-side yet, and mapping
    // Firestore Timestamp requires extra handling we don't need until Phase 5.
)
