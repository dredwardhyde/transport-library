package com.jaffa.rpc.lib;

import com.jaffa.rpc.lib.annotations.Api;
import com.jaffa.rpc.lib.annotations.ApiClient;
import com.jaffa.rpc.lib.annotations.ApiServer;
import com.jaffa.rpc.lib.common.FinalizationHelper;
import com.jaffa.rpc.lib.common.OptionConstants;
import com.jaffa.rpc.lib.common.RequestInvocationHelper;
import com.jaffa.rpc.lib.entities.Protocol;
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException;
import com.jaffa.rpc.lib.grpc.GrpcRequestSender;
import com.jaffa.rpc.lib.grpc.receivers.GrpcAsyncAndSyncRequestReceiver;
import com.jaffa.rpc.lib.grpc.receivers.GrpcAsyncResponseReceiver;
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
import com.jaffa.rpc.lib.security.TicketProvider;
import com.jaffa.rpc.lib.serialization.Serializer;
import com.jaffa.rpc.lib.spring.ClientEndpoint;
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
import org.apache.commons.collections4.map.HashedMap;
import org.apache.kafka.common.utils.Time;
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
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

@Slf4j
@SuppressWarnings({"squid:S2142", "squid:S2095", "unused", "squid:S3776"})
public class JaffaService {

    @Getter
    private static final Properties producerProps = new Properties();
    @Getter
    private static final Properties consumerProps = new Properties();
    @Getter
    @Setter
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
    @Setter
    @Getter
    private static AdminZkClient adminZkClient;
    @Setter(AccessLevel.PRIVATE)
    private static RabbitAdmin adminRabbitMQ;
    @Setter
    @Getter
    private static ConnectionFactory connectionFactory;
    private final List<KafkaReceiver> kafkaReceivers = new ArrayList<>();
    private final List<Closeable> zmqReceivers = new ArrayList<>();
    private final List<Thread> receiverThreads = new ArrayList<>();
    @Autowired
    private ServerEndpoints serverEndpoints;
    @Autowired
    private List<ClientEndpoint> clientEndpoints;
    @Autowired
    private ApplicationContext context;

    @Getter
    private static Map<Class<?>, Class<? extends TicketProvider>> clientsAndTicketProviders = new HashedMap<>();

    private static void loadInternalProperties() {
        if (Utils.getRpcProtocol().equals(Protocol.KAFKA)) {
            consumerProps.put("bootstrap.servers", Utils.getRequiredOption(OptionConstants.KAFKA_BOOTSTRAP_SERVERS));
            consumerProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            consumerProps.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
            consumerProps.put("enable.auto.commit", String.valueOf(false));
            consumerProps.put("group.id", UUID.randomUUID().toString());

            producerProps.put("bootstrap.servers", Utils.getRequiredOption(OptionConstants.KAFKA_BOOTSTRAP_SERVERS));
            producerProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            producerProps.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");

            if (Boolean.parseBoolean(System.getProperty(OptionConstants.KAFKA_USE_SSL, String.valueOf(false)))) {
                Map<String, String> sslProps = new HashMap<>();
                sslProps.put("security.protocol", "SSL");
                sslProps.put("ssl.truststore.location", Utils.getRequiredOption(OptionConstants.KAFKA_SSL_TRUSTSTORE_LOCATION));
                sslProps.put("ssl.truststore.password", Utils.getRequiredOption(OptionConstants.KAFKA_SSL_TRUSTSTORE_PASSWORD));
                sslProps.put("ssl.keystore.location", Utils.getRequiredOption(OptionConstants.KAFKA_SSL_KEYSTORE_LOCATION));
                sslProps.put("ssl.keystore.password", Utils.getRequiredOption(OptionConstants.KAFKA_SSL_KEYSTORE_PASSWORD));
                sslProps.put("ssl.key.password", Utils.getRequiredOption(OptionConstants.KAFKA_SSL_KEY_PASSWORD));
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
            RequestInvocationHelper.getWrappedServices().put(apiImpl.getValue(), context.getBean(apiImpl.getKey()));
            Utils.registerService(apiImpl.getValue().getName(), Utils.getRpcProtocol());
        }
        RequestInvocationHelper.setContext(context);
    }

    @SuppressWarnings("squid:S2583")
    private void prepareServiceRegistration() throws ClassNotFoundException {
        Utils.connect(Utils.getRequiredOption(OptionConstants.ZOOKEEPER_CONNECTION));
        Protocol protocol = Utils.getRpcProtocol();
        if (protocol.equals(Protocol.KAFKA)) {
            ZooKeeperClient zooKeeperClient = new ZooKeeperClient(Utils.getRequiredOption(OptionConstants.ZOOKEEPER_CONNECTION),
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
            String rabbitHost = Utils.getRequiredOption(OptionConstants.RABBIT_HOST);
            int rabbitPort = Integer.parseInt(Utils.getRequiredOption(OptionConstants.RABBIT_PORT));
            RabbitConnectionFactoryBean factory = new RabbitConnectionFactoryBean();
            factory.setHost(rabbitHost);
            factory.setPort(rabbitPort);
            factory.setUsername(System.getProperty(OptionConstants.RABBIT_LOGIN, "guest"));
            factory.setPassword(System.getProperty(OptionConstants.RABBIT_PASSWORD, "guest"));
            if (Boolean.parseBoolean(System.getProperty(OptionConstants.RABBIT_USE_SSL, "false"))) {
                factory.setUseSSL(true);
                factory.setKeyStore(Utils.getRequiredOption(OptionConstants.RABBIT_SSL_KEYSTORE_LOCATION));
                factory.setKeyStorePassphrase(Utils.getRequiredOption(OptionConstants.RABBIT_SSL_KEYSTORE_PASSWORD));
                factory.setTrustStore(Utils.getRequiredOption(OptionConstants.RABBIT_SSL_TRUSTSTORE_LOCATION));
                factory.setTrustStorePassphrase(Utils.getRequiredOption(OptionConstants.RABBIT_SSL_TRUSTSTORE_PASSWORD));
            }
            if (Utils.isZkTestMode()) {
                JaffaService.setConnectionFactory(new CachingConnectionFactory(context.getBean(com.rabbitmq.client.ConnectionFactory.class)));
            } else {
                JaffaService.setConnectionFactory(new CachingConnectionFactory(factory.getRabbitConnectionFactory()));
            }
            JaffaService.setAdminRabbitMQ(new RabbitAdmin(JaffaService.connectionFactory));
            JaffaService.adminRabbitMQ.declareExchange(new DirectExchange(RabbitMQRequestSender.EXCHANGE_NAME, true, false));
            if (Objects.isNull(JaffaService.adminRabbitMQ.getQueueInfo(RabbitMQRequestSender.SERVER))) {
                JaffaService.adminRabbitMQ.declareQueue(new Queue(RabbitMQRequestSender.SERVER));
            }
            if (Objects.isNull(JaffaService.adminRabbitMQ.getQueueInfo(RabbitMQRequestSender.CLIENT_ASYNC_NAME))) {
                JaffaService.adminRabbitMQ.declareQueue(new Queue(RabbitMQRequestSender.CLIENT_ASYNC_NAME));
            }
            if (Objects.isNull(JaffaService.adminRabbitMQ.getQueueInfo(RabbitMQRequestSender.CLIENT_SYNC_NAME))) {
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
                    throw new IllegalArgumentException(String.format("Class %s does not have default constructor!", server.getName()));
                }
                apiImpls.add(serverInterface);
            }
        } else {
            if (Objects.nonNull(clientEndpoints)) {
                for (Class<?> client : clientEndpoints.stream().map(ClientEndpoint::getEndpoint).collect(Collectors.toList())) {
                    if (!client.isAnnotationPresent(ApiClient.class))
                        throw new IllegalArgumentException("Class " + client.getName() + " does has ApiClient annotation!");
                    apiImpls.add(Class.forName(Utils.getServiceInterfaceNameFromClient(client.getName())));
                }
            }
        }
        apiImpls.forEach(x -> topicsCreated.add(x.getName() + "-" + Utils.getRequiredOption(OptionConstants.MODULE_ID) + "-" + type));
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
            Serializer.init();
            Protocol protocol = Utils.getRpcProtocol();
            if (Objects.nonNull(clientEndpoints) && !clientEndpoints.isEmpty()) {
                clientEndpoints.forEach(x -> {
                    if (Objects.nonNull(x.getTicketProvider()))
                        clientsAndTicketProviders.put(x.getEndpoint(), x.getTicketProvider());
                });
            }
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
                    if (Boolean.parseBoolean(System.getProperty(OptionConstants.ZMQ_CURVE_ENABLED, String.valueOf(false)))) {
                        CurveUtils.readClientKeys();
                        CurveUtils.readServerKeys();
                    }
                    if (serverEndpoints.getEndpoints().length != 0) {
                        ZMQAsyncAndSyncRequestReceiver zmqSyncRequestReceiver = new ZMQAsyncAndSyncRequestReceiver();
                        this.zmqReceivers.add(zmqSyncRequestReceiver);
                        this.receiverThreads.add(new Thread(zmqSyncRequestReceiver));
                    }
                    if (Objects.nonNull(clientEndpoints) && !clientEndpoints.isEmpty()) {
                        ZMQAsyncResponseReceiver zmqAsyncResponseReceiver = new ZMQAsyncResponseReceiver();
                        this.zmqReceivers.add(zmqAsyncResponseReceiver);
                        this.receiverThreads.add(new Thread(zmqAsyncResponseReceiver));
                    }
                    break;
                case HTTP:
                    HttpAsyncAndSyncRequestReceiver.initClient();
                    if (serverEndpoints.getEndpoints().length != 0) {
                        HttpAsyncAndSyncRequestReceiver httpAsyncAndSyncRequestReceiver = new HttpAsyncAndSyncRequestReceiver();
                        this.zmqReceivers.add(httpAsyncAndSyncRequestReceiver);
                        this.receiverThreads.add(new Thread(httpAsyncAndSyncRequestReceiver));
                    }
                    if (Objects.nonNull(clientEndpoints) && !clientEndpoints.isEmpty()) {
                        HttpAsyncResponseReceiver httpAsyncResponseReceiver = new HttpAsyncResponseReceiver();
                        this.zmqReceivers.add(httpAsyncResponseReceiver);
                        this.receiverThreads.add(new Thread(httpAsyncResponseReceiver));
                    }
                    break;
                case GRPC:
                    if (serverEndpoints.getEndpoints().length != 0) {
                        GrpcAsyncAndSyncRequestReceiver grpcAsyncAndSyncRequestReceiver = new GrpcAsyncAndSyncRequestReceiver();
                        this.zmqReceivers.add(grpcAsyncAndSyncRequestReceiver);
                        this.receiverThreads.add(new Thread(grpcAsyncAndSyncRequestReceiver));
                    }
                    if (Objects.nonNull(clientEndpoints) && !clientEndpoints.isEmpty()) {
                        GrpcAsyncResponseReceiver grpcAsyncResponseReceiver = new GrpcAsyncResponseReceiver();
                        this.zmqReceivers.add(grpcAsyncResponseReceiver);
                        this.receiverThreads.add(new Thread(grpcAsyncResponseReceiver));
                    }
                    break;
                case RABBIT:
                    if (serverEndpoints.getEndpoints().length != 0) {
                        RabbitMQAsyncAndSyncRequestReceiver rabbitMQAsyncAndSyncRequestReceiver = new RabbitMQAsyncAndSyncRequestReceiver();
                        this.zmqReceivers.add(rabbitMQAsyncAndSyncRequestReceiver);
                        this.receiverThreads.add(new Thread(rabbitMQAsyncAndSyncRequestReceiver));
                    }
                    if (Objects.nonNull(clientEndpoints) && !clientEndpoints.isEmpty()) {
                        RabbitMQAsyncResponseReceiver rabbitMQAsyncResponseReceiver = new RabbitMQAsyncResponseReceiver();
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
            FinalizationHelper.startFinalizer(context);
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
            throw new JaffaRpcSystemException(e);
        }
    }

    public void close() {
        log.info("Close started");
        this.kafkaReceivers.forEach(KafkaReceiver::close);
        log.info("Kafka receivers closed");
        KafkaRequestSender.shutDownConsumers();
        log.info("Kafka sync response consumers closed");
        if (Objects.nonNull(Utils.getConn())) {
            try {
                if (!Utils.isZkTestMode()) {
                    for (String service : Utils.getServices()) {
                        Utils.deleteAllRegistrations(service);
                    }
                }
                if (Objects.nonNull(Utils.getConn())) Utils.getConn().close();
                Utils.setConn(null);
            } catch (Exception e) {
                log.error("Unable to unregister services from ZooKeeper cluster, probably it was done earlier", e);
            }
        }
        log.info("Services were unregistered");
        this.zmqReceivers.forEach(a -> {
            try {
                a.close();
            } catch (Exception e) {
                log.error("Unable to shut down ZeroMQ receivers", e);
            }
        });
        log.info("ZMQ receivers were terminated");
        GrpcRequestSender.shutDownChannels();
        log.info("gRPC sender channels were shut down");
        GrpcAsyncAndSyncRequestReceiver.shutDownChannels();
        log.info("gRPC receiver channels were shut down");
        ZContext zkCtx = ZeroMqRequestSender.context;
        if (!zkCtx.isClosed()) zkCtx.close();
        log.info("ZMQ sender context was shut down");
        RabbitMQRequestSender.close();
        log.info("All ZMQ sockets were closed");
        for (Thread thread : this.receiverThreads) {
            do {
                thread.interrupt();
            } while (thread.getState() != Thread.State.TERMINATED);
        }
        log.info("All receiver threads stopped");
        FinalizationHelper.stopFinalizer();
        log.info("Jaffa RPC shutdown completed");
    }
}