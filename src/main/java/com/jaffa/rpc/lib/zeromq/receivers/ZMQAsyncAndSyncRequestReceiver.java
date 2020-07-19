package com.jaffa.rpc.lib.zeromq.receivers;

import com.jaffa.rpc.lib.common.Options;
import com.jaffa.rpc.lib.common.RequestInvoker;
import com.jaffa.rpc.lib.entities.Command;
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException;
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException;
import com.jaffa.rpc.lib.serialization.Serializer;
import com.jaffa.rpc.lib.zeromq.CurveUtils;
import com.jaffa.rpc.lib.zeromq.ZeroMqRequestSender;
import com.jaffa.rpc.lib.zookeeper.Utils;
import lombok.extern.slf4j.Slf4j;
import org.zeromq.*;
import zmq.ZError;

import java.io.Closeable;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class ZMQAsyncAndSyncRequestReceiver implements Runnable, Closeable {

    private static final ExecutorService service = Executors.newFixedThreadPool(3);

    private ZContext context;
    private ZAuth auth;

    @Override
    public void run() {
        ZMQ.Socket socket;
        try {
            context = new ZContext(1);
            context.setLinger(0);
            if (Boolean.parseBoolean(System.getProperty(Options.ZMQ_CURVE_ENABLED, String.valueOf(false)))) {
                auth = new ZAuth(context);
                auth.setVerbose(true);
                auth.configureCurve(Utils.getRequiredOption(Options.ZMQ_CLIENT_DIR));
            }
            socket = context.createSocket(SocketType.REP);
            CurveUtils.makeSocketSecure(socket);
            socket.bind("tcp://" + Utils.getZeroMQBindAddress());
        } catch (UnknownHostException zmqStartupException) {
            log.error("Error during ZeroMQ request receiver startup:", zmqStartupException);
            throw new JaffaRpcSystemException(zmqStartupException);
        }
        while (!Thread.currentThread().isInterrupted()) {
            try {
                byte[] bytes = socket.recv();
                final Command command = Serializer.getCurrent().deserialize(bytes, Command.class);
                if (Objects.nonNull(command.getCallbackKey()) && Objects.nonNull(command.getCallbackClass())) {
                    socket.send("OK");
                    Runnable runnable = () -> {
                        try {
                            Object result = RequestInvoker.invoke(command);
                            byte[] serializedResponse = Serializer.getCurrent().serialize(RequestInvoker.constructCallbackContainer(command, result));
                            ZMQ.Socket socketAsync = context.createSocket(SocketType.REQ);
                            ZeroMqRequestSender.addCurveKeysToSocket(socketAsync, command.getSourceModuleId());
                            socketAsync.connect("tcp://" + command.getCallBackHost());
                            socketAsync.send(serializedResponse);
                            socketAsync.close();
                        } catch (ClassNotFoundException | NoSuchMethodException e) {
                            log.error("Error while receiving async request", e);
                            throw new JaffaRpcExecutionException(e);
                        }
                    };
                    service.execute(runnable);
                } else {
                    Object result = RequestInvoker.invoke(command);
                    byte[] serializedResponse = Serializer.getCurrent().serializeWithClass(RequestInvoker.getResult(result));
                    socket.send(serializedResponse);
                }
            } catch (ZMQException | ZError.IOException recvTerminationException) {
                if (!recvTerminationException.getMessage().contains("Errno 4") && !recvTerminationException.getMessage().contains("156384765")) {
                    log.error("General ZMQ exception", recvTerminationException);
                    throw new JaffaRpcSystemException(recvTerminationException);
                }
            }
        }
        log.info("{} terminated", this.getClass().getSimpleName());
    }

    @Override
    public void close() {
        if (Boolean.parseBoolean(System.getProperty(Options.ZMQ_CURVE_ENABLED, String.valueOf(false)))) {
            try {
                auth.close();
            } catch (IOException ioException) {
                log.error("Error while closing ZeroMQ context", ioException);
            }
        } else {
            context.close();
        }
        service.shutdownNow();
    }
}
