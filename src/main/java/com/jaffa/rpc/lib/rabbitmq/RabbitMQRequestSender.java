package com.jaffa.rpc.lib.rabbitmq;

import com.jaffa.rpc.lib.JaffaService;
import com.jaffa.rpc.lib.common.Options;
import com.jaffa.rpc.lib.entities.Protocol;
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException;
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException;
import com.jaffa.rpc.lib.request.Sender;
import com.jaffa.rpc.lib.zookeeper.Utils;
import com.rabbitmq.client.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.Connection;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class RabbitMQRequestSender extends Sender {

    private static final String NAME_PREFIX = Utils.getRequiredOption(Options.MODULE_ID);
    public static final String EXCHANGE_NAME = NAME_PREFIX;
    public static final String CLIENT_SYNC_NAME = NAME_PREFIX + "-client-sync";
    public static final String CLIENT_ASYNC_NAME = NAME_PREFIX + "-client-async";
    public static final String SERVER = NAME_PREFIX + "-server";
    private static final Map<String, Callback> requests = new ConcurrentHashMap<>();
    private static Connection connection;
    private static Channel clientChannel;

    public static void init() {
        try {
            connection = JaffaService.getConnectionFactory().createConnection();
            clientChannel = connection.createChannel(false);
            clientChannel.queueBind(CLIENT_SYNC_NAME, EXCHANGE_NAME, CLIENT_SYNC_NAME);
            Consumer consumer = new DefaultConsumer(clientChannel) {
                @Override
                public void handleDelivery(
                        String consumerTag,
                        Envelope envelope,
                        AMQP.BasicProperties properties,
                        final byte[] body) throws IOException {
                    if (Objects.nonNull(properties) && Objects.nonNull(properties.getCorrelationId())) {
                        Callback callback = requests.remove(properties.getCorrelationId());
                        if (Objects.nonNull(callback)) {
                            callback.call(body);
                            clientChannel.basicAck(envelope.getDeliveryTag(), false);
                        }
                    }
                }
            };
            clientChannel.basicConsume(CLIENT_SYNC_NAME, false, consumer);
        } catch (AmqpException | IOException ioException) {
            log.error("Error during RabbitMQ response receiver startup:", ioException);
            throw new JaffaRpcSystemException(ioException);
        }
    }

    public static void close() {
        try {
            if (Objects.nonNull(clientChannel)) clientChannel.close();
        } catch (IOException | TimeoutException ignore) {
            // No-op
        }
        if (Objects.nonNull(connection)) connection.close();
    }

    @Override
    @SuppressWarnings("squid:S1168")
    protected byte[] executeSync(byte[] message) {
        try {
            final AtomicReference<byte[]> atomicReference = new AtomicReference<>();
            requests.put(command.getRqUid(), atomicReference::set);
            sendSync(message);
            long start = System.currentTimeMillis();
            while (!((timeout != -1 && System.currentTimeMillis() - start > timeout) || (System.currentTimeMillis() - start > (1000 * 60 * 60)))) {
                byte[] result = atomicReference.get();
                if (Objects.nonNull(result)) {
                    return result;
                }
            }
            requests.remove(command.getRqUid());
        } catch (IOException ioException) {
            log.error("Error while sending sync RabbitMQ request", ioException);
            throw new JaffaRpcExecutionException(ioException);
        }
        return null;
    }

    private void sendSync(byte[] message) throws IOException {
        String targetModuleId;
        if (StringUtils.isNotBlank(moduleId)) {
            targetModuleId = moduleId;
        } else {
            targetModuleId = Utils.getModuleForService(Utils.getServiceInterfaceNameFromClient(command.getServiceClass()), Protocol.RABBIT);
        }
        clientChannel.basicPublish(targetModuleId, targetModuleId + "-server", null, message);
    }

    @Override
    protected void executeAsync(byte[] message) {
        try {
            sendSync(message);
        } catch (IOException e) {
            log.error("Error while sending async RabbitMQ request", e);
            throw new JaffaRpcExecutionException(e);
        }
    }

    private interface Callback {
        void call(byte[] body);
    }
}
