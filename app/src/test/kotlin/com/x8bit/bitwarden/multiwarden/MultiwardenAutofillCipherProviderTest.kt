package com.x8bit.bitwarden.multiwarden

import com.bitwarden.core.data.repository.model.DataState
import com.bitwarden.vault.CipherListView
import com.bitwarden.vault.CipherListViewType
import com.bitwarden.vault.CipherRepromptType
import com.bitwarden.vault.CipherView
import com.bitwarden.vault.LoginView
import com.x8bit.bitwarden.data.auth.repository.AuthRepository
import com.x8bit.bitwarden.data.autofill.model.AutofillCipher
import com.x8bit.bitwarden.data.autofill.provider.AutofillCipherProvider
import com.x8bit.bitwarden.data.autofill.provider.AutofillCipherProviderImpl
import com.x8bit.bitwarden.data.platform.manager.PolicyManager
import com.x8bit.bitwarden.data.platform.manager.ciphermatching.CipherMatchingManager
import com.x8bit.bitwarden.data.platform.util.subtitle
import com.x8bit.bitwarden.data.vault.manager.model.GetCipherResult
import com.x8bit.bitwarden.data.vault.repository.VaultRepository
import com.x8bit.bitwarden.data.vault.repository.model.VaultUnlockData
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests the Multiwarden behaviour of [AutofillCipherProviderImpl]: autofill items from unlocked
 * accounts other than the active one.
 */
class MultiwardenAutofillCipherProviderTest {
    private val authRepository: AuthRepository = mockk {
        every { activeUserId } returns ACTIVE_USER_ID
    }
    private val cipherMatchingManager: CipherMatchingManager = mockk()
    private val mutableVaultUnlockDataStateFlow =
        MutableStateFlow<List<VaultUnlockData>>(emptyList())
    private val vaultRepository: VaultRepository = mockk {
        every { vaultUnlockDataStateFlow } returns mutableVaultUnlockDataStateFlow
        every { isVaultUnlocked(userId = ACTIVE_USER_ID) } returns false
        every { decryptCipherListResultStateFlow } returns MutableStateFlow(
            DataState.Loading,
        )
    }
    private val policyManager: PolicyManager = mockk()
    private val multiAccountVault: MultiAccountVault = mockk {
        every { otherUnlockedAccounts() } returns listOf(OTHER_ACCOUNT)
        every { accountLabelOrNull(userId = any()) } returns OTHER_EMAIL
    }

    private lateinit var autofillCipherProvider: AutofillCipherProvider

    @BeforeEach
    fun setup() {
        mockkStatic(CipherView::subtitle)
        autofillCipherProvider = AutofillCipherProviderImpl(
            authRepository = authRepository,
            cipherMatchingManager = cipherMatchingManager,
            vaultRepository = vaultRepository,
            policyManager = policyManager,
            multiAccountVault = multiAccountVault,
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(CipherView::subtitle)
    }

    @Test
    fun `isVaultLocked should be false while another account is unlocked`() = runTest {
        assertFalse(autofillCipherProvider.isVaultLocked())
    }

    @Test
    fun `isVaultLocked should be true when no account is unlocked`() = runTest {
        every { multiAccountVault.otherUnlockedAccounts() } returns emptyList()

        assertTrue(autofillCipherProvider.isVaultLocked())
    }

    @Suppress("MaxLineLength")
    @Test
    fun `getLoginAutofillCiphers should return labelled logins of another unlocked account while the active account is locked`() =
        runTest {
            val loginListView: CipherListView = mockk {
                every { archivedDate } returns null
                every { deletedDate } returns null
                every { id } returns CIPHER_ID
                every { reprompt } returns CipherRepromptType.NONE
                every { type } returns CipherListViewType.Login(v1 = mockk())
            }
            val loginView: LoginView = mockk {
                every { password } returns PASSWORD
                every { username } returns USERNAME
                every { totp } returns null
            }
            val cipherView: CipherView = mockk {
                every { id } returns CIPHER_ID
                every { name } returns NAME
                every { login } returns loginView
                every { subtitle } returns USERNAME
            }
            coEvery {
                multiAccountVault.getCipherListViews(userId = OTHER_USER_ID)
            } returns listOf(loginListView)
            coEvery {
                cipherMatchingManager.filterCiphersForMatches(
                    cipherListViews = listOf(loginListView),
                    matchUri = URI,
                )
            } returns listOf(loginListView)
            coEvery {
                multiAccountVault.getCipher(userId = OTHER_USER_ID, cipherId = CIPHER_ID)
            } returns GetCipherResult.Success(cipherView = cipherView)

            val result = autofillCipherProvider.getLoginAutofillCiphers(uri = URI)

            assertEquals(
                listOf(
                    AutofillCipher.Login(
                        cipherId = CIPHER_ID,
                        isTotpEnabled = false,
                        name = NAME,
                        password = PASSWORD,
                        subtitle = "$USERNAME · $OTHER_EMAIL",
                        username = USERNAME,
                        website = URI,
                    ),
                ),
                result,
            )
            coVerify(exactly = 0) { vaultRepository.getCipher(cipherId = any()) }
        }
}

private const val ACTIVE_USER_ID = "active"
private const val OTHER_USER_ID = "other"
private const val OTHER_EMAIL = "other@example.com"
private const val CIPHER_ID = "cipherId"
private const val NAME = "Example"
private const val USERNAME = "user"
private const val PASSWORD = "password"
private const val URI = "https://example.com"
private val OTHER_ACCOUNT = MultiwardenAccount(
    userId = OTHER_USER_ID,
    email = OTHER_EMAIL,
    isPremium = false,
)
