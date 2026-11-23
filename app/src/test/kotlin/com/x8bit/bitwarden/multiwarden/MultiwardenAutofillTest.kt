package com.x8bit.bitwarden.multiwarden

import com.x8bit.bitwarden.data.autofill.model.AutofillCipher
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MultiwardenAutofillTest {
    private val multiAccountVault: MultiAccountVault = mockk()

    @Test
    fun `withAccountLabel without a label should return the item unchanged`() {
        assertEquals(LOGIN, LOGIN.withAccountLabel(label = null))
    }

    @Test
    fun `withAccountLabel should add the label after the subtitle`() {
        assertEquals(
            LOGIN.copy(subtitle = "user · work@example.com"),
            LOGIN.withAccountLabel(label = "work@example.com"),
        )
    }

    @Test
    fun `withAccountLabel with an empty subtitle should use the label alone`() {
        assertEquals(
            CARD.copy(subtitle = "work@example.com"),
            CARD.withAccountLabel(label = "work@example.com"),
        )
    }

    @Test
    fun `autofillCiphersOfAllAccounts should load the active account first, then the others`() =
        runTest {
            every { multiAccountVault.otherUnlockedAccounts() } returns listOf(OTHER_ACCOUNT)
            every { multiAccountVault.accountLabelOrNull(userId = "active") } returns ACTIVE_EMAIL
            every { multiAccountVault.accountLabelOrNull(userId = "other") } returns OTHER_EMAIL

            val result = multiAccountVault.autofillCiphersOfAllAccounts(activeUserId = "active") {
                listOf(LOGIN.copy(cipherId = it))
            }

            assertEquals(
                listOf(
                    LOGIN.copy(cipherId = "active", subtitle = "user · $ACTIVE_EMAIL"),
                    LOGIN.copy(cipherId = "other", subtitle = "user · $OTHER_EMAIL"),
                ),
                result,
            )
        }

    @Test
    fun `autofillCiphersOfAllAccounts without an active account should load the others`() =
        runTest {
            every { multiAccountVault.otherUnlockedAccounts() } returns listOf(OTHER_ACCOUNT)
            every { multiAccountVault.accountLabelOrNull(userId = "other") } returns null

            val result = multiAccountVault.autofillCiphersOfAllAccounts(activeUserId = null) {
                listOf(LOGIN.copy(cipherId = it))
            }

            assertEquals(listOf(LOGIN.copy(cipherId = "other")), result)
        }
}

private const val ACTIVE_EMAIL = "me@example.com"
private const val OTHER_EMAIL = "other@example.com"
private val OTHER_ACCOUNT = MultiwardenAccount(
    userId = "other",
    email = OTHER_EMAIL,
    isPremium = false,
)
private val LOGIN = AutofillCipher.Login(
    cipherId = "login",
    isTotpEnabled = false,
    name = "Example",
    password = "password",
    subtitle = "user",
    username = "user",
    website = "https://example.com",
)
private val CARD = AutofillCipher.Card(
    cipherId = "card",
    name = "Card",
    subtitle = "",
    cardholderName = "",
    code = "",
    expirationMonth = "",
    expirationYear = "",
    number = "",
    brand = "",
)
