package com.jaffa.rpc.lib.rabbitmq.receivers;

import com.jaffa.rpc.lib.JaffaService;
import com.jaffa.rpc.lib.common.RequestInvocationHelper;
import com.jaffa.rpc.lib.entities.CallbackContainer;
import com.jaffa.rpc.lib.entities.Command;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

@Slf4j
public class RabbitMQAsyncAndSyncRequestReceiver implements Runnable, Closeable {

    private static final ExecutorService responseService = Executors.newFixedThreadPool(3);
    private static final ExecutorService requestService = Executors.newFixedThreadPool(3);
    private static final Map<String, Object> asyncHeaders = new HashMap<>();

    static {
        asyncHeaders.put("communication-type", "async");
    }

    private Connection connection;
    private Channel serverChannel;
    private Channel clientChannel;

    @Override
    public void run() {
        try {
            connection = JaffaService.getConnectionFactory().createConnection();
            serverChannel = connection.createChannel(false);
            clientChannel = connection.createChannel(false);
            serverChannel.queueBind(RabbitMQRequestSender.SERVER, RabbitMQRequestSender.EXCHANGE_NAME, RabbitMQRequestSender.SERVER);
            Consumer consumer = new DefaultConsumer(serverChannel) {
                @Override
                public void handleDelivery(
                        String consumerTag,
                        Envelope envelope,
                        AMQP.BasicProperties properties,
                        final byte[] body) {
                    requestService.execute(() -> {
                                try {
                                    final com.jaffa.rpc.lib.entities.Command command = Serializer.getCurrent().deserialize(body, Command.class);
                                    if (Objects.nonNull(command.getCallbackKey()) && Objects.nonNull(command.getCallbackClass())) {
                                        Runnable runnable = () -> {
                                            try {
                                                Object result = RequestInvocationHelper.invoke(command);
                                                CallbackContainer callbackContainer = RequestInvocationHelper.constructCallbackContainer(command, result);
                                                byte[] response = Serializer.getCurrent().serialize(callbackContainer);
                                                AMQP.BasicProperties props = new AMQP.BasicProperties.Builder().headers(asyncHeaders).build();
                                                clientChannel.basicPublish(command.getSourceModuleId(), command.getSourceModuleId() + "-client-async", props, response);
                                                serverChannel.basicAck(envelope.getDeliveryTag(), false);
                                            } catch (ClassNotFoundException | NoSuchMethodException | IOException e) {
                                                log.error("Error while receiving async request", e);
                                                throw new JaffaRpcExecutionException(e);
                                            }
                                        };
                                        responseService.execute(runnable);
                                    } else {
                                        Object result = RequestInvocationHelper.invoke(command);
                                        byte[] response = Serializer.getCurrent().serializeWithClass(RequestInvocationHelper.getResult(result));
                                        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder().correlationId(command.getRqUid()).build();
                                        clientChannel.basicPublish(command.getSourceModuleId(), command.getSourceModuleId() + "-client-sync", props, response);
                                        serverChannel.basicAck(envelope.getDeliveryTag(), false);
                                    }
                                } catch (IOException ioException) {
                                    log.error("General RabbitMQ exception", ioException);
                                    throw new JaffaRpcSystemException(ioException);
                                }
                            }
                    );
                }
            };
            serverChannel.basicConsume(RabbitMQRequestSender.SERVER, false, consumer);
        } catch (AmqpException | IOException amqpException) {
            log.error("Error during RabbitMQ request receiver startup:", amqpException);
            throw new JaffaRpcSystemException(amqpException);
        }
        log.info("{} terminated", this.getClass().getSimpleName());
    }

    @Override
    public void close() {
        try {
            serverChannel.close();
            clientChannel.close();
        } catch (IOException | TimeoutException ignore) {
            // No-op
        }
        connection.close();
        responseService.shutdownNow();
        requestService.shutdownNow();
    }
}
