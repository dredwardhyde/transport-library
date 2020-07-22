package com.jaffa.rpc.test.zeromq;

import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException;
import com.jaffa.rpc.lib.zeromq.CurveUtils;
import com.jaffa.rpc.lib.zeromq.ZeroMqRequestSender;
import com.jaffa.rpc.lib.zeromq.receivers.ZMQAsyncAndSyncRequestReceiver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Slf4j
@SuppressWarnings({"squid:S5786"})
public class ZeroMQSecondTest {

    @Test
    public void stage1() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        System.setProperty("jaffa.rpc.protocol.zmq.curve.enabled", "true");
        final ZContext context = new ZContext();
        final ZMQ.Socket[] socket = new ZMQ.Socket[1];
        final Thread zmqThread = new Thread(() -> {
            socket[0] = context.createSocket(SocketType.REP);
            socket[0].bind("tcp://*:5555");
            System.setProperty("jaffa.rpc.protocol.zmq.server.keys", "src/test/resources/curve/curve_secret/testcert.pub");
            CurveUtils.readServerKeys();
            CurveUtils.makeSocketSecure(socket[0]);
            try {
                ZeroMqRequestSender.addCurveKeysToSocket(socket[0], "xxx");
                Assertions.fail();
            } catch (JaffaRpcExecutionException jaffaRpcExecutionException) {
                log.error("No keys were found, just as expected");
            }
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    socket[0].recv(0);
                } catch (ZMQException e) {
                    if (e.getErrorCode() == ZMQ.Error.ETERM.getCode()) {
                        break;
                    }
                }
            }
            socket[0].setLinger(0);
            socket[0].close();
        });

        zmqThread.start();

        ZMQAsyncAndSyncRequestReceiver.destroySocketAndContext(context, socket[0], ZeroMQSecondTest.class);
        try {
            zmqThread.interrupt();
            zmqThread.join();
        } catch (InterruptedException e) {
            // No-op
        }

        Method getPublicKeyFromPath = CurveUtils.class.getDeclaredMethod("getPublicKeyFromPath", String.class);
        getPublicKeyFromPath.setAccessible(true);

        String publicKey = (String) getPublicKeyFromPath.invoke(CurveUtils.class, new Object[]{"xxx"});
        Assertions.assertNull(publicKey);

        Method getSecretKeyFromPath = CurveUtils.class.getDeclaredMethod("getSecretKeyFromPath", String.class);
        getSecretKeyFromPath.setAccessible(true);

        String secretKey = (String) getSecretKeyFromPath.invoke(CurveUtils.class, new Object[]{"xxx"});
        Assertions.assertNull(secretKey);
    }
}
