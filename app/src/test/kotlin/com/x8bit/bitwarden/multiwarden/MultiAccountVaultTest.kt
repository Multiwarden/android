package com.x8bit.bitwarden.multiwarden

import com.bitwarden.core.data.util.asFailure
import com.bitwarden.core.data.util.asSuccess
import com.bitwarden.network.model.SyncResponseJson
import com.bitwarden.vault.Cipher
import com.bitwarden.vault.CipherListView
import com.bitwarden.vault.CipherView
import com.bitwarden.vault.DecryptCipherListResult
import com.x8bit.bitwarden.data.auth.repository.AuthRepository
import com.x8bit.bitwarden.data.auth.repository.model.UserState
import com.x8bit.bitwarden.data.vault.datasource.disk.VaultDiskSource
import com.x8bit.bitwarden.data.vault.datasource.sdk.VaultSdkSource
import com.x8bit.bitwarden.data.vault.manager.model.GetCipherResult
import com.x8bit.bitwarden.data.vault.repository.model.GenerateTotpResult
import com.x8bit.bitwarden.data.vault.repository.util.toEncryptedSdkCipher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class MultiAccountVaultTest {
    private val mutableUserStateFlow = MutableStateFlow<UserState?>(null)
    private val authRepository: AuthRepository = mockk {
        every { userStateFlow } returns mutableUserStateFlow
    }
    private val vaultDiskSource: VaultDiskSource = mockk()
    private val vaultSdkSource: VaultSdkSource = mockk()

    private val multiAccountVault: MultiAccountVault = MultiAccountVaultImpl(
        authRepository = authRepository,
        vaultDiskSource = vaultDiskSource,
        vaultSdkSource = vaultSdkSource,
    )

    @BeforeEach
    fun setup() {
        mockkStatic(SyncResponseJson.Cipher::toEncryptedSdkCipher)
        every { DISK_CIPHER.toEncryptedSdkCipher() } returns SDK_CIPHER
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(SyncResponseJson.Cipher::toEncryptedSdkCipher)
    }

    @Test
    fun `otherUnlockedAccounts without a user state should be empty`() {
        assertEquals(emptyList<MultiwardenAccount>(), multiAccountVault.otherUnlockedAccounts())
    }

    @Suppress("MaxLineLength")
    @Test
    fun `otherUnlockedAccounts should list only unlocked, logged in accounts that are not active`() {
        mutableUserStateFlow.value = userState(
            account(userId = ACTIVE_USER_ID, isVaultUnlocked = true),
            account(userId = OTHER_USER_ID, isVaultUnlocked = true, isPremium = true),
            account(userId = LOCKED_USER_ID, isVaultUnlocked = false),
            account(userId = LOGGED_OUT_USER_ID, isVaultUnlocked = true, isLoggedIn = false),
        )

        assertEquals(listOf(OTHER_ACCOUNT), multiAccountVault.otherUnlockedAccounts())
    }

    @Test
    fun `accountLabelOrNull should be null while no other account is unlocked`() {
        mutableUserStateFlow.value = userState(
            account(userId = ACTIVE_USER_ID, isVaultUnlocked = true),
            account(userId = LOCKED_USER_ID, isVaultUnlocked = false),
        )

        assertNull(multiAccountVault.accountLabelOrNull(userId = ACTIVE_USER_ID))
    }

    @Test
    fun `accountLabelOrNull should be the account email while another account is unlocked`() {
        mutableUserStateFlow.value = userState(
            account(userId = ACTIVE_USER_ID, isVaultUnlocked = true),
            account(userId = OTHER_USER_ID, isVaultUnlocked = true),
        )

        assertEquals(
            "$ACTIVE_USER_ID@example.com",
            multiAccountVault.accountLabelOrNull(userId = ACTIVE_USER_ID),
        )
        assertEquals(OTHER_EMAIL, multiAccountVault.accountLabelOrNull(userId = OTHER_USER_ID))
    }

    @Test
    fun `getCipherListViews for a locked account should be null without reading its vault`() =
        runTest {
            mutableUserStateFlow.value = userState(
                account(userId = ACTIVE_USER_ID, isVaultUnlocked = true),
                account(userId = LOCKED_USER_ID, isVaultUnlocked = false),
            )

            assertNull(multiAccountVault.getCipherListViews(userId = LOCKED_USER_ID))
            coVerify(exactly = 0) { vaultDiskSource.getCiphers(userId = any()) }
        }

    @Test
    fun `getCipherListViews for an unlocked account should decrypt its vault`() = runTest {
        unlockOtherAccount()
        val cipherListView: CipherListView = mockk()
        coEvery { vaultDiskSource.getCiphers(userId = OTHER_USER_ID) } returns listOf(DISK_CIPHER)
        coEvery {
            vaultSdkSource.decryptCipherListWithFailures(
                userId = OTHER_USER_ID,
                cipherList = listOf(SDK_CIPHER),
            )
        } returns DecryptCipherListResult(
            successes = listOf(cipherListView),
            failures = emptyList(),
        ).asSuccess()

        assertEquals(
            listOf(cipherListView),
            multiAccountVault.getCipherListViews(userId = OTHER_USER_ID),
        )
    }

    @Test
    fun `getCipher should decrypt the cipher with the owning account's keys`() = runTest {
        unlockOtherAccount()
        val cipherView: CipherView = mockk()
        coEvery {
            vaultDiskSource.getCipher(userId = OTHER_USER_ID, cipherId = CIPHER_ID)
        } returns DISK_CIPHER
        coEvery {
            vaultSdkSource.decryptCipher(userId = OTHER_USER_ID, cipher = SDK_CIPHER)
        } returns cipherView.asSuccess()

        assertEquals(
            GetCipherResult.Success(cipherView = cipherView),
            multiAccountVault.getCipher(userId = OTHER_USER_ID, cipherId = CIPHER_ID),
        )
    }

    @Test
    fun `getCipher should report a decryption failure`() = runTest {
        unlockOtherAccount()
        val error = IllegalStateException("decryption failed")
        coEvery {
            vaultDiskSource.getCipher(userId = OTHER_USER_ID, cipherId = CIPHER_ID)
        } returns DISK_CIPHER
        coEvery {
            vaultSdkSource.decryptCipher(userId = OTHER_USER_ID, cipher = SDK_CIPHER)
        } returns error.asFailure()

        assertEquals(
            GetCipherResult.Failure(error = error),
            multiAccountVault.getCipher(userId = OTHER_USER_ID, cipherId = CIPHER_ID),
        )
    }

    @Test
    fun `getCipher for a locked account should not find the cipher`() = runTest {
        mutableUserStateFlow.value = userState(
            account(userId = ACTIVE_USER_ID, isVaultUnlocked = true),
            account(userId = LOCKED_USER_ID, isVaultUnlocked = false),
        )

        assertEquals(
            GetCipherResult.CipherNotFound,
            multiAccountVault.getCipher(userId = LOCKED_USER_ID, cipherId = CIPHER_ID),
        )
    }

    @Test
    fun `findOtherOwner should return the unlocked account that has the cipher`() = runTest {
        unlockOtherAccount()
        coEvery {
            vaultDiskSource.getCipher(userId = OTHER_USER_ID, cipherId = CIPHER_ID)
        } returns DISK_CIPHER

        assertEquals(OTHER_ACCOUNT, multiAccountVault.findOtherOwner(cipherId = CIPHER_ID))
    }

    @Test
    fun `findOtherOwner should be null when no other unlocked account has the cipher`() =
        runTest {
            unlockOtherAccount()
            coEvery {
                vaultDiskSource.getCipher(userId = OTHER_USER_ID, cipherId = CIPHER_ID)
            } returns null

            assertNull(multiAccountVault.findOtherOwner(cipherId = CIPHER_ID))
        }

    @Test
    fun `generateTotp should fail when the account has no such cipher`() = runTest {
        unlockOtherAccount()
        coEvery { vaultDiskSource.getCiphers(userId = OTHER_USER_ID) } returns emptyList()
        coEvery {
            vaultSdkSource.decryptCipherListWithFailures(
                userId = OTHER_USER_ID,
                cipherList = emptyList(),
            )
        } returns DecryptCipherListResult(successes = emptyList(), failures = emptyList())
            .asSuccess()

        val result = multiAccountVault.generateTotp(
            userId = OTHER_USER_ID,
            cipherId = CIPHER_ID,
            time = Instant.EPOCH,
        )

        assertTrue(result is GenerateTotpResult.Error)
    }

    private fun unlockOtherAccount() {
        mutableUserStateFlow.value = userState(
            account(userId = ACTIVE_USER_ID, isVaultUnlocked = true),
            account(userId = OTHER_USER_ID, isVaultUnlocked = true, isPremium = true),
        )
    }
}

private const val ACTIVE_USER_ID = "active"
private const val OTHER_USER_ID = "other"
private const val LOCKED_USER_ID = "locked"
private const val LOGGED_OUT_USER_ID = "loggedOut"
private const val OTHER_EMAIL = "$OTHER_USER_ID@example.com"
private const val CIPHER_ID = "cipherId"
private val DISK_CIPHER: SyncResponseJson.Cipher = mockk()
private val SDK_CIPHER: Cipher = mockk()
private val OTHER_ACCOUNT = MultiwardenAccount(
    userId = OTHER_USER_ID,
    email = OTHER_EMAIL,
    isPremium = true,
)

private fun userState(vararg accounts: UserState.Account): UserState = mockk {
    every { activeUserId } returns ACTIVE_USER_ID
    every { this@mockk.accounts } returns accounts.toList()
}

private fun account(
    userId: String,
    isVaultUnlocked: Boolean,
    isLoggedIn: Boolean = true,
    isPremium: Boolean = false,
): UserState.Account = mockk {
    every { this@mockk.userId } returns userId
    every { email } returns "$userId@example.com"
    every { this@mockk.isVaultUnlocked } returns isVaultUnlocked
    every { this@mockk.isLoggedIn } returns isLoggedIn
    every { this@mockk.isPremium } returns isPremium
}
