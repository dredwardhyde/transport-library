package com.jaffa.rpc.lib.zookeeper

import com.github.benmanes.caffeine.cache.Caffeine
import com.jaffa.rpc.lib.common.OptionConstants
import com.jaffa.rpc.lib.entities.Protocol
import com.jaffa.rpc.lib.exception.JaffaRpcNoRouteException
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException
import com.jaffa.rpc.lib.grpc.GrpcRequestSender
import com.jaffa.rpc.lib.http.HttpRequestSender
import com.jaffa.rpc.lib.kafka.KafkaRequestSender
import com.jaffa.rpc.lib.rabbitmq.RabbitMQRequestSender
import com.jaffa.rpc.lib.request.Sender
import com.jaffa.rpc.lib.zeromq.ZeroMqRequestSender
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.tuple.ImmutablePair
import org.apache.commons.lang3.tuple.MutablePair
import org.apache.commons.lang3.tuple.Pair
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.KeeperException
import org.apache.zookeeper.ZooDefs
import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.data.Stat
import org.json.simple.JSONArray
import org.json.simple.parser.JSONParser
import org.json.simple.parser.ParseException
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.net.UnknownHostException
import java.util.*
import java.util.concurrent.TimeUnit

object Utils {

    private val log = LoggerFactory.getLogger(Utils::class.java)

    val services: MutableList<String> = ArrayList()

    val senders: MutableMap<Protocol, Class<out Sender?>> = EnumMap(Protocol::class.java)

    @Volatile
    @JvmStatic
    lateinit var conn: ZooKeeperConnection

    lateinit var zk: ZooKeeper

    @kotlin.jvm.JvmField
    val cache = Caffeine.newBuilder().maximumSize(100).expireAfterWrite(10, TimeUnit.MINUTES)
            .build { k: String? ->
                try {
                    zk.getData(k, true, null)
                } catch (e: Exception) {
                    //No-op
                    null
                }
            }

    @kotlin.jvm.JvmStatic
    fun loadExternalProperties(moduleId: String?) {
        try {
            val path = System.getProperty("jaffa-rpc-config")
            log.info("Loading Jaffa RPC properies from file {} for moduleId {}", path, moduleId)
            if (path != null) {
                val p = Properties()
                val `is`: InputStream = FileInputStream(path)
                p.load(`is`)
                for (name in p.stringPropertyNames()) {
                    if (moduleId != null && name.contains(moduleId)) {
                        val value = p.getProperty(name)
                        if (name.toLowerCase().contains("password")) log.info(
                                "Loading property {} = {}",
                                name,
                                "*************"
                        ) else log.info("Loading property {} = {}", name, value)
                        System.setProperty(name, value)
                    }
                }
            }
        } catch (ioException: IOException) {
            log.error("Unable to read properties from jaffa-rpc-config file", ioException)
        }
    }

    @kotlin.jvm.JvmStatic
    fun connect(url: String) {
        try {
            conn = ZooKeeperConnection()
            zk = conn.connect(url)
            val shutdownHook = ShutdownHook()
            Runtime.getRuntime().addShutdownHook(shutdownHook)
        } catch (e: IOException) {
            log.error("Can not connect to ZooKeeper cluster", e)
            throw JaffaRpcSystemException(e)
        } catch (e: InterruptedException) {
            log.error("Can not connect to ZooKeeper cluster", e)
            throw JaffaRpcSystemException(e)
        }
    }

    @kotlin.jvm.JvmStatic
    fun getRequiredOption(option: String?): String {
        val optionValue = System.getProperty(option)
        return if (StringUtils.isBlank(optionValue)) throw IllegalArgumentException("Property $option  was not set") else optionValue.trim { it <= ' ' }
    }

    fun getServiceInterfaceNameFromClient(clientName: String?): String? {
        return clientName?.replace("Client$".toRegex(), "")
    }

    @kotlin.jvm.JvmStatic
    fun getHostForService(service: String?, moduleId: String?, protocol: Protocol): Pair<String?, String?> {
        val serviceClient = getServiceInterfaceNameFromClient(service)
        return try {
            val host = getHostsForService("/$serviceClient", moduleId, protocol)[0]
            if (protocol == Protocol.HTTP) {
                host.left = httpPrefix + host.left
            }
            host
        } catch (e: ParseException) {
            throw JaffaRpcNoRouteException(serviceClient)
        }
    }

    private val httpPrefix: String
        get() = (if (System.getProperty(OptionConstants.USE_HTTPS, false.toString())
                        .toBoolean()
        ) "https" else "http") + "://"
    val isZkTestMode: Boolean
        get() = System.getProperty(OptionConstants.ZK_TEST_MODE, false.toString()).toBoolean()

    @Throws(ParseException::class)
    private fun getHostsForService(service: String, moduleId: String?, protocol: Protocol): ArrayList<MutablePair<String?, String?>> {
        val zkData = cache[service] ?: throw JaffaRpcNoRouteException(service)
        val jArray = JSONParser().parse(String(zkData)) as JSONArray
        return if (jArray.isEmpty()) throw JaffaRpcNoRouteException(service) else {
            val hosts = ArrayList<MutablePair<String?, String?>>()
            for (json in jArray) {
                val params = (json as String).split("#".toRegex()).toTypedArray()
                if (moduleId != null) {
                    if (moduleId == params[1] && protocol.shortName == params[2]) hosts.add(
                            MutablePair(
                                    params[0],
                                    params[1]
                            )
                    )
                } else {
                    if (protocol.shortName == params[2]) hosts.add(MutablePair(params[0], params[1]))
                }
            }
            if (hosts.isEmpty()) throw JaffaRpcNoRouteException(service, moduleId)
            hosts
        }
    }

    @kotlin.jvm.JvmStatic
    fun getModuleForService(service: String?, protocol: Protocol): String {
        return try {
            val zkData = cache["/$service"] ?: throw JaffaRpcNoRouteException(service, protocol)
            val jArray = JSONParser().parse(String(zkData)) as JSONArray
            if (jArray.isEmpty()) throw JaffaRpcNoRouteException(service) else {
                val hosts = ArrayList<String>()
                for (json in jArray) {
                    val params = (json as String).split("#".toRegex()).toTypedArray()
                    if (protocol.shortName == params[2]) hosts.add(params[1])
                }
                if (hosts.isEmpty()) throw JaffaRpcNoRouteException(service, protocol)
                hosts[0]
            }
        } catch (e: ParseException) {
            log.error("Error while getting avaiable module.id:", e)
            throw JaffaRpcNoRouteException(service, protocol.shortName)
        }
    }

    @kotlin.jvm.JvmStatic
    fun registerService(service: String, protocol: Protocol) {
        try {
            val stat = isZNodeExists("/$service")
            if (stat != null) {
                update("/$service", protocol)
            } else {
                create("/$service", protocol)
            }
            services.add("/$service")
            log.info("Registered service: {}", service)
        } catch (e: Exception) {
            throw JaffaRpcSystemException(e)
        }
    }

    val rpcProtocol: Protocol?
        get() = Protocol.getByName(getRequiredOption(OptionConstants.PROTOCOL))

    @kotlin.jvm.JvmStatic
    val currentSenderClass: Class<out Sender?>
        get() {
            return senders[rpcProtocol] ?: throw JaffaRpcSystemException(JaffaRpcSystemException.NO_PROTOCOL_DEFINED)
        }

    fun getHostAndPort(address: String, delimiter: String): Pair<String, Int> {
        val s = address.split(delimiter.toRegex()).toTypedArray()
        return ImmutablePair(s[0], s[1].toInt())
    }

    @kotlin.jvm.JvmStatic
    val servicePort: Int
        get() {
            val defaultPort = 4242
            return try {
                System.getProperty(
                        OptionConstants.PROTOCOL_OPTION_PREFIX +
                                rpcProtocol?.shortName +
                                OptionConstants.SERVICE_PORT_OPTION_SUFFIX,
                        defaultPort.toString()
                ).toInt()
            } catch (e: NumberFormatException) {
                defaultPort
            }
        }

    @kotlin.jvm.JvmStatic
    val callbackPort: Int
        get() {
            val defaultPort = 4342
            return try {
                System.getProperty(
                        OptionConstants.PROTOCOL_OPTION_PREFIX +
                                rpcProtocol?.shortName +
                                OptionConstants.CALLBACK_PORT_OPTION_SUFFIX,
                        defaultPort.toString()
                ).toInt()
            } catch (e: NumberFormatException) {
                defaultPort
            }
        }

    @Throws(KeeperException::class, InterruptedException::class, UnknownHostException::class)
    private fun create(service: String, protocol: Protocol) {
        val ja = JSONArray()
        ja.add(getServiceBindAddress(protocol))
        zk.create(service, ja.toJSONString().toByteArray(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT)
    }

    @Throws(KeeperException::class, InterruptedException::class)
    private fun isZNodeExists(service: String): Stat? {
        return zk.exists(service, true)
    }

    @Throws(KeeperException::class, InterruptedException::class, ParseException::class, UnknownHostException::class)
    private fun update(service: String, protocol: Protocol) {
        val zkData = zk.getData(service, true, null)
        val jArray = JSONParser().parse(zkData?.let { String(it) }) as JSONArray
        val local = getServiceBindAddress(protocol)
        if (!jArray.contains(local)) {
            jArray.add(local)
            zk.exists(service, true)?.version?.let { zk.setData(service, jArray.toJSONString().toByteArray(), it) }
        }
    }

    @kotlin.jvm.JvmStatic
    @Throws(KeeperException::class, InterruptedException::class, ParseException::class, UnknownHostException::class)
    fun deleteAllRegistrations(service: String?) {
        Protocol.values().forEach { delete(service, it) }
    }

    @kotlin.jvm.JvmStatic
    @Throws(KeeperException::class, InterruptedException::class, ParseException::class, UnknownHostException::class)
    fun delete(service: String?, protocol: Protocol) {
        val zkData = zk.getData(service, true, null)
        val jArray = JSONParser().parse(zkData?.let { String(it) }) as JSONArray
        val local = getServiceBindAddress(protocol)
        if (jArray.contains(local)) {
            jArray.remove(local)
            zk.exists(service, true)?.version?.let { zk.setData(service, jArray.toJSONString().toByteArray(), it) }
        }
        log.info("Service {} for protocol {} was unregistered", service, protocol.fullName)
    }

    @Throws(UnknownHostException::class)
    private fun getServiceBindAddress(protocol: Protocol): String {
        return getLocalHostLANAddress().hostAddress + ":" + servicePort + "#" + OptionConstants.MODULE_ID + "#" + protocol.shortName
    }

    @get:Throws(UnknownHostException::class)
    val zeroMQBindAddress: String
        get() = getLocalHostLANAddress().hostAddress + ":" + servicePort
    val httpBindAddress: InetSocketAddress
        get() = InetSocketAddress(localHost, servicePort)

    @get:Throws(UnknownHostException::class)
    val zeroMQCallbackBindAddress: String
        get() = getLocalHostLANAddress().hostAddress + ":" + callbackPort
    val httpCallbackBindAddress: InetSocketAddress
        get() = InetSocketAddress(localHost, callbackPort)

    @get:Throws(UnknownHostException::class)
    val httpCallbackStringAddress: String
        get() = httpPrefix + getLocalHostLANAddress().hostAddress + ":" + callbackPort
    val localHost: String
        get() = try {
            getLocalHostLANAddress().hostAddress
        } catch (e: UnknownHostException) {
            throw JaffaRpcSystemException(e)
        }

    private fun getLocalHostLANAddress(): InetAddress {
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces()
            while (ifaces.hasMoreElements()) {
                val iface = ifaces.nextElement()
                val inetAddrs = iface.inetAddresses
                while (inetAddrs.hasMoreElements()) {
                    val inetAddr = inetAddrs.nextElement()
                    if (!inetAddr.isLoopbackAddress && inetAddr.isSiteLocalAddress) {
                        return inetAddr
                    }
                }
            }
            return InetAddress.getLocalHost()
        } catch (e: SocketException) {
            throw JaffaRpcSystemException(e)
        }
    }

    init {
        senders[Protocol.ZMQ] = ZeroMqRequestSender::class.java
        senders[Protocol.HTTP] = HttpRequestSender::class.java
        senders[Protocol.KAFKA] = KafkaRequestSender::class.java
        senders[Protocol.RABBIT] = RabbitMQRequestSender::class.java
        senders[Protocol.GRPC] = GrpcRequestSender::class.java
    }
}

internal class ShutdownHook : Thread() {

    private val log = LoggerFactory.getLogger(ShutdownHook::class.java)

    override fun run() {
        try {
            if (!Utils.isZkTestMode) {
                Utils.services.forEach { Utils.deleteAllRegistrations(it) }
            }
            Utils.conn.close()
        } catch (e: Exception) {
            log.error("Error occurred in shutdown hook", e)
        }
    }
}