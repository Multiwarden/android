package com.x8bit.bitwarden.multiwarden

import com.bitwarden.vault.CipherListView
import com.x8bit.bitwarden.data.auth.repository.AuthRepository
import com.x8bit.bitwarden.data.auth.repository.model.UserState
import com.x8bit.bitwarden.data.vault.datasource.disk.VaultDiskSource
import com.x8bit.bitwarden.data.vault.datasource.sdk.VaultSdkSource
import com.x8bit.bitwarden.data.vault.manager.model.GetCipherResult
import com.x8bit.bitwarden.data.vault.repository.model.GenerateTotpResult
import com.x8bit.bitwarden.data.vault.repository.util.toEncryptedSdkCipher
import java.time.Instant

/**
 * Multiwarden: the default [MultiAccountVault]. Reads another account's encrypted vault from disk
 * and decrypts it with that account's own SDK client, which stays initialised for as long as the
 * account is unlocked.
 */
class MultiAccountVaultImpl(
    private val authRepository: AuthRepository,
    private val vaultDiskSource: VaultDiskSource,
    private val vaultSdkSource: VaultSdkSource,
) : MultiAccountVault {
    override fun otherUnlockedAccounts(): List<MultiwardenAccount> {
        val userState = authRepository.userStateFlow.value ?: return emptyList()
        return userState
            .accounts
            .filter { it.userId != userState.activeUserId && it.isLoggedIn && it.isVaultUnlocked }
            .map { it.toMultiwardenAccount() }
    }

    override fun accountLabelOrNull(userId: String): String? {
        if (otherUnlockedAccounts().isEmpty()) return null
        return authRepository
            .userStateFlow
            .value
            ?.accounts
            ?.firstOrNull { it.userId == userId }
            ?.email
    }

    override suspend fun getCipherListViews(userId: String): List<CipherListView>? {
        if (!isUnlocked(userId = userId)) return null
        val ciphers = vaultDiskSource
            .getCiphers(userId = userId)
            .map { it.toEncryptedSdkCipher() }
        return vaultSdkSource
            .decryptCipherListWithFailures(userId = userId, cipherList = ciphers)
            .getOrNull()
            ?.successes
    }

    override suspend fun getCipher(userId: String, cipherId: String): GetCipherResult {
        if (!isUnlocked(userId = userId)) return GetCipherResult.CipherNotFound
        val cipher = vaultDiskSource.getCipher(userId = userId, cipherId = cipherId)
            ?: return GetCipherResult.CipherNotFound
        return vaultSdkSource
            .decryptCipher(userId = userId, cipher = cipher.toEncryptedSdkCipher())
            .fold(
                onSuccess = { GetCipherResult.Success(cipherView = it) },
                onFailure = { GetCipherResult.Failure(error = it) },
            )
    }

    override suspend fun findOtherOwner(cipherId: String): MultiwardenAccount? =
        otherUnlockedAccounts().firstOrNull { account ->
            vaultDiskSource.getCipher(userId = account.userId, cipherId = cipherId) != null
        }

    override suspend fun generateTotp(
        userId: String,
        cipherId: String,
        time: Instant,
    ): GenerateTotpResult {
        val cipherListView = getCipherListViews(userId = userId)
            ?.firstOrNull { it.id == cipherId }
            ?: return GenerateTotpResult.Error(error = IllegalArgumentException(cipherId))
        return vaultSdkSource
            .generateTotpForCipherListView(
                userId = userId,
                cipherListView = cipherListView,
                time = time,
            )
            .fold(
                onSuccess = {
                    GenerateTotpResult.Success(code = it.code, periodSeconds = it.period.toInt())
                },
                onFailure = { GenerateTotpResult.Error(error = it) },
            )
    }

    private fun isUnlocked(userId: String): Boolean =
        otherUnlockedAccounts().any { it.userId == userId }

    private fun UserState.Account.toMultiwardenAccount(): MultiwardenAccount =
        MultiwardenAccount(userId = userId, email = email, isPremium = isPremium)
}
