package com.x8bit.bitwarden.multiwarden

import app.cash.turbine.test
import com.bitwarden.core.data.manager.dispatcher.FakeDispatcherManager
import com.bitwarden.core.data.repository.model.DataState
import com.bitwarden.data.datasource.disk.base.FakeSharedPreferences
import com.bitwarden.vault.DecryptCipherListResult
import com.x8bit.bitwarden.data.auth.repository.AuthRepository
import com.x8bit.bitwarden.data.auth.repository.model.UserState
import com.x8bit.bitwarden.data.vault.datasource.disk.VaultDiskSource
import com.x8bit.bitwarden.data.vault.datasource.sdk.VaultSdkSource
import com.x8bit.bitwarden.data.vault.datasource.sdk.model.createMockCipherListView
import com.x8bit.bitwarden.data.vault.datasource.sdk.model.createMockDecryptCipherListResult
import com.x8bit.bitwarden.data.vault.datasource.sdk.model.createMockFolderView
import com.x8bit.bitwarden.data.vault.repository.VaultRepository
import com.x8bit.bitwarden.data.vault.repository.model.VaultData
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MultiwardenCombinedVaultTest {
    private val mutableUserStateFlow = MutableStateFlow<UserState?>(
        userState(
            account(userId = ACTIVE_USER_ID, isVaultUnlocked = true),
            account(userId = OTHER_USER_ID, isVaultUnlocked = true),
        ),
    )
    private val authRepository: AuthRepository = mockk {
        every { userStateFlow } returns mutableUserStateFlow
    }
    private val mutableVaultDataStateFlow =
        MutableStateFlow<DataState<VaultData>>(DataState.Loaded(ACTIVE_VAULT_DATA))
    private val vaultRepository: VaultRepository = mockk {
        every { vaultDataStateFlow } returns mutableVaultDataStateFlow
    }
    private val vaultDiskSource: VaultDiskSource = mockk {
        every { getCiphersFlow(userId = OTHER_USER_ID) } returns flowOf(emptyList())
        every { getFoldersFlow(userId = OTHER_USER_ID) } returns flowOf(emptyList())
    }
    private val vaultSdkSource: VaultSdkSource = mockk {
        coEvery {
            decryptCipherListWithFailures(userId = OTHER_USER_ID, cipherList = emptyList())
        } returns Result.success(
            DecryptCipherListResult(successes = listOf(OTHER_CIPHER), failures = emptyList()),
        )
        coEvery {
            decryptFolderList(userId = OTHER_USER_ID, folderList = emptyList())
        } returns Result.success(listOf(OTHER_FOLDER))
    }
    private val sharedPreferences = FakeSharedPreferences()

    private fun createCombinedVault(): MultiwardenCombinedVault = MultiwardenCombinedVaultImpl(
        authRepository = authRepository,
        vaultRepository = vaultRepository,
        vaultDiskSource = vaultDiskSource,
        vaultSdkSource = vaultSdkSource,
        sharedPreferences = sharedPreferences,
        dispatcherManager = FakeDispatcherManager(),
    )

    @Test
    fun `combined vault should be available only while another account is unlocked`() {
        val combinedVault = createCombinedVault()
        assertTrue(combinedVault.isCombinedModeAvailableFlow.value)

        mutableUserStateFlow.value = userState(
            account(userId = ACTIVE_USER_ID, isVaultUnlocked = true),
            account(userId = OTHER_USER_ID, isVaultUnlocked = false),
        )

        assertFalse(combinedVault.isCombinedModeAvailableFlow.value)
    }

    @Test
    fun `setCombinedModeOn should be remembered`() {
        val combinedVault = createCombinedVault()
        assertFalse(combinedVault.isCombinedModeOnFlow.value)

        combinedVault.setCombinedModeOn(isOn = true)

        assertTrue(combinedVault.isCombinedModeOnFlow.value)
        assertTrue(createCombinedVault().isCombinedModeOnFlow.value)
    }

    @Test
    fun `vaultDataStateFlow should be the active account's data while the vault is not combined`() =
        runTest {
            val combinedVault = createCombinedVault()

            combinedVault.vaultDataStateFlow.test {
                assertEquals(DataState.Loaded(ACTIVE_VAULT_DATA), expectMostRecentItem())
            }
            assertNull(combinedVault.otherOwnerOf(cipherId = OTHER_CIPHER_ID))
        }

    @Test
    fun `vaultDataStateFlow should add the other accounts' items and folders, labelled`() =
        runTest {
            val combinedVault = createCombinedVault()
            combinedVault.setCombinedModeOn(isOn = true)

            combinedVault.vaultDataStateFlow.test {
                val vaultData = (expectMostRecentItem() as DataState.Loaded).data
                assertEquals(
                    listOf(
                        "mockSubtitle-1 · $ACTIVE_USER_ID",
                        "mockSubtitle-2 · $OTHER_USER_ID",
                    ),
                    vaultData.decryptCipherListResult.successes.map { it.subtitle },
                )
                assertEquals(
                    listOf("$ACTIVE_USER_ID · mockName-1", "$OTHER_USER_ID · mockName-2"),
                    vaultData.folderViewList.map { it.name },
                )
                assertEquals(
                    OTHER_USER_ID,
                    combinedVault.otherOwnerOf(cipherId = OTHER_CIPHER_ID)?.userId,
                )
                assertNull(combinedVault.otherOwnerOf(cipherId = ACTIVE_CIPHER_ID))
            }
        }

    @Test
    fun `withOtherVaults should keep an item both accounts can see once, as the active one's`() {
        val shared = createMockCipherListView(number = 1)

        val vaultData = ACTIVE_VAULT_DATA.withOtherVaults(
            active = ACTIVE_ACCOUNT,
            others = listOf(
                OtherVault(
                    account = OTHER_ACCOUNT,
                    ciphers = listOf(shared, OTHER_CIPHER),
                    folders = emptyList(),
                ),
            ),
        )

        assertEquals(
            listOf(ACTIVE_CIPHER_ID, OTHER_CIPHER_ID),
            vaultData.decryptCipherListResult.successes.map { it.id },
        )
    }

    @Test
    fun `label should be the part of the email address before the at sign`() {
        assertEquals(OTHER_USER_ID, OTHER_ACCOUNT.label)
    }
}

private const val ACTIVE_USER_ID = "active"
private const val OTHER_USER_ID = "other"
private const val ACTIVE_CIPHER_ID = "mockId-1"
private const val OTHER_CIPHER_ID = "mockId-2"
private val ACTIVE_ACCOUNT = MultiwardenAccount(
    userId = ACTIVE_USER_ID,
    email = "$ACTIVE_USER_ID@example.com",
    isPremium = false,
)
private val OTHER_ACCOUNT = MultiwardenAccount(
    userId = OTHER_USER_ID,
    email = "$OTHER_USER_ID@example.com",
    isPremium = false,
)
private val OTHER_CIPHER = createMockCipherListView(number = 2)
private val OTHER_FOLDER = createMockFolderView(number = 2)
private val ACTIVE_VAULT_DATA = VaultData(
    decryptCipherListResult = createMockDecryptCipherListResult(number = 1),
    collectionViewList = emptyList(),
    folderViewList = listOf(createMockFolderView(number = 1)),
    sendViewList = emptyList(),
)

private fun userState(vararg accounts: UserState.Account): UserState = mockk {
    every { activeUserId } returns ACTIVE_USER_ID
    every { this@mockk.accounts } returns accounts.toList()
}

private fun account(
    userId: String,
    isVaultUnlocked: Boolean,
): UserState.Account = mockk {
    every { this@mockk.userId } returns userId
    every { email } returns "$userId@example.com"
    every { this@mockk.isVaultUnlocked } returns isVaultUnlocked
    every { isLoggedIn } returns true
    every { isPremium } returns false
}
