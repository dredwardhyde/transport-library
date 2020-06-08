package com.jaffa.rpc.lib.zeromq.receivers;

import com.jaffa.rpc.lib.common.RequestInvoker;
import com.jaffa.rpc.lib.entities.Command;
import com.jaffa.rpc.lib.entities.RequestContext;
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
            context = new ZContext(10);
            context.setLinger(0);
            if (Boolean.parseBoolean(System.getProperty("jaffa.rpc.protocol.zmq.curve.enabled", String.valueOf(false)))) {
                auth = new ZAuth(context);
                auth.setVerbose(true);
                auth.configureCurve(Utils.getRequiredOption("jaffa.rpc.protocol.zmq.client.dir"));
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
                final Command command = Serializer.ctx.deserialize(bytes, Command.class);
                if (command.getCallbackKey() != null && command.getCallbackClass() != null) {
                    socket.send("OK");
                }
                if (command.getCallbackKey() != null && command.getCallbackClass() != null) {
                    Runnable runnable = () -> {
                        try {
                            RequestContext.setMetaData(command);
                            Object result = RequestInvoker.invoke(command);
                            RequestContext.removeMetaData();
                            byte[] serializedResponse = Serializer.ctx.serialize(RequestInvoker.constructCallbackContainer(command, result));
                            ZMQ.Socket socketAsync = context.createSocket(SocketType.REQ);
                            ZeroMqRequestSender.addCurveKeysToSocket(socketAsync, command.getSourceModuleId());
                            socketAsync.connect("tcp://" + command.getCallBackZMQ());
                            socketAsync.send(serializedResponse);
                            socketAsync.close();
                        } catch (ClassNotFoundException | NoSuchMethodException e) {
                            log.error("Error while receiving async request", e);
                            throw new JaffaRpcExecutionException(e);
                        }
                    };
                    service.execute(runnable);
                } else {
                    RequestContext.setMetaData(command);
                    Object result = RequestInvoker.invoke(command);
                    RequestContext.removeMetaData();
                    byte[] serializedResponse = Serializer.ctx.serializeWithClass(RequestInvoker.getResult(result));
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
        if (Boolean.parseBoolean(System.getProperty("jaffa.rpc.protocol.zmq.curve.enabled", String.valueOf(false)))) {
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
