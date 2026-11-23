package com.x8bit.bitwarden.data.autofill.manager

import com.bitwarden.ui.platform.resource.BitwardenString
import com.bitwarden.ui.util.asText
import com.bitwarden.vault.CipherView
import com.x8bit.bitwarden.data.auth.repository.AuthRepository
import com.x8bit.bitwarden.data.platform.manager.clipboard.BitwardenClipboardManager
import com.x8bit.bitwarden.data.platform.repository.SettingsRepository
import com.x8bit.bitwarden.data.vault.repository.VaultRepository
import com.x8bit.bitwarden.data.vault.repository.model.GenerateTotpResult
import com.x8bit.bitwarden.multiwarden.MultiAccountVault
import java.time.Clock

/**
 * Default implementation of the [AutofillTotpManager].
 */
class AutofillTotpManagerImpl(
    private val clock: Clock,
    private val clipboardManager: BitwardenClipboardManager,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val vaultRepository: VaultRepository,
    // Multiwarden: items can come from any unlocked account.
    private val multiAccountVault: MultiAccountVault,
) : AutofillTotpManager {
    override suspend fun tryCopyTotpToClipboard(cipherView: CipherView) {
        if (settingsRepository.isAutoCopyTotpDisabled) return
        // Multiwarden: premium and the TOTP code come from the account that owns the item.
        val otherOwner = cipherView.id?.let { multiAccountVault.findOtherOwner(cipherId = it) }
        val isPremium = otherOwner?.isPremium
            ?: (authRepository.userStateFlow.value?.activeAccount?.isPremium == true)
        if (!isPremium && !cipherView.organizationUseTotp) return
        cipherView.login?.totp ?: return
        val cipherId = cipherView.id ?: return

        val totpResult = otherOwner
            ?.let {
                multiAccountVault.generateTotp(
                    userId = it.userId,
                    cipherId = cipherId,
                    time = clock.instant(),
                )
            }
            ?: vaultRepository.generateTotp(
                time = clock.instant(),
                cipherId = cipherId,
            )

        if (totpResult is GenerateTotpResult.Success) {
            clipboardManager.setText(
                text = totpResult.code,
                toastDescriptorOverride = BitwardenString.verification_code_totp.asText(),
            )
        }
    }
}
