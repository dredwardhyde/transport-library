package com.jaffa.rpc.lib.serialization

import org.slf4j.LoggerFactory
import java.io.*

class JavaSerializer : ObjectSerializer {

    private val log = LoggerFactory.getLogger(JavaSerializer::class.java)

    override fun serialize(obj: Any?): ByteArray? {
        try {
            ByteArrayOutputStream().use { bos ->
                val out: ObjectOutputStream
                out = ObjectOutputStream(bos)
                out.writeObject(obj)
                out.flush()
                return bos.toByteArray()
            }
        } catch (ioException: IOException) {
            log.error("Exception while object Java serialization", ioException)
        }
        return null
    }

    override fun serializeWithClass(obj: Any?): ByteArray? {
        return serialize(obj)
    }

    override fun deserializeWithClass(serialized: ByteArray?): Any? {
        val bis = ByteArrayInputStream(serialized)
        try {
            ObjectInputStream(bis).use { `in` -> return `in`.readObject() }
        } catch (exception: IOException) {
            log.error("Exception while object Java deserialization", exception)
        } catch (exception: ClassNotFoundException) {
            log.error("Exception while object Java deserialization", exception)
        }
        return null
    }

    override fun <T> deserialize(serialized: ByteArray?, clazz: Class<T>?): T? {
        val bis = ByteArrayInputStream(serialized)
        try {
            ObjectInputStream(bis).use { `in` -> return `in`.readObject() as T }
        } catch (exception: IOException) {
            log.error("Exception while object Java deserialization", exception)
        } catch (exception: ClassNotFoundException) {
            log.error("Exception while object Java deserialization", exception)
        } catch (exception: ClassCastException) {
            log.error("Exception while object Java deserialization", exception)
        }
        return null
    }
}