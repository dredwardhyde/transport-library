package com.jaffa.rpc.lib.zeromq.receivers;

import com.jaffa.rpc.lib.common.OptionConstants;
import com.jaffa.rpc.lib.common.RequestInvocationHelper;
import com.jaffa.rpc.lib.entities.CallbackContainer;
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
import java.lang.reflect.InvocationTargetException;
import java.net.UnknownHostException;

@Slf4j
@SuppressWarnings("squid:S1193")
public class ZMQAsyncResponseReceiver implements Runnable, Closeable {

    private final ZContext context;
    private final ZMQ.Socket socket;
    private ZAuth auth;

    public ZMQAsyncResponseReceiver() {
        try {
            context = new ZContext(10);
            context.setLinger(0);
            if (Boolean.parseBoolean(System.getProperty(OptionConstants.ZMQ_CURVE_ENABLED, String.valueOf(false)))) {
                auth = new ZAuth(context);
                auth.configureCurve(Utils.getRequiredOption(OptionConstants.ZMQ_CLIENT_DIR));
            }
            socket = context.createSocket(SocketType.REP);
            CurveUtils.makeSocketSecure(socket);
            socket.bind("tcp://" + Utils.getZeroMQCallbackBindAddress());
        } catch (UnknownHostException zmqStartupException) {
            log.error("Error during ZeroMQ response receiver startup:", zmqStartupException);
            throw new JaffaRpcSystemException(zmqStartupException);
        }
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                byte[] bytes = socket.recv();
                if (bytes != null && bytes.length == 1 && bytes[0] == 7) {
                    ZMQAsyncAndSyncRequestReceiver.destroySocketAndContext(context, socket, ZMQAsyncResponseReceiver.class);
                    break;
                }
                CallbackContainer callbackContainer = Serializer.getCurrent().deserialize(bytes, CallbackContainer.class);
                RequestInvocationHelper.processCallbackContainer(callbackContainer);
            } catch (ZMQException | ZError.IOException recvTerminationException) {
                ZMQAsyncAndSyncRequestReceiver.checkZMQExceptionAndThrow(recvTerminationException);
            } catch (IllegalAccessException | InvocationTargetException | ClassNotFoundException | NoSuchMethodException callbackExecutionException) {
                log.error("ZMQ callback execution exception", callbackExecutionException);
                throw new JaffaRpcExecutionException(callbackExecutionException);
            }
        }
        log.info("{} terminated", this.getClass().getSimpleName());
    }

    @Override
    public void close() throws UnknownHostException {
        sendKillMessageToSocket(Utils.getZeroMQCallbackBindAddress());
        if (Boolean.parseBoolean(System.getProperty(OptionConstants.ZMQ_CURVE_ENABLED, String.valueOf(false)))) {
            try {
                auth.close();
            } catch (IOException ioException) {
                log.error("Error while closing ZeroMQ context", ioException);
            }
        }
        log.info("ZMQAsyncResponseReceiver closed");
    }

    public static void sendKillMessageToSocket(String address) {
        ZContext contextToClose = new ZContext(1);
        try (ZMQ.Socket socketClose = contextToClose.createSocket(SocketType.REQ)) {
            ZeroMqRequestSender.addCurveKeysToSocket(socketClose, Utils.getRequiredOption(OptionConstants.MODULE_ID));
            socketClose.setLinger(0);
            socketClose.connect("tcp://" + address);
            socketClose.send(new byte[]{7}, 0);
            socketClose.setReceiveTimeOut(1);
            socketClose.recv(0);
        } catch (Throwable e) {
            log.error("Error while sending kill message", e);
        }
        contextToClose.close();
        log.info("Kill message sent to {} receiver", address);
    }
}
