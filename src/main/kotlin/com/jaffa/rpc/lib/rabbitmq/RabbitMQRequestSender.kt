package com.jaffa.rpc.lib.rabbitmq

import com.jaffa.rpc.lib.JaffaService
import com.jaffa.rpc.lib.common.OptionConstants
import com.jaffa.rpc.lib.entities.Protocol
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException
import com.jaffa.rpc.lib.exception.JaffaRpcNoRouteException
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException
import com.jaffa.rpc.lib.request.Sender
import com.jaffa.rpc.lib.zookeeper.Utils
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpException
import org.springframework.amqp.rabbit.connection.Connection
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

class RabbitMQRequestSender : Sender() {

    public override fun executeSync(message: ByteArray?): ByteArray? {
        try {
            val atomicReference = AtomicReference<ByteArray>()
            requests[command?.rqUid] = object : Callback {
                override fun call(body: ByteArray?) {
                    atomicReference.set(body)
                }
            }
            sendSync(message)
            val start = System.currentTimeMillis()
            while (!(timeout != -1L && System.currentTimeMillis() - start > timeout || System.currentTimeMillis() - start > 1000 * 60 * 60)) {
                val result = atomicReference.get()
                if (result != null) {
                    return result
                }
            }
            requests.remove(command?.rqUid)
        } catch (jaffaRpcNoRouteException: JaffaRpcNoRouteException) {
            throw jaffaRpcNoRouteException
        } catch (exception: Exception) {
            log.error("Error while sending sync RabbitMQ request", exception)
            throw JaffaRpcExecutionException(exception)
        }
        return null
    }

    @Throws(IOException::class)
    private fun sendSync(message: ByteArray?) {
        val targetModuleId: String?
        if (StringUtils.isNotBlank(moduleId)) {
            targetModuleId = moduleId
            Utils.getHostForService(
                    Utils.getServiceInterfaceNameFromClient(command?.serviceClass),
                    moduleId,
                    Protocol.RABBIT
            )
        } else {
            targetModuleId = Utils.getModuleForService(
                    Utils.getServiceInterfaceNameFromClient(command?.serviceClass),
                    Protocol.RABBIT
            )
        }
        clientChannel?.basicPublish(targetModuleId, "$targetModuleId-server", null, message)
    }

    public override fun executeAsync(message: ByteArray?) {
        try {
            sendSync(message)
        } catch (jaffaRpcNoRouteException: JaffaRpcNoRouteException) {
            throw jaffaRpcNoRouteException
        } catch (e: Exception) {
            log.error("Error while sending async RabbitMQ request", e)
            throw JaffaRpcExecutionException(e)
        }
    }

    private interface Callback {
        fun call(body: ByteArray?)
    }

    companion object {
        private val log = LoggerFactory.getLogger(RabbitMQRequestSender::class.java)
        private val NAME_PREFIX = OptionConstants.MODULE_ID
        val EXCHANGE_NAME = NAME_PREFIX
        val CLIENT_SYNC_NAME = "$NAME_PREFIX-client-sync"
        val CLIENT_ASYNC_NAME = "$NAME_PREFIX-client-async"
        val SERVER = "$NAME_PREFIX-server"
        private val requests: MutableMap<String?, Callback> = ConcurrentHashMap()
        private var connection: Connection? = null
        private var clientChannel: Channel? = null

        @kotlin.jvm.JvmStatic
        fun init() {
            try {
                connection = JaffaService.connectionFactory?.createConnection()
                clientChannel = connection?.createChannel(false)
                clientChannel?.queueBind(CLIENT_SYNC_NAME, EXCHANGE_NAME, CLIENT_SYNC_NAME)
                val consumer: Consumer = object : DefaultConsumer(clientChannel) {
                    @Throws(IOException::class)
                    override fun handleDelivery(
                            consumerTag: String,
                            envelope: Envelope,
                            properties: AMQP.BasicProperties,
                            body: ByteArray
                    ) {
                        if (properties.correlationId != null) {
                            val callback = requests.remove(properties.correlationId)
                            if (callback != null) {
                                callback.call(body)
                                clientChannel?.basicAck(envelope.deliveryTag, false)
                            }
                        }
                    }
                }
                clientChannel?.basicConsume(CLIENT_SYNC_NAME, false, consumer)
            } catch (ioException: AmqpException) {
                log.error("Error during RabbitMQ response receiver startup:", ioException)
                throw JaffaRpcSystemException(ioException)
            } catch (ioException: IOException) {
                log.error("Error during RabbitMQ response receiver startup:", ioException)
                throw JaffaRpcSystemException(ioException)
            }
        }

        fun close() {
            try {
                clientChannel?.close()
            } catch (ignore: IOException) {
                // No-op
            } catch (ignore: TimeoutException) {
                // No-op
            }
            connection?.close()
        }
    }
}