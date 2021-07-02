package com.jaffa.rpc.lib.serialization

import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class JavaSerializer : ObjectSerializer {

    private val log = LoggerFactory.getLogger(JavaSerializer::class.java)

    companion object {
        const val ERROR_DESERIALIZATION_MESSAGE = "Exception while object Java deserialization"
        const val ERROR_SERIALIZATION_MESSAGE = "Exception while object Java serialization"
    }

    override fun serialize(obj: Any?): ByteArray? {
        try {
            ByteArrayOutputStream().use { bos ->
                val out = ObjectOutputStream(bos)
                out.writeObject(obj)
                out.flush()
                return bos.toByteArray()
            }
        } catch (exception: Exception) {
            throw JaffaRpcExecutionException(exception)
        }
    }

    override fun serializeWithClass(obj: Any?): ByteArray? {
        return serialize(obj)
    }

    override fun deserializeWithClass(serialized: ByteArray?): Any? {
        val bis = ByteArrayInputStream(serialized)
        try {
            ObjectInputStream(bis).use { `in` -> return `in`.readObject() }
        } catch (exception: Exception) {
            throw JaffaRpcExecutionException(exception)
        }
    }

    override fun <T> deserialize(serialized: ByteArray?, clazz: Class<T>?): T? {
        val bis = ByteArrayInputStream(serialized)
        try {
            ObjectInputStream(bis).use { `in` -> return `in`.readObject() as T }
        } catch (exception: Exception) {
            throw JaffaRpcExecutionException(exception)
        }
    }
}