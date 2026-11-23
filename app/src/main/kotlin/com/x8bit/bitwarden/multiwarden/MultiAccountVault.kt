package com.x8bit.bitwarden.multiwarden

import com.bitwarden.vault.CipherListView
import com.x8bit.bitwarden.data.vault.manager.model.GetCipherResult
import com.x8bit.bitwarden.data.vault.repository.model.GenerateTotpResult
import java.time.Instant

/**
 * Multiwarden: gives access to the vaults of unlocked accounts other than the active one.
 *
 * Bitwarden keeps several vaults unlocked at the same time (an account stays unlocked after
 * switching away until its own vault timeout passes), but only reads the active account's vault.
 * Upstream code keeps handling the active account itself and asks this class about the others.
 */
interface MultiAccountVault {
    /**
     * Unlocked accounts other than the active one, in the account switcher's order.
     */
    fun otherUnlockedAccounts(): List<MultiwardenAccount>

    /**
     * The label to show next to an item of the account with the given [userId], or `null` when
     * no other account is unlocked and labels would only add noise.
     */
    fun accountLabelOrNull(userId: String): String?

    /**
     * Decrypts the cipher list of the account with the given [userId], or returns `null` when
     * that account is not unlocked.
     */
    suspend fun getCipherListViews(userId: String): List<CipherListView>?

    /**
     * Decrypts the cipher with the given [cipherId] from the account with the given [userId].
     */
    suspend fun getCipher(userId: String, cipherId: String): GetCipherResult

    /**
     * The unlocked, non-active account that owns the cipher with the given [cipherId], or `null`
     * when no such account does (for example because the active account owns it).
     */
    suspend fun findOtherOwner(cipherId: String): MultiwardenAccount?

    /**
     * Generates the current TOTP code of the cipher with the given [cipherId] in the account with
     * the given [userId].
     */
    suspend fun generateTotp(userId: String, cipherId: String, time: Instant): GenerateTotpResult
}

/**
 * Multiwarden: an account whose vault can be read.
 *
 * @property userId The account's user ID.
 * @property email The account's email address, used to label its items.
 * @property isPremium Whether the account has premium features, such as TOTP.
 */
data class MultiwardenAccount(
    val userId: String,
    val email: String,
    val isPremium: Boolean,
)
