package com.jaffa.rpc.test.zeromq;

import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException;
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException;
import com.jaffa.rpc.lib.zeromq.CurveUtils;
import com.jaffa.rpc.lib.zeromq.ZeroMqRequestSender;
import com.jaffa.rpc.lib.zeromq.receivers.ZMQAsyncAndSyncRequestReceiver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Slf4j
@SuppressWarnings({"squid:S5786"})
public class ZeroMQSecondTest {

    @Test
    public void stage1() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        System.setProperty("jaffa.rpc.protocol.zmq.curve.enabled", "true");
        final ZContext context = new ZContext();
        final ZMQ.Socket socket = context.createSocket(SocketType.REP);
        socket.bind("tcp://localhost:5555");
        System.setProperty("jaffa.rpc.protocol.zmq.server.keys", "src/test/resources/curve/curve_secret/testcert.pub");
        CurveUtils.readServerKeys();
        CurveUtils.makeSocketSecure(socket);
        try {
            ZeroMqRequestSender.addCurveKeysToSocket(socket, "xxx");
            Assertions.fail();
        } catch (JaffaRpcExecutionException jaffaRpcExecutionException) {
            log.error("No keys were found, just as expected");
        }

        ZMQAsyncAndSyncRequestReceiver.destroySocketAndContext(context, socket, ZeroMQSecondTest.class);

        Method getPublicKeyFromPath = CurveUtils.class.getDeclaredMethod("getPublicKeyFromPath", String.class);
        getPublicKeyFromPath.setAccessible(true);

        String publicKey = (String) getPublicKeyFromPath.invoke(CurveUtils.class, new Object[]{"xxx"});
        Assertions.assertNull(publicKey);

        Method getSecretKeyFromPath = CurveUtils.class.getDeclaredMethod("getSecretKeyFromPath", String.class);
        getSecretKeyFromPath.setAccessible(true);

        String secretKey = (String) getSecretKeyFromPath.invoke(CurveUtils.class, new Object[]{"xxx"});
        Assertions.assertNull(secretKey);
        System.setProperty("jaffa.rpc.protocol.zmq.curve.enabled", "false");
        try {
            new ZMQAsyncAndSyncRequestReceiver();
            Assertions.fail();
        } catch (JaffaRpcSystemException jaffaRpcSystemException) {
            //No-op
        }
        try {
            ZMQAsyncAndSyncRequestReceiver.checkZMQExceptionAndThrow(new RuntimeException("xxx"));
            Assertions.fail();
        } catch (JaffaRpcSystemException jaffaRpcSystemException) {
            //No-op
        }
    }
}
