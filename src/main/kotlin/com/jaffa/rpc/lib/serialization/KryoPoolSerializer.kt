package com.jaffa.rpc.lib.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.util.Pool
import java.io.ByteArrayOutputStream

class KryoPoolSerializer : ObjectSerializer {
    private val pool: Pool<Kryo>
    override fun serialize(obj: Any?): ByteArray? {
        val baos = ByteArrayOutputStream()
        val output = Output(baos)
        val kryo = pool.obtain()
        kryo.writeObject(output, obj)
        output.flush()
        output.close()
        pool.free(kryo)
        return baos.toByteArray()
    }

    override fun serializeWithClass(obj: Any?): ByteArray? {
        val baos = ByteArrayOutputStream()
        val output = Output(baos)
        val kryo = pool.obtain()
        kryo.writeClassAndObject(output, obj)
        output.flush()
        output.close()
        pool.free(kryo)
        return baos.toByteArray()
    }

    override fun deserializeWithClass(serialized: ByteArray?): Any? {
        val obj: Any?
        val kryo = pool.obtain()
        val input = Input(serialized)
        obj = kryo.readClassAndObject(input)
        pool.free(kryo)
        return obj
    }

    override fun <T> deserialize(serialized: ByteArray?, clazz: Class<T>?): T? {
        val obj: T?
        val kryo = pool.obtain()
        val input = Input(serialized)
        obj = kryo.readObject(input, clazz)
        pool.free(kryo)
        return obj
    }

    init {
        pool = object : Pool<Kryo>(true, true, 100) {
            override fun create(): Kryo {
                return Kryo().also { it.register(Void.TYPE) }.also { it.isRegistrationRequired = true }
                    .also { it.references = true }
            }
        }
    }
}