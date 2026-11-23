package com.x8bit.bitwarden.multiwarden

import com.x8bit.bitwarden.data.autofill.model.AutofillCipher

/**
 * Separates an item's own subtitle from its account label.
 */
private const val LABEL_SEPARATOR: String = " · "

/**
 * Multiwarden: loads autofill items from the active account (if any) and then from every other
 * unlocked account, labelling each item with its account when more than one account is unlocked.
 *
 * @param activeUserId The active account's user ID, or `null` when there is none.
 * @param load Loads the items of one account; it is given that account's user ID.
 */
suspend fun <T : AutofillCipher> MultiAccountVault.autofillCiphersOfAllAccounts(
    activeUserId: String?,
    load: suspend (userId: String) -> List<T>,
): List<T> {
    val userIds = listOfNotNull(activeUserId) + otherUnlockedAccounts().map { it.userId }
    return userIds.flatMap { userId ->
        val label = accountLabelOrNull(userId = userId)
        load(userId).map { it.withAccountLabel(label = label) }
    }
}

/**
 * Multiwarden: adds the account [label] to this item's subtitle, so the autofill suggestion shows
 * which account the item comes from. Returns the item unchanged when [label] is `null`.
 */
@Suppress("UNCHECKED_CAST")
fun <T : AutofillCipher> T.withAccountLabel(label: String?): T {
    if (label.isNullOrBlank()) return this
    val labelledSubtitle = listOf(subtitle, label)
        .filter { it.isNotBlank() }
        .joinToString(separator = LABEL_SEPARATOR)
    val autofillCipher: AutofillCipher = this
    return when (autofillCipher) {
        is AutofillCipher.Card -> autofillCipher.copy(subtitle = labelledSubtitle)
        is AutofillCipher.Identity -> autofillCipher.copy(subtitle = labelledSubtitle)
        is AutofillCipher.Login -> autofillCipher.copy(subtitle = labelledSubtitle)
    } as T
}
