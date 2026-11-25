package com.x8bit.bitwarden.multiwarden

import com.bitwarden.core.data.repository.model.DataState
import com.x8bit.bitwarden.data.vault.repository.VaultRepository
import com.x8bit.bitwarden.data.vault.repository.model.VaultData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Multiwarden: the combined vault. When it is on and more than one account is unlocked, the vault
 * screens show the items and folders of every unlocked account together. Each folder is named
 * "account · folder" and each item's subtitle ends with its account, so it is clear whose they
 * are.
 *
 * Bitwarden's screens keep reading the vault through [vaultDataStateFlow] instead of
 * [VaultRepository.vaultDataStateFlow]; outside the combined vault the two are the same.
 */
interface MultiwardenCombinedVault {
    /**
     * Whether the combined vault is switched on (it stays on until a single account is picked).
     */
    val isCombinedModeOnFlow: StateFlow<Boolean>

    /**
     * Whether the combined vault can be offered: more than one account is unlocked.
     */
    val isCombinedModeAvailableFlow: StateFlow<Boolean>

    /**
     * The active account's vault data, plus, in the combined vault, every other unlocked
     * account's items and folders, labelled with their account.
     */
    val vaultDataStateFlow: StateFlow<DataState<VaultData>>

    /**
     * Switches the combined vault on or off.
     */
    fun setCombinedModeOn(isOn: Boolean)

    /**
     * The account, other than the active one, that the item with the given [cipherId] in
     * [vaultDataStateFlow] belongs to, or `null` when it is the active account's (or unknown).
     */
    fun otherOwnerOf(cipherId: String): MultiwardenAccount?

    companion object {
        /**
         * A [MultiwardenCombinedVault] that is always off and passes [vaultRepository]'s data
         * through unchanged. Used as the default in Bitwarden's classes, so their own tests keep
         * testing Bitwarden's behaviour.
         */
        fun passThrough(vaultRepository: VaultRepository): MultiwardenCombinedVault =
            object : MultiwardenCombinedVault {
                override val isCombinedModeOnFlow: StateFlow<Boolean> =
                    MutableStateFlow(false).asStateFlow()
                override val isCombinedModeAvailableFlow: StateFlow<Boolean> =
                    MutableStateFlow(false).asStateFlow()
                override val vaultDataStateFlow: StateFlow<DataState<VaultData>>
                    get() = vaultRepository.vaultDataStateFlow

                override fun setCombinedModeOn(isOn: Boolean) = Unit
                override fun otherOwnerOf(cipherId: String): MultiwardenAccount? = null
            }
    }
}
