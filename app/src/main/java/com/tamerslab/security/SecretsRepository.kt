package com.tamerslab.security

import com.tamerslab.security.proto.Secrets
import kotlinx.coroutines.flow.Flow

interface SecretsRepository {
    val secretsFlow: Flow<Secrets>

    suspend fun getSecrets(): Secrets
    suspend fun updateSecrets(secrets: Secrets)
}