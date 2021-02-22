package com.jaffa.rpc.lib.common

object OptionConstants {
    const val ILLEGAL_ARGS_MESSAGE = "Arguments can not be null!"
    const val NEGATIVE_TIMEOUT_MESSAGE = "Request timeout can not be negative!"

    @kotlin.jvm.JvmField
    var MODULE_ID: String? = null
    var ZK_TEST_MODE: String? = null
    var PROTOCOL: String? = null
    var SERIALIZER: String? = null
    var ZOOKEEPER_CONNECTION: String? = null
    var ZOOKEEPER_CLIENT_SECURE: String? = null
    var ZOOKEEPER_CLIENT_CONTEXT: String? = null
    var ZOOKEEPER_SSL_KEYSTORE_LOCATION: String? = null
    var ZOOKEEPER_SSL_KEYSTORE_PASSWORD: String? = null
    var ZOOKEEPER_SSL_TRUSTSTORE_LOCATION: String? = null
    var ZOOKEEPER_SSL_TRUSTSTORE_PASSWORD: String? = null
    var ADMIN_SSL_KEYSTORE_LOCATION: String? = null
    var ADMIN_SSL_KEYSTORE_PASSWORD: String? = null
    var ADMIN_SSL_TRUSTSTORE_LOCATION: String? = null
    var ADMIN_SSL_TRUSTSTORE_PASSWORD: String? = null
    var ADMIN_USE_HTTPS: String? = null
    var PROTOCOL_OPTION_PREFIX: String? = null
    var SERVICE_PORT_OPTION_SUFFIX: String? = null
    var CALLBACK_PORT_OPTION_SUFFIX: String? = null
    var USE_HTTPS: String? = null
    var HTTP_SSL_SERVER_KEYSTORE_LOCATION: String? = null
    var HTTP_SSL_SERVER_KEYSTORE_PASSWORD: String? = null
    var HTTP_SSL_SERVER_TRUSTSTORE_LOCATION: String? = null
    var HTTP_SSL_SERVER_TRUSTSTORE_PASSWORD: String? = null
    var HTTP_SSL_CLIENT_KEYSTORE_LOCATION: String? = null
    var HTTP_SSL_CLIENT_KEYSTORE_PASSWORD: String? = null
    var HTTP_SSL_CLIENT_TRUSTSTORE_LOCATION: String? = null
    var HTTP_SSL_CLIENT_TRUSTSTORE_PASSWORD: String? = null
    var KAFKA_BOOTSTRAP_SERVERS: String? = null
    var KAFKA_USE_SSL: String? = null
    var KAFKA_SSL_TRUSTSTORE_LOCATION: String? = null
    var KAFKA_SSL_TRUSTSTORE_PASSWORD: String? = null
    var KAFKA_SSL_KEYSTORE_LOCATION: String? = null
    var KAFKA_SSL_KEYSTORE_PASSWORD: String? = null
    var KAFKA_SSL_KEY_PASSWORD: String? = null
    var ZMQ_CURVE_ENABLED: String? = null
    var ZMQ_CLIENT_DIR: String? = null
    var ZMQ_SERVER_KEYS: String? = null
    var ZMQ_CLIENT_KEY: String? = null

    @kotlin.jvm.JvmField
    var RABBIT_LOGIN: String? = null
    var RABBIT_HOST: String? = null
    var RABBIT_PORT: String? = null

    @kotlin.jvm.JvmField
    var RABBIT_PASSWORD: String? = null
    var RABBIT_USE_SSL: String? = null
    var RABBIT_SSL_KEYSTORE_LOCATION: String? = null
    var RABBIT_SSL_KEYSTORE_PASSWORD: String? = null
    var RABBIT_SSL_TRUSTSTORE_LOCATION: String? = null
    var RABBIT_SSL_TRUSTSTORE_PASSWORD: String? = null
    var GRPC_USE_SSL: String? = null
    var GRPC_SSL_SERVER_KEY_LOCATION: String? = null
    var GRPC_SSL_SERVER_STORE_LOCATION: String? = null
    var GRPC_SSL_CLIENT_KEY_LOCATION: String? = null
    var GRPC_SSL_CLIENT_KEYSTORE_LOCATION: String? = null
    var GRPC_SSL_CLIENT_TRUSTSTORE_LOCATION: String? = null

    @kotlin.jvm.JvmStatic
    fun setModuleId(moduleId: String?) {
        MODULE_ID = moduleId
        ZK_TEST_MODE = "jaffa.rpc.$MODULE_ID.test.mode"
        PROTOCOL = "jaffa.rpc.$MODULE_ID.protocol"
        SERIALIZER = "jaffa.rpc.$MODULE_ID.serializer"
        ZOOKEEPER_CONNECTION = "jaffa.rpc.$MODULE_ID.zookeeper.connection"
        ZOOKEEPER_CLIENT_SECURE = "jaffa.rpc." + MODULE_ID + ".zookeeper.client.secure"
        ZOOKEEPER_CLIENT_CONTEXT = "jaffa.rpc." + MODULE_ID + ".zookeeper.clientCnxnSocket"
        ZOOKEEPER_SSL_KEYSTORE_LOCATION = "jaffa.rpc.$MODULE_ID.zookeeper.ssl.keyStore.location"
        ZOOKEEPER_SSL_KEYSTORE_PASSWORD = "jaffa.rpc." + MODULE_ID + ".zookeeper.ssl.keyStore.password"
        ZOOKEEPER_SSL_TRUSTSTORE_LOCATION = "jaffa.rpc." + MODULE_ID + ".zookeeper.ssl.trustStore.location"
        ZOOKEEPER_SSL_TRUSTSTORE_PASSWORD = "jaffa.rpc." + MODULE_ID + ".zookeeper.ssl.trustStore.password"
        ADMIN_SSL_KEYSTORE_LOCATION = "jaffa.rpc." + MODULE_ID + ".admin.ssl.keystore.location"
        ADMIN_SSL_KEYSTORE_PASSWORD = "jaffa.rpc.$MODULE_ID.admin.ssl.keystore.password"
        ADMIN_SSL_TRUSTSTORE_LOCATION = "jaffa.rpc.$MODULE_ID.admin.ssl.truststore.location"
        ADMIN_SSL_TRUSTSTORE_PASSWORD = "jaffa.rpc.$MODULE_ID.admin.ssl.truststore.password"
        ADMIN_USE_HTTPS = "jaffa.$MODULE_ID.admin.use.https"
        PROTOCOL_OPTION_PREFIX = "$PROTOCOL."
        SERVICE_PORT_OPTION_SUFFIX = ".service.port"
        CALLBACK_PORT_OPTION_SUFFIX = ".callback.port"
        USE_HTTPS = PROTOCOL_OPTION_PREFIX + "use.https"
        HTTP_SSL_SERVER_KEYSTORE_LOCATION = PROTOCOL_OPTION_PREFIX + "http.ssl.server.keystore.location"
        HTTP_SSL_SERVER_KEYSTORE_PASSWORD = PROTOCOL_OPTION_PREFIX + "http.ssl.server.keystore.password"
        HTTP_SSL_SERVER_TRUSTSTORE_LOCATION = PROTOCOL_OPTION_PREFIX + "http.ssl.server.truststore.location"
        HTTP_SSL_SERVER_TRUSTSTORE_PASSWORD = PROTOCOL_OPTION_PREFIX + "http.ssl.server.truststore.password"
        HTTP_SSL_CLIENT_KEYSTORE_LOCATION = PROTOCOL_OPTION_PREFIX + "http.ssl.client.keystore.location"
        HTTP_SSL_CLIENT_KEYSTORE_PASSWORD = PROTOCOL_OPTION_PREFIX + "http.ssl.client.keystore.password"
        HTTP_SSL_CLIENT_TRUSTSTORE_LOCATION = PROTOCOL_OPTION_PREFIX + "http.ssl.client.truststore.location"
        HTTP_SSL_CLIENT_TRUSTSTORE_PASSWORD = PROTOCOL_OPTION_PREFIX + "http.ssl.client.truststore.password"
        KAFKA_BOOTSTRAP_SERVERS = PROTOCOL_OPTION_PREFIX + "kafka.bootstrap.servers"
        KAFKA_USE_SSL = PROTOCOL_OPTION_PREFIX + "kafka.use.ssl"
        KAFKA_SSL_TRUSTSTORE_LOCATION = PROTOCOL_OPTION_PREFIX + "kafka.ssl.truststore.location"
        KAFKA_SSL_TRUSTSTORE_PASSWORD = PROTOCOL_OPTION_PREFIX + "kafka.ssl.truststore.password"
        KAFKA_SSL_KEYSTORE_LOCATION = PROTOCOL_OPTION_PREFIX + "kafka.ssl.keystore.location"
        KAFKA_SSL_KEYSTORE_PASSWORD = PROTOCOL_OPTION_PREFIX + "kafka.ssl.keystore.password"
        KAFKA_SSL_KEY_PASSWORD = PROTOCOL_OPTION_PREFIX + "kafka.ssl.key.password"
        ZMQ_CURVE_ENABLED = PROTOCOL_OPTION_PREFIX + "zmq.curve.enabled"
        ZMQ_CLIENT_DIR = PROTOCOL_OPTION_PREFIX + "zmq.client.dir"
        ZMQ_SERVER_KEYS = PROTOCOL_OPTION_PREFIX + "zmq.server.keys"
        ZMQ_CLIENT_KEY = PROTOCOL_OPTION_PREFIX + "zmq.client.key."
        RABBIT_LOGIN = PROTOCOL_OPTION_PREFIX + "rabbit.login"
        RABBIT_HOST = PROTOCOL_OPTION_PREFIX + "rabbit.host"
        RABBIT_PORT = PROTOCOL_OPTION_PREFIX + "rabbit.port"
        RABBIT_PASSWORD = PROTOCOL_OPTION_PREFIX + "rabbit.password"
        RABBIT_USE_SSL = PROTOCOL_OPTION_PREFIX + "rabbit.use.ssl"
        RABBIT_SSL_KEYSTORE_LOCATION = PROTOCOL_OPTION_PREFIX + "rabbit.ssl.keystore.location"
        RABBIT_SSL_KEYSTORE_PASSWORD = PROTOCOL_OPTION_PREFIX + "rabbit.ssl.keystore.password"
        RABBIT_SSL_TRUSTSTORE_LOCATION = PROTOCOL_OPTION_PREFIX + "rabbit.ssl.truststore.location"
        RABBIT_SSL_TRUSTSTORE_PASSWORD = PROTOCOL_OPTION_PREFIX + "rabbit.ssl.truststore.password"
        GRPC_USE_SSL = PROTOCOL_OPTION_PREFIX + "grpc.use.ssl"
        GRPC_SSL_SERVER_KEY_LOCATION = PROTOCOL_OPTION_PREFIX + "grpc.ssl.server.key.location"
        GRPC_SSL_SERVER_STORE_LOCATION = PROTOCOL_OPTION_PREFIX + "grpc.ssl.server.store.location"
        GRPC_SSL_CLIENT_KEY_LOCATION = PROTOCOL_OPTION_PREFIX + "grpc.ssl.client.key.location"
        GRPC_SSL_CLIENT_KEYSTORE_LOCATION = PROTOCOL_OPTION_PREFIX + "grpc.ssl.client.keystore.location"
        GRPC_SSL_CLIENT_TRUSTSTORE_LOCATION = PROTOCOL_OPTION_PREFIX + "grpc.ssl.client.truststore.location"
    }
}