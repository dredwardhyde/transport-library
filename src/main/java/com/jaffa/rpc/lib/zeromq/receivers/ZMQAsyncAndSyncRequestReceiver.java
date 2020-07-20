package com.jaffa.rpc.lib.zeromq.receivers;

import com.jaffa.rpc.lib.common.OptionConstants;
import com.jaffa.rpc.lib.common.RequestInvocationHelper;
import com.jaffa.rpc.lib.entities.Command;
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException;
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException;
import com.jaffa.rpc.lib.serialization.Serializer;
import com.jaffa.rpc.lib.zeromq.CurveUtils;
import com.jaffa.rpc.lib.zeromq.ZeroMqRequestSender;
import com.jaffa.rpc.lib.zookeeper.Utils;
import lombok.extern.slf4j.Slf4j;
import org.zeromq.SocketType;
import org.zeromq.ZAuth;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;
import zmq.ZError;

import java.io.Closeable;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@SuppressWarnings("squid:S1193")
public class ZMQAsyncAndSyncRequestReceiver implements Runnable, Closeable {

    private static final ExecutorService service = Executors.newFixedThreadPool(3);

    private final ZContext context;
    private ZAuth auth;
    private final ZMQ.Socket socket;

    public ZMQAsyncAndSyncRequestReceiver() {
        try {
            context = new ZContext(10);
            context.setLinger(0);
            if (Boolean.parseBoolean(System.getProperty(OptionConstants.ZMQ_CURVE_ENABLED, String.valueOf(false)))) {
                auth = new ZAuth(context);
                auth.configureCurve(Utils.getRequiredOption(OptionConstants.ZMQ_CLIENT_DIR));
            }
            socket = context.createSocket(SocketType.REP);
            CurveUtils.makeSocketSecure(socket);
            socket.bind("tcp://" + Utils.getZeroMQBindAddress());
        } catch (UnknownHostException zmqStartupException) {
            log.error("Error during ZeroMQ request receiver startup:", zmqStartupException);
            throw new JaffaRpcSystemException(zmqStartupException);
        }
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                byte[] bytes = socket.recv();
                if (bytes != null && bytes.length == 1 && bytes[0] == 7) {
                    destroySocketAndContext(context, socket, ZMQAsyncAndSyncRequestReceiver.class);
                    break;
                }
                final Command command = Serializer.getCurrent().deserialize(bytes, Command.class);
                if (Objects.nonNull(command.getCallbackKey()) && Objects.nonNull(command.getCallbackClass())) {
                    socket.send("OK");
                    Runnable runnable = () -> {
                        try {
                            Object result = RequestInvocationHelper.invoke(command);
                            byte[] serializedResponse = Serializer.getCurrent().serialize(RequestInvocationHelper.constructCallbackContainer(command, result));
                            ZMQ.Socket socketAsync = context.createSocket(SocketType.REQ);
                            ZeroMqRequestSender.addCurveKeysToSocket(socketAsync, command.getSourceModuleId());
                            socketAsync.connect("tcp://" + command.getCallBackHost());
                            socketAsync.send(serializedResponse);
                            context.destroySocket(socketAsync);
                        } catch (ClassNotFoundException | NoSuchMethodException e) {
                            log.error("Error while receiving async request", e);
                            throw new JaffaRpcExecutionException(e);
                        }
                    };
                    service.execute(runnable);
                } else {
                    Object result = RequestInvocationHelper.invoke(command);
                    byte[] serializedResponse = Serializer.getCurrent().serializeWithClass(RequestInvocationHelper.getResult(result));
                    socket.send(serializedResponse);
                }
            } catch (ZMQException | ZError.IOException recvTerminationException) {
                checkZMQExceptionAndThrow(recvTerminationException);
            }
        }
        log.info("{} terminated", this.getClass().getSimpleName());
    }

    public static void checkZMQExceptionAndThrow(Exception recvTerminationException) {
        if (!recvTerminationException.getMessage().contains("Errno 4") && !recvTerminationException.getMessage().contains("156384765")) {
            log.error("General ZMQ exception", recvTerminationException);
            throw new JaffaRpcSystemException(recvTerminationException);
        }
    }

    public static void destroySocketAndContext(ZContext context, ZMQ.Socket socket, Class<?> source) {
        context.destroySocket(socket);
        log.info("{} socket destroyed", source.getSimpleName());
        context.destroy();
        log.info("{} context destroyed", source.getSimpleName());
    }

    @Override
    public void close() throws UnknownHostException {
        ZMQAsyncResponseReceiver.sendKillMessageToSocket(Utils.getZeroMQBindAddress());
        if (Boolean.parseBoolean(System.getProperty(OptionConstants.ZMQ_CURVE_ENABLED, String.valueOf(false)))) {
            try {
                auth.close();
            } catch (IOException ioException) {
                log.error("Error while closing ZeroMQ context", ioException);
            }
        }
        service.shutdownNow();
        log.info("ZMQAsyncAndSyncRequestReceiver closed");
    }
}
