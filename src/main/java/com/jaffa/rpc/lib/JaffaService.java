package com.jaffa.rpc.lib;

import com.jaffa.rpc.lib.annotations.Api;
import com.jaffa.rpc.lib.annotations.ApiClient;
import com.jaffa.rpc.lib.annotations.ApiServer;
import com.jaffa.rpc.lib.common.FinalizationWorker;
import com.jaffa.rpc.lib.common.RequestInvoker;
import com.jaffa.rpc.lib.entities.Protocol;
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException;
import com.jaffa.rpc.lib.http.receivers.HttpAsyncAndSyncRequestReceiver;
import com.jaffa.rpc.lib.http.receivers.HttpAsyncResponseReceiver;
import com.jaffa.rpc.lib.kafka.KafkaRequestSender;
import com.jaffa.rpc.lib.kafka.receivers.KafkaAsyncRequestReceiver;
import com.jaffa.rpc.lib.kafka.receivers.KafkaAsyncResponseReceiver;
import com.jaffa.rpc.lib.kafka.receivers.KafkaReceiver;
import com.jaffa.rpc.lib.kafka.receivers.KafkaSyncRequestReceiver;
import com.jaffa.rpc.lib.rabbitmq.RabbitMQRequestSender;
import com.jaffa.rpc.lib.rabbitmq.receivers.RabbitMQAsyncAndSyncRequestReceiver;
import com.jaffa.rpc.lib.rabbitmq.receivers.RabbitMQAsyncResponseReceiver;
import com.jaffa.rpc.lib.serialization.Serializer;
import com.jaffa.rpc.lib.spring.ClientEndpoints;
import com.jaffa.rpc.lib.spring.ServerEndpoints;
import com.jaffa.rpc.lib.zeromq.CurveUtils;
import com.jaffa.rpc.lib.zeromq.ZeroMqRequestSender;
import com.jaffa.rpc.lib.zeromq.receivers.ZMQAsyncAndSyncRequestReceiver;
import com.jaffa.rpc.lib.zeromq.receivers.ZMQAsyncResponseReceiver;
import com.jaffa.rpc.lib.zookeeper.Utils;
import com.jaffa.rpc.lib.zookeeper.ZooKeeperConnection;
import kafka.admin.RackAwareMode;
import kafka.zk.AdminZkClient;
import kafka.zk.KafkaZkClient;
import kafka.zookeeper.ZooKeeperClient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.utils.Time;
import org.apache.zookeeper.KeeperException;
import org.json.simple.parser.ParseException;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.RabbitConnectionFactoryBean;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.zeromq.ZContext;
import scala.Option;

import javax.annotation.PostConstruct;
import java.io.Closeable;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.CountDownLatch;

@Slf4j
@SuppressWarnings({"squid:S2142", "squid:S2095", "unused", "squid:S3776"})
public class JaffaService {

    @Getter
    private static final Properties producerProps = new Properties();
    @Getter
    private static final Properties consumerProps = new Properties();
    @Getter
    @Setter(AccessLevel.PRIVATE)
    private static KafkaZkClient zkClient;
    @Getter
    @Setter(AccessLevel.PRIVATE)
    private static int brokersCount = 0;
    @Getter
    @Setter(AccessLevel.PRIVATE)
    private static Set<String> serverAsyncTopics;
    @Getter
    @Setter(AccessLevel.PRIVATE)
    private static Set<String> clientAsyncTopics;
    @Getter
    @Setter(AccessLevel.PRIVATE)
    private static Set<String> serverSyncTopics;
    @Getter
    @Setter(AccessLevel.PRIVATE)
    private static Set<String> clientSyncTopics;
    @Setter(AccessLevel.PRIVATE)
    private static AdminZkClient adminZkClient;
    @Setter(AccessLevel.PRIVATE)
    private static RabbitAdmin adminRabbitMQ;
    @Setter(AccessLevel.PRIVATE)
    @Getter(AccessLevel.PUBLIC)
    private static ConnectionFactory connectionFactory;
    private final List<KafkaReceiver> kafkaReceivers = new ArrayList<>();
    private final List<Closeable> zmqReceivers = new ArrayList<>();
    private final List<Thread> receiverThreads = new ArrayList<>();
    @Autowired
    private ServerEndpoints serverEndpoints;
    @Autowired
    private ClientEndpoints clientEndpoints;
    @Autowired
    private ApplicationContext context;

    private static void loadInternalProperties() {
        if (Utils.getRpcProtocol().equals(Protocol.KAFKA)) {
            consumerProps.put("bootstrap.servers", Utils.getRequiredOption("jaffa.rpc.protocol.kafka.bootstrap.servers"));
            consumerProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            consumerProps.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
            consumerProps.put("enable.auto.commit", String.valueOf(false));
            consumerProps.put("group.id", UUID.randomUUID().toString());

            producerProps.put("bootstrap.servers", Utils.getRequiredOption("jaffa.rpc.protocol.kafka.bootstrap.servers"));
            producerProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            producerProps.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");

            if (Boolean.parseBoolean(System.getProperty("jaffa.rpc.protocol.kafka.use.ssl", String.valueOf(false)))) {
                Map<String, String> sslProps = new HashMap<>();
                sslProps.put("security.protocol", "SSL");
                sslProps.put("ssl.truststore.location", Utils.getRequiredOption("jaffa.rpc.protocol.kafka.ssl.truststore.location"));
                sslProps.put("ssl.truststore.password", Utils.getRequiredOption("jaffa.rpc.protocol.kafka.ssl.truststore.password"));
                sslProps.put("ssl.keystore.location", Utils.getRequiredOption("jaffa.rpc.protocol.kafka.ssl.keystore.location"));
                sslProps.put("ssl.keystore.password", Utils.getRequiredOption("jaffa.rpc.protocol.kafka.ssl.keystore.password"));
                sslProps.put("ssl.key.password", Utils.getRequiredOption("jaffa.rpc.protocol.kafka.ssl.key.password"));
                consumerProps.putAll(sslProps);
                producerProps.putAll(sslProps);
            }
        }
    }

    private void registerServices() {
        Map<Class<?>, Class<?>> apiImpls = new HashMap<>();
        for (Class<?> server : serverEndpoints.getEndpoints()) {
            log.info("Server endpoint: {}", server.getName());
            apiImpls.put(server, server.getInterfaces()[0]);
        }
        for (Map.Entry<Class<?>, Class<?>> apiImpl : apiImpls.entrySet()) {
            RequestInvoker.getWrappedServices().put(apiImpl.getValue(), context.getBean(apiImpl.getKey()));
            Utils.registerService(apiImpl.getValue().getName(), Utils.getRpcProtocol());
        }
        RequestInvoker.setContext(context);
    }

    @SuppressWarnings("squid:S2583")
    private void prepareServiceRegistration() throws ClassNotFoundException {
        Utils.connect(Utils.getRequiredOption("jaffa.rpc.zookeeper.connection"));
        Protocol protocol = Utils.getRpcProtocol();
        if (protocol.equals(Protocol.KAFKA)) {
            ZooKeeperClient zooKeeperClient = new ZooKeeperClient(Utils.getRequiredOption("jaffa.rpc.zookeeper.connection"),
                    200000,
                    15000,
                    10,
                    Time.SYSTEM,
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    null, Option.apply(ZooKeeperConnection.getZkConfig()));
            JaffaService.setZkClient(new KafkaZkClient(zooKeeperClient, false, Time.SYSTEM));
            JaffaService.setAdminZkClient(new AdminZkClient(zkClient));
            JaffaService.setBrokersCount(zkClient.getAllBrokersInCluster().size());
            log.info("Kafka brokers: {}", brokersCount);
            JaffaService.setServerAsyncTopics(createKafkaTopics("server-async"));
            JaffaService.setClientAsyncTopics(createKafkaTopics("client-async"));
            JaffaService.setServerSyncTopics(createKafkaTopics("server-sync"));
            JaffaService.setClientSyncTopics(createKafkaTopics("client-sync"));
        }
        if (protocol.equals(Protocol.RABBIT)) {
            String rabbitHost = Utils.getRequiredOption("jaffa.rpc.protocol.rabbit.host");
            int rabbitPort = Integer.parseInt(Utils.getRequiredOption("jaffa.rpc.protocol.rabbit.port"));
            RabbitConnectionFactoryBean factory = new RabbitConnectionFactoryBean();
            factory.setHost(rabbitHost);
            factory.setPort(rabbitPort);
            factory.setUsername(System.getProperty("jaffa.rpc.protocol.rabbit.login", "guest"));
            factory.setPassword(System.getProperty("jaffa.rpc.protocol.rabbit.password", "guest"));
            if (Boolean.parseBoolean(System.getProperty("jaffa.rpc.protocol.rabbit.use.ssl", "false"))) {
                factory.setUseSSL(true);
                factory.setKeyStore(Utils.getRequiredOption("jaffa.rpc.protocol.rabbit.ssl.keystore.location"));
                factory.setKeyStorePassphrase(Utils.getRequiredOption("jaffa.rpc.protocol.rabbit.ssl.keystore.password"));
                factory.setTrustStore(Utils.getRequiredOption("jaffa.rpc.protocol.rabbit.ssl.truststore.location"));
                factory.setTrustStorePassphrase(Utils.getRequiredOption("jaffa.rpc.protocol.rabbit.ssl.truststore.password"));
            }
            JaffaService.setConnectionFactory(new CachingConnectionFactory(factory.getRabbitConnectionFactory()));
            JaffaService.setAdminRabbitMQ(new RabbitAdmin(JaffaService.connectionFactory));
            JaffaService.adminRabbitMQ.declareExchange(new DirectExchange(RabbitMQRequestSender.EXCHANGE_NAME, true, false));
            if (JaffaService.adminRabbitMQ.getQueueInfo(RabbitMQRequestSender.SERVER) == null) {
                JaffaService.adminRabbitMQ.declareQueue(new Queue(RabbitMQRequestSender.SERVER));
            }
            if (JaffaService.adminRabbitMQ.getQueueInfo(RabbitMQRequestSender.CLIENT_ASYNC_NAME) == null) {
                JaffaService.adminRabbitMQ.declareQueue(new Queue(RabbitMQRequestSender.CLIENT_ASYNC_NAME));
            }
            if (JaffaService.adminRabbitMQ.getQueueInfo(RabbitMQRequestSender.CLIENT_SYNC_NAME) == null) {
                JaffaService.adminRabbitMQ.declareQueue(new Queue(RabbitMQRequestSender.CLIENT_SYNC_NAME));
            }
        }
    }

    private Set<String> getTopicNames(String type) throws ClassNotFoundException {
        Set<String> topicsCreated = new HashSet<>();
        Set<Class<?>> apiImpls = new HashSet<>();
        if (type.contains("server")) {
            for (Class<?> server : serverEndpoints.getEndpoints()) {
                if (!server.isAnnotationPresent(ApiServer.class))
                    throw new IllegalArgumentException(String.format("Class %s is not annotated as ApiServer!", server.getName()));
                if (server.getInterfaces().length == 0)
                    throw new IllegalArgumentException(String.format("Class %s does not implement Api interface!", server.getName()));
                Class<?> serverInterface = server.getInterfaces()[0];
                if (!serverInterface.isAnnotationPresent(Api.class))
                    throw new IllegalArgumentException(String.format("Class %s does not implement Api interface!", server.getName()));
                try {
                    server.getConstructor();
                } catch (NoSuchMethodException e) {
                    log.error("General error during endpoint initialization", e);
                    throw new IllegalArgumentException(String.format("Class %s does not have default constructor!", server.getName()));
                }
                apiImpls.add(serverInterface);
            }
        } else {
            for (Class<?> client : clientEndpoints.getEndpoints()) {
                if (!client.isAnnotationPresent(ApiClient.class))
                    throw new IllegalArgumentException("Class " + client.getName() + " does has ApiClient annotation!");
                apiImpls.add(Class.forName(Utils.getServiceInterfaceNameFromClient(client.getName())));
            }
        }
        apiImpls.forEach(x -> topicsCreated.add(x.getName() + "-" + Utils.getRequiredOption("jaffa.rpc.module.id") + "-" + type));
        return topicsCreated;
    }

    private Set<String> createKafkaTopics(String type) throws ClassNotFoundException {
        Set<String> topicsCreated = getTopicNames(type);
        topicsCreated.forEach(topic -> {
            if (!zkClient.topicExists(topic))
                adminZkClient.createTopic(topic, brokersCount, 1, new Properties(), RackAwareMode.Disabled$.MODULE$);
            else if (!Integer.valueOf(zkClient.getTopicPartitionCount(topic).get() + "").equals(brokersCount))
                throw new IllegalStateException("Topic " + topic + " has wrong config");
        });
        return topicsCreated;
    }

    @PostConstruct
    @SuppressWarnings("unused")
    private void init() {
        try {
            Utils.loadExternalProperties();
            loadInternalProperties();
            long startedTime = System.currentTimeMillis();
            prepareServiceRegistration();
            CountDownLatch started = null;
            int expectedThreadCount = 0;
            Protocol protocol = Utils.getRpcProtocol();
            switch (protocol) {
                case KAFKA:
                    if (!clientSyncTopics.isEmpty() && !clientAsyncTopics.isEmpty()) expectedThreadCount += 2;
                    if (!serverSyncTopics.isEmpty() && !serverAsyncTopics.isEmpty()) expectedThreadCount += 2;
                    if (expectedThreadCount != 0) started = new CountDownLatch(brokersCount * expectedThreadCount);
                    if (!serverSyncTopics.isEmpty() && !serverAsyncTopics.isEmpty()) {
                        KafkaSyncRequestReceiver kafkaSyncRequestReceiver = new KafkaSyncRequestReceiver(started);
                        KafkaAsyncRequestReceiver kafkaAsyncRequestReceiver = new KafkaAsyncRequestReceiver(started);
                        this.kafkaReceivers.add(kafkaAsyncRequestReceiver);
                        this.kafkaReceivers.add(kafkaSyncRequestReceiver);
                        this.receiverThreads.add(new Thread(kafkaSyncRequestReceiver));
                        this.receiverThreads.add(new Thread(kafkaAsyncRequestReceiver));
                    }
                    if (!clientSyncTopics.isEmpty() && !clientAsyncTopics.isEmpty()) {
                        KafkaAsyncResponseReceiver kafkaAsyncResponseReceiver = new KafkaAsyncResponseReceiver(started);
                        this.kafkaReceivers.add(kafkaAsyncResponseReceiver);
                        KafkaRequestSender.initSyncKafkaConsumers(brokersCount, started);
                        this.receiverThreads.add(new Thread(kafkaAsyncResponseReceiver));
                    }
                    break;
                case ZMQ:
                    if (serverEndpoints.getEndpoints().length != 0) {
                        ZMQAsyncAndSyncRequestReceiver zmqSyncRequestReceiver = new ZMQAsyncAndSyncRequestReceiver();
                        this.zmqReceivers.add(zmqSyncRequestReceiver);
                        this.receiverThreads.add(new Thread(zmqSyncRequestReceiver));
                    }
                    if (clientEndpoints.getEndpoints().length != 0) {
                        ZMQAsyncResponseReceiver zmqAsyncResponseReceiver = new ZMQAsyncResponseReceiver();
                        this.zmqReceivers.add(zmqAsyncResponseReceiver);
                        this.receiverThreads.add(new Thread(zmqAsyncResponseReceiver));
                    }
                    if (Boolean.parseBoolean(System.getProperty("jaffa.rpc.protocol.zmq.curve.enabled", String.valueOf(false)))) {
                        CurveUtils.readClientKeys();
                        CurveUtils.readServerKeys();
                    }
                    break;
                case HTTP:
                    HttpAsyncAndSyncRequestReceiver.initClient();
                    if (serverEndpoints.getEndpoints().length != 0) {
                        HttpAsyncAndSyncRequestReceiver httpAsyncAndSyncRequestReceiver = new HttpAsyncAndSyncRequestReceiver();
                        this.zmqReceivers.add(httpAsyncAndSyncRequestReceiver);
                        this.receiverThreads.add(new Thread(httpAsyncAndSyncRequestReceiver));
                    }
                    if (clientEndpoints.getEndpoints().length != 0) {
                        HttpAsyncResponseReceiver httpAsyncResponseReceiver = new HttpAsyncResponseReceiver();
                        this.zmqReceivers.add(httpAsyncResponseReceiver);
                        this.receiverThreads.add(new Thread(httpAsyncResponseReceiver));
                    }
                    break;
                case RABBIT:
                    if (serverEndpoints.getEndpoints().length != 0) {
                        RabbitMQAsyncAndSyncRequestReceiver rabbitMQAsyncAndSyncRequestReceiver = new RabbitMQAsyncAndSyncRequestReceiver();
                        this.zmqReceivers.add(rabbitMQAsyncAndSyncRequestReceiver);
                        this.receiverThreads.add(new Thread(rabbitMQAsyncAndSyncRequestReceiver));
                    }
                    if (clientEndpoints.getEndpoints().length != 0) {
                        RabbitMQAsyncResponseReceiver rabbitMQAsyncResponseReceiver = new RabbitMQAsyncResponseReceiver();
                        rabbitMQAsyncResponseReceiver.setContext(context);
                        this.zmqReceivers.add(rabbitMQAsyncResponseReceiver);
                        this.receiverThreads.add(new Thread(rabbitMQAsyncResponseReceiver));
                    }
                    RabbitMQRequestSender.init();
                    break;
                default:
                    throw new JaffaRpcSystemException("No known protocol defined");
            }
            this.receiverThreads.forEach(Thread::start);
            if (expectedThreadCount != 0) started.await();
            registerServices();
            FinalizationWorker.startFinalizer(context);
            log.info("\n    .---.                                             \n" +
                    "    |   |                                               \n" +
                    "    '---'                                               \n" +
                    "    .---.                 _.._       _.._               \n" +
                    "    |   |               .' .._|    .' .._|              \n" +
                    "    |   |     __        | '        | '         __       \n" +
                    "    |   |  .:--.'.    __| |__    __| |__    .:--.'.     \n" +
                    "    |   | / |   \\ |  |__   __|  |__   __|  / |   \\ |  \n" +
                    "    |   | `\" __ | |     | |        | |     `\" __ | |  \n" +
                    "    |   |  .'.''| |     | |        | |      .'.''| |    \n" +
                    " __.'   ' / /   | |_    | |        | |     / /   | |_   \n" +
                    "|      '  \\ \\._,\\ '/    | |        | |     \\ \\._,\\ '/ \n" +
                    "|____.'    `--'  `\"     |_|        |_|      `--'  `\"  \n" +
                    "                                       STARTED IN {} MS \n", System.currentTimeMillis() - startedTime);
        } catch (Exception e) {
            log.error("Exception during Jaffa RPC library startup:", e);
            throw new JaffaRpcSystemException(e);
        }
    }

    public void close() {
        log.info("Close started");
        this.kafkaReceivers.forEach(KafkaReceiver::close);
        log.info("Kafka receivers closed");
        KafkaRequestSender.shutDownConsumers();
        log.info("Kafka sync response consumers closed");
        if (Utils.getConn() != null) {
            try {
                for (String service : Utils.getServices()) {
                    Utils.deleteAllRegistrations(service);
                }
                if (Utils.getConn() != null) Utils.getConn().close();
                Utils.setConn(null);
            } catch (KeeperException | InterruptedException | ParseException | UnknownHostException e) {
                log.error("Unable to unregister services from ZooKeeper cluster, probably it was done earlier");
            }
        }
        log.info("Services were unregistered");
        this.zmqReceivers.forEach(a -> {
            try {
                a.close();
            } catch (IOException e) {
                log.error("Unable to shut down ZeroMQ receivers", e);
                throw new JaffaRpcSystemException(e);
            }
        });
        ZContext zkCtx = ZeroMqRequestSender.context;
        if (!zkCtx.isClosed()) zkCtx.close();
        RabbitMQRequestSender.close();
        log.info("All ZMQ sockets were closed");
        for (Thread thread : this.receiverThreads) {
            do {
                thread.interrupt();
            } while (thread.getState() != Thread.State.TERMINATED);
        }
        log.info("All receiver threads stopped");
        FinalizationWorker.stopFinalizer();
        log.info("Finalizer was stopped");
        log.info("Jaffa RPC shutdown completed");
    }
}