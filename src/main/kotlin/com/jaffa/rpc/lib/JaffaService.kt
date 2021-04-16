package com.jaffa.rpc.lib

import com.jaffa.rpc.lib.annotations.Api
import com.jaffa.rpc.lib.annotations.ApiClient
import com.jaffa.rpc.lib.annotations.ApiServer
import com.jaffa.rpc.lib.common.FinalizationHelper
import com.jaffa.rpc.lib.common.OptionConstants
import com.jaffa.rpc.lib.common.RequestInvocationHelper
import com.jaffa.rpc.lib.entities.Protocol
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException
import com.jaffa.rpc.lib.grpc.GrpcRequestSender
import com.jaffa.rpc.lib.grpc.receivers.GrpcAsyncAndSyncRequestReceiver
import com.jaffa.rpc.lib.grpc.receivers.GrpcAsyncResponseReceiver
import com.jaffa.rpc.lib.http.receivers.HttpAsyncAndSyncRequestReceiver
import com.jaffa.rpc.lib.http.receivers.HttpAsyncResponseReceiver
import com.jaffa.rpc.lib.kafka.KafkaRequestSender
import com.jaffa.rpc.lib.kafka.receivers.KafkaAsyncRequestReceiver
import com.jaffa.rpc.lib.kafka.receivers.KafkaAsyncResponseReceiver
import com.jaffa.rpc.lib.kafka.receivers.KafkaReceiver
import com.jaffa.rpc.lib.kafka.receivers.KafkaSyncRequestReceiver
import com.jaffa.rpc.lib.rabbitmq.RabbitMQRequestSender
import com.jaffa.rpc.lib.rabbitmq.receivers.RabbitMQAsyncAndSyncRequestReceiver
import com.jaffa.rpc.lib.rabbitmq.receivers.RabbitMQAsyncResponseReceiver
import com.jaffa.rpc.lib.security.TicketProvider
import com.jaffa.rpc.lib.serialization.Serializer
import com.jaffa.rpc.lib.spring.ClientEndpoint
import com.jaffa.rpc.lib.spring.ServerEndpoints
import com.jaffa.rpc.lib.zeromq.CurveUtils
import com.jaffa.rpc.lib.zeromq.ZeroMqRequestSender
import com.jaffa.rpc.lib.zeromq.receivers.ZMQAsyncAndSyncRequestReceiver
import com.jaffa.rpc.lib.zeromq.receivers.ZMQAsyncResponseReceiver
import com.jaffa.rpc.lib.zookeeper.Utils
import com.jaffa.rpc.lib.zookeeper.ZooKeeperConnection
import kafka.admin.RackAwareMode
import kafka.zk.AdminZkClient
import kafka.zk.KafkaZkClient
import kafka.zookeeper.ZooKeeperClient
import org.apache.commons.collections4.map.HashedMap
import org.apache.kafka.common.utils.Time
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.connection.RabbitConnectionFactoryBean
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.zeromq.ZContext
import scala.Option
import java.io.Closeable
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.function.Consumer
import java.util.stream.Collectors
import javax.annotation.PostConstruct

open class JaffaService {

    private val log = LoggerFactory.getLogger(JaffaService::class.java)

    private val kafkaReceivers: MutableList<KafkaReceiver> = ArrayList()

    private val zmqReceivers: MutableList<Closeable> = ArrayList()

    private val receiverThreads: MutableList<Thread> = ArrayList()

    @Autowired
    private val serverEndpoints: ServerEndpoints? = null

    @Autowired
    private val clientEndpoints: List<ClientEndpoint>? = null

    @Autowired
    private val context: ApplicationContext? = null

    @Autowired
    private val moduleId: String? = null

    private fun registerServices() {
        val apiImpls: MutableMap<Class<*>, Class<*>> = HashMap()
        serverEndpoints?.endpoints?.forEach { it ->
            log.info("Server endpoint: {}", it.name)
            apiImpls[it] = it.interfaces[0]
        }
        apiImpls.forEach { entry ->
            RequestInvocationHelper.wrappedServices[entry.value] = context?.getBean(entry.key) as Any
            Utils.rpcProtocol?.let { Utils.registerService(entry.value.name, it) }
        }
        RequestInvocationHelper.context = context
    }

    @Throws(ClassNotFoundException::class)
    private fun prepareServiceRegistration() {
        Utils.connect(Utils.getRequiredOption(OptionConstants.ZOOKEEPER_CONNECTION))
        val protocol = Utils.rpcProtocol
        if (protocol == Protocol.KAFKA) {
            val zooKeeperClient = ZooKeeperClient(
                    Utils.getRequiredOption(OptionConstants.ZOOKEEPER_CONNECTION),
                    200000,
                    15000,
                    10,
                    Time.SYSTEM,
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    null, Option.apply(ZooKeeperConnection.zkConfig)
            )
            zkClient = KafkaZkClient(zooKeeperClient, false, Time.SYSTEM)
            adminZkClient = AdminZkClient(zkClient)
            brokersCount = zkClient?.allBrokersInCluster?.size() ?: 0
            log.info("Kafka brokers: {}", brokersCount)
            serverAsyncTopics = createKafkaTopics("server-async")
            clientAsyncTopics = createKafkaTopics("client-async")
            serverSyncTopics = createKafkaTopics("server-sync")
            clientSyncTopics = createKafkaTopics("client-sync")
        }
        if (protocol == Protocol.RABBIT) {
            val rabbitHost = Utils.getRequiredOption(OptionConstants.RABBIT_HOST)
            val rabbitPort = Utils.getRequiredOption(OptionConstants.RABBIT_PORT).toInt()
            val factory = RabbitConnectionFactoryBean()
            factory.setHost(rabbitHost)
            factory.setPort(rabbitPort)
            factory.setUsername(System.getProperty(OptionConstants.RABBIT_LOGIN, "guest"))
            factory.setPassword(System.getProperty(OptionConstants.RABBIT_PASSWORD, "guest"))
            if (System.getProperty(OptionConstants.RABBIT_USE_SSL, "false").toBoolean()) {
                factory.setUseSSL(true)
                factory.setKeyStore(Utils.getRequiredOption(OptionConstants.RABBIT_SSL_KEYSTORE_LOCATION))
                factory.setKeyStorePassphrase(Utils.getRequiredOption(OptionConstants.RABBIT_SSL_KEYSTORE_PASSWORD))
                factory.setTrustStore(Utils.getRequiredOption(OptionConstants.RABBIT_SSL_TRUSTSTORE_LOCATION))
                factory.setTrustStorePassphrase(Utils.getRequiredOption(OptionConstants.RABBIT_SSL_TRUSTSTORE_PASSWORD))
            }
            connectionFactory = if (Utils.isZkTestMode) {
                CachingConnectionFactory(context?.getBean(com.rabbitmq.client.ConnectionFactory::class.java))
            } else {
                CachingConnectionFactory(factory.rabbitConnectionFactory)
            }
            adminRabbitMQ = RabbitAdmin(connectionFactory)
            adminRabbitMQ?.declareExchange(DirectExchange(RabbitMQRequestSender.EXCHANGE_NAME, true, false))
            if (adminRabbitMQ?.getQueueInfo(RabbitMQRequestSender.SERVER) == null) {
                adminRabbitMQ?.declareQueue(Queue(RabbitMQRequestSender.SERVER))
            }
            if (adminRabbitMQ?.getQueueInfo(RabbitMQRequestSender.CLIENT_ASYNC_NAME) == null) {
                adminRabbitMQ?.declareQueue(Queue(RabbitMQRequestSender.CLIENT_ASYNC_NAME))
            }
            if (adminRabbitMQ?.getQueueInfo(RabbitMQRequestSender.CLIENT_SYNC_NAME) == null) {
                adminRabbitMQ?.declareQueue(Queue(RabbitMQRequestSender.CLIENT_SYNC_NAME))
            }
        }
    }

    @Throws(ClassNotFoundException::class)
    private fun getTopicNames(type: String): Set<String> {
        val topicsCreated: MutableSet<String> = HashSet()
        val apiImpls: MutableSet<Class<*>> = HashSet()
        if (type.contains("server")) {
            for (server in serverEndpoints?.endpoints!!) {
                require(server.isAnnotationPresent(ApiServer::class.java)) {
                    String.format("Class %s is not annotated as ApiServer!", server.name)
                }
                require(server.interfaces.isNotEmpty()) {
                    String.format("Class %s does not implement Api interface!", server.name)
                }
                val serverInterface = server.interfaces[0]
                require(serverInterface.isAnnotationPresent(Api::class.java)) {
                    String.format("Class %s does not implement Api interface!", server.name)
                }
                try {
                    server.getConstructor()
                } catch (e: NoSuchMethodException) {
                    throw IllegalArgumentException(String.format("Class %s does not have default constructor!", server.name))
                }
                apiImpls.add(serverInterface)
            }
        } else {
            if (clientEndpoints != null) {
                for (client in clientEndpoints.stream().map { obj: ClientEndpoint -> obj.endpoint }
                        .collect(Collectors.toList())) {
                    require(client.isAnnotationPresent(ApiClient::class.java)) { "Class " + client.name + " does has ApiClient annotation!" }
                    apiImpls.add(Class.forName(Utils.getServiceInterfaceNameFromClient(client.name)))
                }
            }
        }
        apiImpls.forEach(Consumer { x: Class<*> -> topicsCreated.add(x.name + "-" + moduleId + "-" + type) })
        return topicsCreated
    }

    @Throws(ClassNotFoundException::class)
    private fun createKafkaTopics(type: String): Set<String> {
        val topicsCreated = getTopicNames(type)
        topicsCreated.forEach(Consumer { topic: String ->
            if (!zkClient!!.topicExists(topic))
                adminZkClient!!.createTopic(topic, brokersCount, 1, Properties(), RackAwareMode.`Disabled$`.`MODULE$`)
            else
                check(Integer.valueOf(zkClient!!.getTopicPartitionCount(topic).get().toString() + "") == brokersCount) { "Topic $topic has wrong config" }
        })
        return topicsCreated
    }

    @PostConstruct
    open fun init() {
        try {
            require(moduleId != null) { "moduleId can not be null!" }
            OptionConstants.setModuleId(moduleId)
            Utils.loadExternalProperties(moduleId)
            loadInternalProperties()
            val startedTime = System.currentTimeMillis()
            prepareServiceRegistration()
            var started: CountDownLatch? = null
            var expectedThreadCount = 0
            Serializer.init()
            val protocol = Utils.rpcProtocol
            if (clientEndpoints != null && clientEndpoints.isNotEmpty()) {
                clientEndpoints.forEach(Consumer { x: ClientEndpoint ->
                    if (x.ticketProvider != null) clientsAndTicketProviders[x.endpoint] = x.ticketProvider
                })
            }
            when (protocol) {
                Protocol.KAFKA -> {
                    if (clientSyncTopics?.isNotEmpty() == true && clientAsyncTopics?.isNotEmpty() == true) expectedThreadCount += 2
                    if (serverSyncTopics?.isNotEmpty() == true && serverAsyncTopics?.isNotEmpty() == true) expectedThreadCount += 2
                    if (expectedThreadCount != 0) started = CountDownLatch(brokersCount * expectedThreadCount)
                    if (serverSyncTopics?.isNotEmpty() == true && serverAsyncTopics?.isNotEmpty() == true) {
                        val kafkaSyncRequestReceiver = KafkaSyncRequestReceiver(started)
                        val kafkaAsyncRequestReceiver = KafkaAsyncRequestReceiver(started)
                        kafkaReceivers.add(kafkaAsyncRequestReceiver)
                        kafkaReceivers.add(kafkaSyncRequestReceiver)
                        receiverThreads.add(Thread(kafkaSyncRequestReceiver))
                        receiverThreads.add(Thread(kafkaAsyncRequestReceiver))
                    }
                    if (clientSyncTopics?.isNotEmpty() == true && clientAsyncTopics?.isNotEmpty() == true) {
                        val kafkaAsyncResponseReceiver = KafkaAsyncResponseReceiver(started)
                        kafkaReceivers.add(kafkaAsyncResponseReceiver)
                        KafkaRequestSender.initSyncKafkaConsumers(brokersCount, started!!)
                        receiverThreads.add(Thread(kafkaAsyncResponseReceiver))
                    }
                }
                Protocol.ZMQ -> {
                    if (System.getProperty(OptionConstants.ZMQ_CURVE_ENABLED, false.toString()).toBoolean()) {
                        CurveUtils.readClientKeys()
                        CurveUtils.readServerKeys()
                    }
                    if (serverEndpoints != null && serverEndpoints.endpoints.isNotEmpty()) {
                        val zmqSyncRequestReceiver = ZMQAsyncAndSyncRequestReceiver()
                        zmqReceivers.add(zmqSyncRequestReceiver)
                        receiverThreads.add(Thread(zmqSyncRequestReceiver))
                    }
                    if (clientEndpoints != null && clientEndpoints.isNotEmpty()) {
                        val zmqAsyncResponseReceiver = ZMQAsyncResponseReceiver()
                        zmqReceivers.add(zmqAsyncResponseReceiver)
                        receiverThreads.add(Thread(zmqAsyncResponseReceiver))
                    }
                }
                Protocol.HTTP -> {
                    HttpAsyncAndSyncRequestReceiver.initClient()
                    if (serverEndpoints != null && serverEndpoints.endpoints.isNotEmpty()) {
                        val httpAsyncAndSyncRequestReceiver = HttpAsyncAndSyncRequestReceiver()
                        zmqReceivers.add(httpAsyncAndSyncRequestReceiver)
                        receiverThreads.add(Thread(httpAsyncAndSyncRequestReceiver))
                    }
                    if (clientEndpoints != null && clientEndpoints.isNotEmpty()) {
                        val httpAsyncResponseReceiver = HttpAsyncResponseReceiver()
                        zmqReceivers.add(httpAsyncResponseReceiver)
                        receiverThreads.add(Thread(httpAsyncResponseReceiver))
                    }
                }
                Protocol.GRPC -> {
                    if (serverEndpoints != null && serverEndpoints.endpoints.isNotEmpty()) {
                        val grpcAsyncAndSyncRequestReceiver = GrpcAsyncAndSyncRequestReceiver()
                        zmqReceivers.add(grpcAsyncAndSyncRequestReceiver)
                        receiverThreads.add(Thread(grpcAsyncAndSyncRequestReceiver))
                    }
                    if (clientEndpoints != null && clientEndpoints.isNotEmpty()) {
                        val grpcAsyncResponseReceiver = GrpcAsyncResponseReceiver()
                        zmqReceivers.add(grpcAsyncResponseReceiver)
                        receiverThreads.add(Thread(grpcAsyncResponseReceiver))
                    }
                }
                Protocol.RABBIT -> {
                    if (serverEndpoints != null && serverEndpoints.endpoints.isNotEmpty()) {
                        val rabbitMQAsyncAndSyncRequestReceiver = RabbitMQAsyncAndSyncRequestReceiver()
                        zmqReceivers.add(rabbitMQAsyncAndSyncRequestReceiver)
                        receiverThreads.add(Thread(rabbitMQAsyncAndSyncRequestReceiver))
                    }
                    if (clientEndpoints != null && clientEndpoints.isNotEmpty()) {
                        val rabbitMQAsyncResponseReceiver = RabbitMQAsyncResponseReceiver()
                        zmqReceivers.add(rabbitMQAsyncResponseReceiver)
                        receiverThreads.add(Thread(rabbitMQAsyncResponseReceiver))
                    }
                    RabbitMQRequestSender.init()
                }
            }
            receiverThreads.forEach(Consumer { obj: Thread -> obj.start() })
            if (expectedThreadCount != 0) started?.await()
            registerServices()
            FinalizationHelper.startFinalizer(context)
            log.info(
                    """
                    .---.                                             
                    |   |                                               
                    '---'                                               
                    .---.                 _.._       _.._               
                    |   |               .' .._|    .' .._|              
                    |   |     __        | '        | '         __       
                    |   |  .:--.'.    __| |__    __| |__    .:--.'.     
                    |   | / |   \ |  |__   __|  |__   __|  / |   \ |  
                    |   | `" __ | |     | |        | |     `" __ | |  
                    |   |  .'.''| |     | |        | |      .'.''| |    
                 __.'   ' / /   | |_    | |        | |     / /   | |_   
                |      '  \ \._,\ '/    | |        | |     \ \._,\ '/ 
                |____.'    `--'  `"     |_|        |_|      `--'  `"  
                                                       STARTED IN {} MS 
                """, System.currentTimeMillis() - startedTime
            )
        } catch (e: Exception) {
            throw JaffaRpcSystemException(e)
        }
    }

    fun close() {
        log.info("Close started")
        kafkaReceivers.forEach(Consumer { obj: KafkaReceiver -> obj.close() })
        log.info("Kafka receivers closed")
        KafkaRequestSender.shutDownConsumers()
        log.info("Kafka sync response consumers closed")
        if (Utils.conn != null) {
            try {
                if (!Utils.isZkTestMode) {
                    Utils.services.forEach { Utils.deleteAllRegistrations(it) }
                }
                Utils.conn?.close()
                Utils.conn = null
            } catch (e: Exception) {
                log.error("Unable to unregister services from ZooKeeper cluster, probably it was done earlier", e)
            }
        }
        log.info("Services were unregistered")
        zmqReceivers.forEach(Consumer { a: Closeable ->
            try {
                a.close()
            } catch (e: Exception) {
                log.error("Unable to shut down ZeroMQ receivers", e)
            }
        })
        log.info("ZMQ receivers were terminated")
        GrpcRequestSender.shutDownChannels()
        log.info("gRPC sender channels were shut down")
        GrpcAsyncAndSyncRequestReceiver.shutDownChannels()
        log.info("gRPC receiver channels were shut down")
        val zkCtx: ZContext = ZeroMqRequestSender.context
        if (!zkCtx.isClosed) zkCtx.close()
        log.info("ZMQ sender context was shut down")
        RabbitMQRequestSender.close()
        log.info("All ZMQ sockets were closed")
        for (thread in receiverThreads) {
            do {
                thread.interrupt()
            } while (thread.state != Thread.State.TERMINATED)
        }
        log.info("All receiver threads stopped")
        FinalizationHelper.stopFinalizer()
        log.info("Jaffa RPC shutdown completed")
    }

    companion object {
        val producerProps = Properties()

        val consumerProps = Properties()

        val clientsAndTicketProviders: MutableMap<Class<*>, Class<out TicketProvider>> = HashedMap()

        var zkClient: KafkaZkClient? = null

        var brokersCount = 0

        var serverAsyncTopics: Set<String>? = null

        var clientAsyncTopics: Set<String>? = null

        var serverSyncTopics: Set<String>? = null

        var clientSyncTopics: Set<String>? = null

        var adminZkClient: AdminZkClient? = null

        var adminRabbitMQ: RabbitAdmin? = null

        var connectionFactory: ConnectionFactory? = null
        fun loadInternalProperties() {
            if (Utils.rpcProtocol == Protocol.KAFKA) {
                consumerProps["bootstrap.servers"] = Utils.getRequiredOption(OptionConstants.KAFKA_BOOTSTRAP_SERVERS)
                consumerProps["key.deserializer"] = "org.apache.kafka.common.serialization.StringDeserializer"
                consumerProps["value.deserializer"] = "org.apache.kafka.common.serialization.ByteArrayDeserializer"
                consumerProps["enable.auto.commit"] = false.toString()
                consumerProps["group.id"] = UUID.randomUUID().toString()
                producerProps["bootstrap.servers"] = Utils.getRequiredOption(OptionConstants.KAFKA_BOOTSTRAP_SERVERS)
                producerProps["key.serializer"] = "org.apache.kafka.common.serialization.StringSerializer"
                producerProps["value.serializer"] = "org.apache.kafka.common.serialization.ByteArraySerializer"
                if (System.getProperty(OptionConstants.KAFKA_USE_SSL, false.toString()).toBoolean()) {
                    val sslProps: MutableMap<String, String?> = HashMap()
                    sslProps["security.protocol"] = "SSL"
                    sslProps["ssl.truststore.location"] = Utils.getRequiredOption(OptionConstants.KAFKA_SSL_TRUSTSTORE_LOCATION)
                    sslProps["ssl.truststore.password"] = Utils.getRequiredOption(OptionConstants.KAFKA_SSL_TRUSTSTORE_PASSWORD)
                    sslProps["ssl.keystore.location"] = Utils.getRequiredOption(OptionConstants.KAFKA_SSL_KEYSTORE_LOCATION)
                    sslProps["ssl.keystore.password"] = Utils.getRequiredOption(OptionConstants.KAFKA_SSL_KEYSTORE_PASSWORD)
                    sslProps["ssl.key.password"] = Utils.getRequiredOption(OptionConstants.KAFKA_SSL_KEY_PASSWORD)
                    consumerProps.putAll(sslProps)
                    producerProps.putAll(sslProps)
                }
            }
        }
    }
}