package com.roomies.expensetracker.model

/**
 * Full details for one group member, resolved from users/{uid}. Used by the
 * Members dialog so the creator sees more than just a name -- unlike the
 * uid -> displayName map used by pickers elsewhere.
 */
data class Member(
    val uid: String,
    val displayName: String,
    val email: String = ""
)
