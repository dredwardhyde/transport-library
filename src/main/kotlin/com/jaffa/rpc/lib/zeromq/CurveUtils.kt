package com.jaffa.rpc.lib.zeromq

import com.jaffa.rpc.lib.common.OptionConstants
import com.jaffa.rpc.lib.zookeeper.Utils
import org.slf4j.LoggerFactory
import org.zeromq.ZMQ
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

object CurveUtils {

    private val log = LoggerFactory.getLogger(CurveUtils::class.java)

    private val moduleIdWithClientKeys: MutableMap<String, String?> = HashMap()

    var serverPublicKey: String? = null

    var serverSecretKey: String? = null

    @kotlin.jvm.JvmStatic
    fun getPublicKeyFromPath(path: String): String? {
        try {
            val keys = String(Files.readAllBytes(Paths.get(path)))
            return keys.substring(keys.indexOf("public-key = \"") + 14, keys.indexOf("public-key = \"") + 54)
        } catch (ioException: IOException) {
            log.error("Error while getting public Curve key from location $path", ioException)
        }
        return null
    }

    @kotlin.jvm.JvmStatic
    fun makeSocketSecure(socket: ZMQ.Socket) {
        if (System.getProperty(OptionConstants.ZMQ_CURVE_ENABLED, false.toString()).toBoolean()) {
            socket.setZAPDomain("global".toByteArray())
            socket.curveServer = true
            socket.curvePublicKey = serverPublicKey?.toByteArray()
            socket.curveSecretKey = serverSecretKey?.toByteArray()
        }
    }

    fun getClientPublicKey(moduleId: String?): String? {
        val clientPublicKey = moduleIdWithClientKeys[moduleId]
        log.debug("Reading public client key {} for {}", clientPublicKey, moduleId)
        return clientPublicKey
    }

    @kotlin.jvm.JvmStatic
    fun getSecretKeyFromPath(path: String): String? {
        try {
            val keys = String(Files.readAllBytes(Paths.get(path)))
            return keys.substring(keys.indexOf("secret-key = \"") + 14, keys.indexOf("secret-key = \"") + 54)
        } catch (ioException: IOException) {
            log.error("Error while getting secret Curve key from location $path", ioException)
        }
        return null
    }

    fun readClientKeys() {
        for ((key, value) in System.getProperties()) {
            val name = key.toString()
            if (!name.startsWith(OptionConstants.ZMQ_CLIENT_KEY)) continue
            val path = value.toString()
            val moduleId = name.replace(OptionConstants.ZMQ_CLIENT_KEY, "")
            moduleIdWithClientKeys[moduleId] = getPublicKeyFromPath(path)
        }
    }

    @kotlin.jvm.JvmStatic
    fun readServerKeys() {
        val localServerKeys = Utils.getRequiredOption(OptionConstants.ZMQ_SERVER_KEYS)
        serverPublicKey = getPublicKeyFromPath(localServerKeys)
        serverSecretKey = getSecretKeyFromPath(localServerKeys)
    }
}