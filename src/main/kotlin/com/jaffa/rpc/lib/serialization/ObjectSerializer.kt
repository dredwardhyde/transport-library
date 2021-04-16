package com.jaffa.rpc.lib.serialization

interface ObjectSerializer {

    fun serialize(obj: Any?): ByteArray?

    fun serializeWithClass(obj: Any?): ByteArray?

    fun deserializeWithClass(serialized: ByteArray?): Any?

    fun <T> deserialize(serialized: ByteArray?, clazz: Class<T>?): T?

}