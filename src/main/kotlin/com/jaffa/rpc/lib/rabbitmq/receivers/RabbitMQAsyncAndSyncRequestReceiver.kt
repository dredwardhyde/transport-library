package com.jaffa.rpc.lib.rabbitmq.receivers

import com.jaffa.rpc.lib.JaffaService
import com.jaffa.rpc.lib.common.RequestInvocationHelper
import com.jaffa.rpc.lib.entities.Command
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
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException

class RabbitMQAsyncAndSyncRequestReceiver : Runnable, Closeable {

    private val log = LoggerFactory.getLogger(RabbitMQAsyncAndSyncRequestReceiver::class.java)

    companion object {

        private val responseService = Executors.newFixedThreadPool(3)

        private val requestService = Executors.newFixedThreadPool(3)

        private val asyncHeaders: MutableMap<String, Any> = HashMap()

        init {
            asyncHeaders["communication-type"] = "async"
        }
    }

    private var connection: Connection? = null

    private var serverChannel: Channel? = null

    private var clientChannel: Channel? = null

    override fun run() {
        try {
            connection = JaffaService.connectionFactory?.createConnection()
            serverChannel = connection?.createChannel(false)
            clientChannel = connection?.createChannel(false)
            serverChannel?.queueBind(RabbitMQRequestSender.SERVER, RabbitMQRequestSender.EXCHANGE_NAME, RabbitMQRequestSender.SERVER)
            val consumer: Consumer = object : DefaultConsumer(serverChannel) {
                override fun handleDelivery(consumerTag: String, envelope: Envelope, properties: AMQP.BasicProperties, body: ByteArray) {
                    requestService.execute {
                        try {
                            val command = Serializer.current.deserialize(body, Command::class.java)
                            if (command?.callbackKey != null && command.callbackClass != null) {
                                val runnable = Runnable {
                                    try {
                                        clientChannel?.basicPublish(
                                                command.sourceModuleId,
                                                command.sourceModuleId + "-client-async",
                                                AMQP.BasicProperties.Builder().headers(asyncHeaders).build(),
                                                Serializer.current.serialize(RequestInvocationHelper.constructCallbackContainer(command, RequestInvocationHelper.invoke(command)))
                                        )
                                        serverChannel?.basicAck(envelope.deliveryTag, false)
                                    } catch (e: Exception) {
                                        log.error("Error while receiving async request", e)
                                    }
                                }
                                responseService.execute(runnable)
                            } else {
                                if (command != null) {
                                    clientChannel?.basicPublish(
                                            command.sourceModuleId,
                                            command.sourceModuleId + "-client-sync",
                                            AMQP.BasicProperties.Builder().correlationId(command.rqUid).build(),
                                            Serializer.current.serializeWithClass(RequestInvocationHelper.getResult(command.let { RequestInvocationHelper.invoke(it) }))
                                    )
                                }
                                serverChannel?.basicAck(envelope.deliveryTag, false)
                            }
                        } catch (ioException: Exception) {
                            log.error("Error while receiving sync request", ioException)
                        }
                    }
                }
            }
            serverChannel?.basicConsume(RabbitMQRequestSender.SERVER, false, consumer)
        } catch (amqpException: Exception) {
            log.error("Error during RabbitMQ request receiver startup:", amqpException)
            throw JaffaRpcSystemException(amqpException)
        }
        log.info("{} terminated", this.javaClass.simpleName)
    }

    override fun close() {
        try {
            serverChannel?.close()
            clientChannel?.close()
        } catch (ignore: IOException) {
            // No-op
        } catch (ignore: TimeoutException) {
            // No-op
        }
        connection?.close()
        responseService.shutdownNow()
        requestService.shutdownNow()
    }
}