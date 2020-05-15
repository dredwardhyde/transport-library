package com.jaffa.rpc.lib.rabbitmq.receivers;

import com.jaffa.rpc.lib.JaffaService;
import com.jaffa.rpc.lib.common.FinalizationWorker;
import com.jaffa.rpc.lib.entities.CallbackContainer;
import com.jaffa.rpc.lib.entities.Command;
import com.jaffa.rpc.lib.entities.ExceptionHolder;
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException;
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException;
import com.jaffa.rpc.lib.rabbitmq.RabbitMQRequestSender;
import com.jaffa.rpc.lib.serialization.Serializer;
import com.jaffa.rpc.lib.ui.AdminServer;
import com.rabbitmq.client.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.Connection;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
                    if (properties.getHeaders() == null) return;
                    Object type = properties.getHeaders().get("communication-type");
                    if (type == null || !"async".equals(String.valueOf(type))) return;
                    try {
                        CallbackContainer callbackContainer = Serializer.getCtx().deserialize(body, CallbackContainer.class);
                        Class<?> callbackClass = Class.forName(callbackContainer.getListener());
                        Command command = FinalizationWorker.getEventsToConsume().remove(callbackContainer.getKey());
                        if (command != null) {
                            if (callbackContainer.getResult() instanceof ExceptionHolder) {
                                java.lang.reflect.Method method = callbackClass.getMethod("onError", String.class, Throwable.class);
                                method.invoke(callbackClass.getDeclaredConstructor().newInstance(), callbackContainer.getKey(), new JaffaRpcExecutionException(((ExceptionHolder) callbackContainer.getResult()).getStackTrace()));
                            } else if (callbackContainer.getResult() instanceof Throwable) {
                                if (Serializer.getCurrentSerializationProtocol().equals("java")) {
                                    Method method = callbackClass.getMethod("onError", String.class, Throwable.class);
                                    method.invoke(callbackClass.getDeclaredConstructor().newInstance(), callbackContainer.getKey(), new JaffaRpcExecutionException((Throwable) callbackContainer.getResult()));
                                } else {
                                    throw new JaffaRpcSystemException("Same serialization protocol must be enabled cluster-wide!");
                                }
                            } else {
                                Method method = callbackClass.getMethod("onSuccess", String.class, Class.forName(callbackContainer.getResultClass()));
                                if (Class.forName(callbackContainer.getResultClass()).equals(Void.class)) {
                                    method.invoke(callbackClass.getDeclaredConstructor().newInstance(), callbackContainer.getKey(), null);
                                } else
                                    method.invoke(callbackClass.getDeclaredConstructor().newInstance(), callbackContainer.getKey(), callbackContainer.getResult());
                            }
                            AdminServer.addMetric(command);
                            clientChannel.basicAck(envelope.getDeliveryTag(), false);
                        } else {
                            log.warn("Response {} already expired", callbackContainer.getKey());
                        }
                    } catch (IOException ioException) {
                        log.error("General RabbitMQ exception", ioException);
                        throw new JaffaRpcSystemException(ioException);
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException | ClassNotFoundException | NoSuchMethodException callbackExecutionException) {
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
        }
        connection.close();
    }
}
