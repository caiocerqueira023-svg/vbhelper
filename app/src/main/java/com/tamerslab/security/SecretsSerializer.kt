package com.tamerslab.security

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.tamerslab.security.proto.Secrets
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object SecretsSerializer: Serializer<Secrets> {
    override val defaultValue = Secrets.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): Secrets {
        try {
            return Secrets.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(t: Secrets, output: OutputStream) {
        t.writeTo(output)
    }
}