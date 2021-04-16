package com.jaffa.rpc.lib.rabbitmq.receivers

import com.jaffa.rpc.lib.JaffaService
import com.jaffa.rpc.lib.common.RequestInvocationHelper
import com.jaffa.rpc.lib.entities.CallbackContainer
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException
import com.jaffa.rpc.lib.rabbitmq.RabbitMQRequestSender
import com.jaffa.rpc.lib.serialization.Serializer
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.connection.Connection
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeoutException

class RabbitMQAsyncResponseReceiver : Runnable, Closeable {

    private val log = LoggerFactory.getLogger(RabbitMQAsyncResponseReceiver::class.java)

    private var connection: Connection? = null

    private var clientChannel: Channel? = null

    override fun run() {
        try {
            connection = JaffaService.connectionFactory?.createConnection()
            clientChannel = connection?.createChannel(false)
            clientChannel?.queueBind(RabbitMQRequestSender.CLIENT_ASYNC_NAME, RabbitMQRequestSender.EXCHANGE_NAME, RabbitMQRequestSender.CLIENT_ASYNC_NAME)
            val consumer: Consumer = object : DefaultConsumer(clientChannel) {
                override fun handleDelivery(consumerTag: String, envelope: Envelope, properties: AMQP.BasicProperties, body: ByteArray) {
                    if (properties.headers == null) return
                    val type = properties.headers["communication-type"]
                    if (type == null || "async" != type.toString()) return
                    try {
                        val callbackContainer = Serializer.current.deserialize(body, CallbackContainer::class.java)
                        if (callbackContainer?.let { RequestInvocationHelper.processCallbackContainer(it) } == true)
                            clientChannel?.basicAck(envelope.deliveryTag, false)
                    } catch (ioException: IOException) {
                        log.error("General RabbitMQ exception", ioException)
                    } catch (callbackExecutionException: Exception) {
                        log.error("RabbitMQ callback execution exception", callbackExecutionException)
                    }
                }
            }
            clientChannel?.basicConsume(RabbitMQRequestSender.CLIENT_ASYNC_NAME, false, consumer)
        } catch (ioException: Exception) {
            log.error("Error during RabbitMQ response receiver startup:", ioException)
            throw JaffaRpcSystemException(ioException)
        }
    }

    override fun close() {
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