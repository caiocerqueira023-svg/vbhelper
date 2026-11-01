package com.tamerslab.security

import com.tamerslab.security.proto.Secrets
import java.io.InputStream

fun interface SecretsImporter {
    fun importSecrets(inputStream: InputStream): Secrets
}