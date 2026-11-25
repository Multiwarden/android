package com.x8bit.bitwarden.multiwarden

import android.content.SharedPreferences
import androidx.core.content.edit
import com.bitwarden.core.data.manager.dispatcher.DispatcherManager
import com.bitwarden.core.data.repository.model.DataState
import com.bitwarden.core.data.repository.util.map
import com.bitwarden.vault.CipherListView
import com.bitwarden.vault.FolderView
import com.x8bit.bitwarden.data.auth.repository.AuthRepository
import com.x8bit.bitwarden.data.auth.repository.model.UserState
import com.x8bit.bitwarden.data.vault.datasource.disk.VaultDiskSource
import com.x8bit.bitwarden.data.vault.datasource.sdk.VaultSdkSource
import com.x8bit.bitwarden.data.vault.repository.VaultRepository
import com.x8bit.bitwarden.data.vault.repository.model.VaultData
import com.x8bit.bitwarden.data.vault.repository.util.toEncryptedSdkCipher
import com.x8bit.bitwarden.data.vault.repository.util.toEncryptedSdkFolderList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * Where the combined vault setting is stored.
 */
private const val COMBINED_MODE_KEY: String = "multiwarden:combinedMode"

/**
 * Separates a folder's or item's own text from its account label.
 */
private const val LABEL_SEPARATOR: String = " · "

/**
 * How long the combined vault data keeps being computed after its last subscriber leaves.
 */
private const val STOP_TIMEOUT_MS: Long = 5_000L

/**
 * Multiwarden: the default [MultiwardenCombinedVault]. Other accounts' items and folders are read
 * from their encrypted vault on disk and decrypted with that account's own SDK client, the same
 * way [MultiAccountVaultImpl] does for autofill, so they update whenever that account syncs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MultiwardenCombinedVaultImpl(
    authRepository: AuthRepository,
    private val vaultRepository: VaultRepository,
    private val vaultDiskSource: VaultDiskSource,
    private val vaultSdkSource: VaultSdkSource,
    private val sharedPreferences: SharedPreferences,
    dispatcherManager: DispatcherManager,
) : MultiwardenCombinedVault {
    private val scope = CoroutineScope(dispatcherManager.unconfined)

    private val mutableCombinedModeOnFlow =
        MutableStateFlow(sharedPreferences.getBoolean(COMBINED_MODE_KEY, false))

    /** The active account and the other unlocked accounts. */
    private val accountsFlow: StateFlow<Accounts> = authRepository
        .userStateFlow
        .map { it.toAccounts() }
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = authRepository.userStateFlow.value.toAccounts(),
        )

    /** Which other account each item of the combined vault belongs to, by item ID. */
    @Volatile
    private var otherOwners: Map<String, MultiwardenAccount> = emptyMap()

    override val isCombinedModeOnFlow: StateFlow<Boolean> = mutableCombinedModeOnFlow.asStateFlow()

    override val isCombinedModeAvailableFlow: StateFlow<Boolean> = accountsFlow
        .map { it.others.isNotEmpty() }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = accountsFlow.value.others.isNotEmpty(),
        )

    /** The other unlocked accounts' vaults while the combined vault is on, otherwise none. */
    private val otherVaultsFlow: Flow<List<OtherVault>> =
        combine(mutableCombinedModeOnFlow, accountsFlow) { isOn, accounts ->
            if (isOn) accounts.others else emptyList()
        }
            .distinctUntilChanged()
            .flatMapLatest { others ->
                if (others.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(others.map { otherVaultFlow(account = it) }) { it.toList() }
                }
            }

    override val vaultDataStateFlow: StateFlow<DataState<VaultData>> =
        combine(
            vaultRepository.vaultDataStateFlow,
            otherVaultsFlow.onStart { emit(emptyList()) },
            accountsFlow,
        ) { dataState, otherVaults, accounts ->
            otherOwners = otherVaults
                .flatMap { vault -> vault.ciphers.mapNotNull { it.id?.to(vault.account) } }
                .toMap()
            if (otherVaults.isEmpty()) {
                dataState
            } else {
                dataState.map { it.withOtherVaults(active = accounts.active, others = otherVaults) }
            }
        }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = STOP_TIMEOUT_MS),
                initialValue = vaultRepository.vaultDataStateFlow.value,
            )

    override fun setCombinedModeOn(isOn: Boolean) {
        sharedPreferences.edit { putBoolean(COMBINED_MODE_KEY, isOn) }
        mutableCombinedModeOnFlow.value = isOn
    }

    override fun otherOwnerOf(cipherId: String): MultiwardenAccount? {
        val owner = otherOwners[cipherId] ?: return null
        return owner.takeIf { it.userId != accountsFlow.value.active?.userId }
    }

    private fun otherVaultFlow(account: MultiwardenAccount): Flow<OtherVault> =
        combine(
            vaultDiskSource.getCiphersFlow(userId = account.userId),
            vaultDiskSource.getFoldersFlow(userId = account.userId),
        ) { ciphers, folders -> ciphers to folders }
            .mapLatest { (ciphers, folders) ->
                OtherVault(
                    account = account,
                    ciphers = vaultSdkSource
                        .decryptCipherListWithFailures(
                            userId = account.userId,
                            cipherList = ciphers.map { it.toEncryptedSdkCipher() },
                        )
                        .getOrNull()
                        ?.successes
                        .orEmpty(),
                    folders = vaultSdkSource
                        .decryptFolderList(
                            userId = account.userId,
                            folderList = folders.toEncryptedSdkFolderList(),
                        )
                        .getOrNull()
                        .orEmpty(),
                )
            }
}

/**
 * Adds the [others]' items and folders to this vault data, labelling every item and folder with
 * its account. An item shared with the active account (for example through an organization) is
 * kept once, as the active account's.
 */
internal fun VaultData.withOtherVaults(
    active: MultiwardenAccount?,
    others: List<OtherVault>,
): VaultData {
    val activeLabel = active?.label
    val activeIds = decryptCipherListResult.successes.mapNotNull { it.id }.toSet()
    val otherCiphers = others.flatMap { vault ->
        vault.ciphers
            .filter { it.id == null || it.id !in activeIds }
            .map { it.withAccountLabel(label = vault.account.label) }
    }
    return copy(
        decryptCipherListResult = decryptCipherListResult.copy(
            successes = decryptCipherListResult.successes
                .map { it.withAccountLabel(label = activeLabel) } + otherCiphers,
        ),
        folderViewList = folderViewList.map { it.withAccountLabel(label = activeLabel) } +
            others.flatMap { vault ->
                vault.folders.map { it.withAccountLabel(label = vault.account.label) }
            },
    )
}

/**
 * How the combined vault names an account: the part of its email address before the "@", as in
 * Multiwarden's browser extension and desktop app.
 */
internal val MultiwardenAccount.label: String
    get() = email.substringBefore("@")

private fun CipherListView.withAccountLabel(label: String?): CipherListView {
    if (label.isNullOrBlank()) return this
    return copy(
        subtitle = listOf(subtitle, label)
            .filter { it.isNotBlank() }
            .joinToString(separator = LABEL_SEPARATOR),
    )
}

private fun FolderView.withAccountLabel(label: String?): FolderView {
    if (label.isNullOrBlank() || id == null) return this
    return copy(name = "$label$LABEL_SEPARATOR$name")
}

/**
 * The vault of an unlocked account other than the active one.
 */
internal data class OtherVault(
    val account: MultiwardenAccount,
    val ciphers: List<CipherListView>,
    val folders: List<FolderView>,
)

/**
 * The active account and the other unlocked accounts.
 */
private data class Accounts(
    val active: MultiwardenAccount?,
    val others: List<MultiwardenAccount>,
)

private fun UserState?.toAccounts(): Accounts {
    if (this == null) return Accounts(active = null, others = emptyList())
    val accountsByMultiwarden = accounts.map {
        it to MultiwardenAccount(userId = it.userId, email = it.email, isPremium = it.isPremium)
    }
    return Accounts(
        active = accountsByMultiwarden.firstOrNull { it.first.userId == activeUserId }?.second,
        others = accountsByMultiwarden
            .filter { (account, _) ->
                account.userId != activeUserId && account.isLoggedIn && account.isVaultUnlocked
            }
            .map { it.second },
    )
}
