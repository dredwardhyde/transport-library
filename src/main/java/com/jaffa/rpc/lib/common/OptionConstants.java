package com.jaffa.rpc.lib.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OptionConstants {
    public static final String ZK_TEST_MODE                         = "jaffa.rpc.test.mode";
    public static final String MODULE_ID                            = "jaffa.rpc.module.id";
    public static final String PROTOCOL                             = "jaffa.rpc.protocol";
    public static final String SERIALIZER 					        = "jaffa.rpc.serializer";
    public static final String ZOOKEEPER_CONNECTION                 = "jaffa.rpc.zookeeper.connection";
    public static final String ZOOKEEPER_CLIENT_SECURE 		        = "jaffa.rpc.zookeeper.client.secure";
    public static final String ZOOKEEPER_CLIENT_CONTEXT		        = "jaffa.rpc.zookeeper.clientCnxnSocket";
    public static final String ZOOKEEPER_SSL_KEYSTORE_LOCATION      = "jaffa.rpc.zookeeper.ssl.keyStore.location";
    public static final String ZOOKEEPER_SSL_KEYSTORE_PASSWORD      = "jaffa.rpc.zookeeper.ssl.keyStore.password";
    public static final String ZOOKEEPER_SSL_TRUSTSTORE_LOCATION    = "jaffa.rpc.zookeeper.ssl.trustStore.location";
    public static final String ZOOKEEPER_SSL_TRUSTSTORE_PASSWORD    = "jaffa.rpc.zookeeper.ssl.trustStore.password";
    public static final String ADMIN_USE_HTTPS                      = "jaffa.admin.use.https";
    public static final String ADMIN_SSL_KEYSTORE_LOCATION          = "jaffa.rpc.admin.ssl.keystore.location";
    public static final String ADMIN_SSL_KEYSTORE_PASSWORD          = "jaffa.rpc.admin.ssl.keystore.password";
    public static final String ADMIN_SSL_TRUSTSTORE_LOCATION        = "jaffa.rpc.admin.ssl.truststore.location";
    public static final String ADMIN_SSL_TRUSTSTORE_PASSWORD        = "jaffa.rpc.admin.ssl.truststore.password";

    public static final String PROTOCOL_OPTION_PREFIX               = PROTOCOL + ".";
    public static final String SERVICE_PORT_OPTION_SUFFIX           = ".service.port";
    public static final String CALLBACK_PORT_OPTION_SUFFIX          = ".callback.port";

    public static final String USE_HTTPS                            = PROTOCOL_OPTION_PREFIX + "use.https";
    public static final String HTTP_SSL_SERVER_KEYSTORE_LOCATION    = PROTOCOL_OPTION_PREFIX + "http.ssl.server.keystore.location";
    public static final String HTTP_SSL_SERVER_KEYSTORE_PASSWORD    = PROTOCOL_OPTION_PREFIX + "http.ssl.server.keystore.password";
    public static final String HTTP_SSL_SERVER_TRUSTSTORE_LOCATION  = PROTOCOL_OPTION_PREFIX + "http.ssl.server.truststore.location";
    public static final String HTTP_SSL_SERVER_TRUSTSTORE_PASSWORD  = PROTOCOL_OPTION_PREFIX + "http.ssl.server.truststore.password";
    public static final String HTTP_SSL_CLIENT_KEYSTORE_LOCATION    = PROTOCOL_OPTION_PREFIX + "http.ssl.client.keystore.location";
    public static final String HTTP_SSL_CLIENT_KEYSTORE_PASSWORD    = PROTOCOL_OPTION_PREFIX + "http.ssl.client.keystore.password";
    public static final String HTTP_SSL_CLIENT_TRUSTSTORE_LOCATION  = PROTOCOL_OPTION_PREFIX + "http.ssl.client.truststore.location";
    public static final String HTTP_SSL_CLIENT_TRUSTSTORE_PASSWORD  = PROTOCOL_OPTION_PREFIX + "http.ssl.client.truststore.password";
    public static final String KAFKA_BOOTSTRAP_SERVERS              = PROTOCOL_OPTION_PREFIX + "kafka.bootstrap.servers";
    public static final String KAFKA_USE_SSL                        = PROTOCOL_OPTION_PREFIX + "kafka.use.ssl";
    public static final String KAFKA_SSL_TRUSTSTORE_LOCATION        = PROTOCOL_OPTION_PREFIX + "kafka.ssl.truststore.location";
    public static final String KAFKA_SSL_TRUSTSTORE_PASSWORD        = PROTOCOL_OPTION_PREFIX + "kafka.ssl.truststore.password";
    public static final String KAFKA_SSL_KEYSTORE_LOCATION          = PROTOCOL_OPTION_PREFIX + "kafka.ssl.keystore.location";
    public static final String KAFKA_SSL_KEYSTORE_PASSWORD          = PROTOCOL_OPTION_PREFIX + "kafka.ssl.keystore.password";
    public static final String KAFKA_SSL_KEY_PASSWORD               = PROTOCOL_OPTION_PREFIX + "kafka.ssl.key.password";
    public static final String ZMQ_CURVE_ENABLED                    = PROTOCOL_OPTION_PREFIX + "zmq.curve.enabled";
    public static final String ZMQ_CLIENT_DIR                       = PROTOCOL_OPTION_PREFIX + "zmq.client.dir";
    public static final String ZMQ_SERVER_KEYS                      = PROTOCOL_OPTION_PREFIX + "zmq.server.keys";
    public static final String ZMQ_CLIENT_KEY                       = PROTOCOL_OPTION_PREFIX + "zmq.client.key.";
    public static final String RABBIT_LOGIN                         = PROTOCOL_OPTION_PREFIX + "rabbit.login";
    public static final String RABBIT_HOST                          = PROTOCOL_OPTION_PREFIX + "rabbit.host";
    public static final String RABBIT_PORT                          = PROTOCOL_OPTION_PREFIX + "rabbit.port";
    public static final String RABBIT_PASSWORD                      = PROTOCOL_OPTION_PREFIX + "rabbit.password";
    public static final String RABBIT_USE_SSL                       = PROTOCOL_OPTION_PREFIX + "rabbit.use.ssl";
    public static final String RABBIT_SSL_KEYSTORE_LOCATION         = PROTOCOL_OPTION_PREFIX + "rabbit.ssl.keystore.location";
    public static final String RABBIT_SSL_KEYSTORE_PASSWORD         = PROTOCOL_OPTION_PREFIX + "rabbit.ssl.keystore.password";
    public static final String RABBIT_SSL_TRUSTSTORE_LOCATION       = PROTOCOL_OPTION_PREFIX + "rabbit.ssl.truststore.location";
    public static final String RABBIT_SSL_TRUSTSTORE_PASSWORD       = PROTOCOL_OPTION_PREFIX + "rabbit.ssl.truststore.password";

    public static final String GRPC_USE_SSL                         = PROTOCOL_OPTION_PREFIX + "grpc.use.ssl";
    public static final String GRPC_SSL_SERVER_KEY_LOCATION         = PROTOCOL_OPTION_PREFIX + "grpc.ssl.server.key.location";
    public static final String GRPC_SSL_SERVER_STORE_LOCATION       = PROTOCOL_OPTION_PREFIX + "grpc.ssl.server.store.location";
    public static final String GRPC_SSL_CLIENT_KEY_LOCATION         = PROTOCOL_OPTION_PREFIX + "grpc.ssl.client.key.location";
    public static final String GRPC_SSL_CLIENT_KEYSTORE_LOCATION    = PROTOCOL_OPTION_PREFIX + "grpc.ssl.client.keystore.location";
    public static final String GRPC_SSL_CLIENT_TRUSTSTORE_LOCATION  = PROTOCOL_OPTION_PREFIX + "grpc.ssl.client.truststore.location";
}
