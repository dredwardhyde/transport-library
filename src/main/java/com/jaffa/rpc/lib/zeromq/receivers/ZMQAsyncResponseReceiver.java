package com.jaffa.rpc.lib.zeromq.receivers;

import com.jaffa.rpc.lib.common.OptionConstants;
import com.jaffa.rpc.lib.common.RequestInvocationHelper;
import com.jaffa.rpc.lib.entities.CallbackContainer;
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException;
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException;
import com.jaffa.rpc.lib.serialization.Serializer;
import com.jaffa.rpc.lib.zeromq.CurveUtils;
import com.jaffa.rpc.lib.zookeeper.Utils;
import lombok.extern.slf4j.Slf4j;
import org.zeromq.*;
import zmq.ZError;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.UnknownHostException;

@Slf4j
public class ZMQAsyncResponseReceiver implements Runnable, Closeable {

    private ZContext context;
    private ZAuth auth;

    @Override
    public void run() {
        ZMQ.Socket socket;
        try {
            context = new ZContext(10);
            context.setLinger(0);
            if (Boolean.parseBoolean(System.getProperty(OptionConstants.ZMQ_CURVE_ENABLED, String.valueOf(false)))) {
                auth = new ZAuth(context);
                auth.setVerbose(true);
                auth.configureCurve(Utils.getRequiredOption(OptionConstants.ZMQ_CLIENT_DIR));
            }
            socket = context.createSocket(SocketType.REP);
            CurveUtils.makeSocketSecure(socket);
            socket.bind("tcp://" + Utils.getZeroMQCallbackBindAddress());
        } catch (UnknownHostException zmqStartupException) {
            log.error("Error during ZeroMQ response receiver startup:", zmqStartupException);
            throw new JaffaRpcSystemException(zmqStartupException);
        }
        while (!Thread.currentThread().isInterrupted()) {
            try {
                byte[] bytes = socket.recv();
                CallbackContainer callbackContainer = Serializer.getCurrent().deserialize(bytes, CallbackContainer.class);
                RequestInvocationHelper.processCallbackContainer(callbackContainer);
            } catch (ZMQException | ZError.IOException recvTerminationException) {
                if (!recvTerminationException.getMessage().contains("Errno 4") && !recvTerminationException.getMessage().contains("156384765")) {
                    log.error("General ZMQ exception", recvTerminationException);
                    throw new JaffaRpcSystemException(recvTerminationException);
                }
            } catch (IllegalAccessException | InvocationTargetException | ClassNotFoundException | NoSuchMethodException callbackExecutionException) {
                log.error("ZMQ callback execution exception", callbackExecutionException);
                throw new JaffaRpcExecutionException(callbackExecutionException);
            }
        }
        log.info("{} terminated", this.getClass().getSimpleName());
    }

    @Override
    public void close() {
        if (Boolean.parseBoolean(System.getProperty(OptionConstants.ZMQ_CURVE_ENABLED, String.valueOf(false)))) {
            try {
                auth.close();
            } catch (IOException ioException) {
                log.error("Error while closing ZeroMQ context", ioException);
            }
        } else {
            context.close();
        }
    }
}
