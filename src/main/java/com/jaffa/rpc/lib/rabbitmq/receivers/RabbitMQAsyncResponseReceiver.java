package com.jaffa.rpc.lib.rabbitmq.receivers;

import com.jaffa.rpc.lib.JaffaService;
import com.jaffa.rpc.lib.common.RequestInvoker;
import com.jaffa.rpc.lib.entities.CallbackContainer;
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException;
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException;
import com.jaffa.rpc.lib.rabbitmq.RabbitMQRequestSender;
import com.jaffa.rpc.lib.serialization.Serializer;
import com.rabbitmq.client.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.Connection;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

@Slf4j
public class RabbitMQAsyncResponseReceiver implements Runnable, Closeable {
    private Connection connection;
    private Channel clientChannel;

    @Override
    public void run() {
        try {
            connection = JaffaService.getConnectionFactory().createConnection();
            clientChannel = connection.createChannel(false);
            clientChannel.queueBind(RabbitMQRequestSender.CLIENT_ASYNC_NAME, RabbitMQRequestSender.EXCHANGE_NAME, RabbitMQRequestSender.CLIENT_ASYNC_NAME);
            Consumer consumer = new DefaultConsumer(clientChannel) {
                @Override
                public void handleDelivery(
                        String consumerTag,
                        Envelope envelope,
                        AMQP.BasicProperties properties,
                        final byte[] body) {
                    if (Objects.isNull(properties.getHeaders())) return;
                    Object type = properties.getHeaders().get("communication-type");
                    if (Objects.isNull(type) || !"async".equals(String.valueOf(type))) return;
                    try {
                        CallbackContainer callbackContainer = Serializer.deserialize(body, CallbackContainer.class);
                        if (RequestInvoker.processCallbackContainer(callbackContainer))
                            clientChannel.basicAck(envelope.getDeliveryTag(), false);
                    } catch (IOException ioException) {
                        log.error("General RabbitMQ exception", ioException);
                        throw new JaffaRpcSystemException(ioException);
                    } catch (IllegalAccessException | InvocationTargetException | ClassNotFoundException | NoSuchMethodException callbackExecutionException) {
                        log.error("RabbitMQ callback execution exception", callbackExecutionException);
                        throw new JaffaRpcExecutionException(callbackExecutionException);
                    }
                }
            };
            clientChannel.basicConsume(RabbitMQRequestSender.CLIENT_ASYNC_NAME, false, consumer);
        } catch (AmqpException | IOException ioException) {
            log.error("Error during RabbitMQ response receiver startup:", ioException);
            throw new JaffaRpcSystemException(ioException);
        }
    }

    @Override
    public void close() {
        try {
            clientChannel.close();
        } catch (IOException | TimeoutException ignore) {
            // No-op
        }
        connection.close();
    }
}
