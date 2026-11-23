package com.x8bit.bitwarden.multiwarden.di

import com.x8bit.bitwarden.data.auth.repository.AuthRepository
import com.x8bit.bitwarden.data.vault.datasource.disk.VaultDiskSource
import com.x8bit.bitwarden.data.vault.datasource.sdk.VaultSdkSource
import com.x8bit.bitwarden.multiwarden.MultiAccountVault
import com.x8bit.bitwarden.multiwarden.MultiAccountVaultImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Multiwarden: provides Multiwarden's own dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object MultiwardenModule {

    @Provides
    @Singleton
    fun providesMultiAccountVault(
        authRepository: AuthRepository,
        vaultDiskSource: VaultDiskSource,
        vaultSdkSource: VaultSdkSource,
    ): MultiAccountVault =
        MultiAccountVaultImpl(
            authRepository = authRepository,
            vaultDiskSource = vaultDiskSource,
            vaultSdkSource = vaultSdkSource,
        )
}
