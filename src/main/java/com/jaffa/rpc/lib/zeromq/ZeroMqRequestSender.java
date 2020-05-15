package com.jaffa.rpc.lib.zeromq;

import com.jaffa.rpc.lib.entities.Protocol;
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException;
import com.jaffa.rpc.lib.request.Sender;
import com.jaffa.rpc.lib.zookeeper.Utils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

@Slf4j
public class ZeroMqRequestSender extends Sender {

    public static final ZContext context = new ZContext(10);

    public static void addCurveKeysToSocket(ZMQ.Socket socket, String moduleId) {
        if (Boolean.parseBoolean(System.getProperty("jaffa.rpc.protocol.zmq.curve.enabled", String.valueOf(false)))) {
            socket.setCurvePublicKey(CurveUtils.getServerPublicKey().getBytes());
            socket.setCurveSecretKey(CurveUtils.getServerSecretKey().getBytes());
            String clientPublicKey = CurveUtils.getClientPublicKey(moduleId);
            if (clientPublicKey == null)
                throw new JaffaRpcExecutionException("No Curve client key was provided for jaffa.rpc.module.id " + moduleId);
            socket.setCurveServerKey(clientPublicKey.getBytes());
        }
    }

    @Override
    public byte[] executeSync(byte[] message) {
        long start = System.currentTimeMillis();
        byte[] response;
        try (ZMQ.Socket socket = context.createSocket(SocketType.REQ)) {
            Pair<String, String> hostAndModuleId = Utils.getHostForService(command.getServiceClass(), moduleId, Protocol.ZMQ);
            addCurveKeysToSocket(socket, hostAndModuleId.getRight());
            socket.connect("tcp://" + hostAndModuleId.getLeft());
            socket.send(message, 0);
            if (timeout != -1) {
                socket.setReceiveTimeOut((int) timeout);
            }
            response = socket.recv(0);
        }
        log.info(">>>>>> Executed sync request {} in {} ms", command.getRqUid(), System.currentTimeMillis() - start);
        return response;
    }

    @Override
    public void executeAsync(byte[] message) {
        long start = System.currentTimeMillis();
        try (ZMQ.Socket socket = context.createSocket(SocketType.REQ)) {
            Pair<String, String> hostAndModuleId = Utils.getHostForService(command.getServiceClass(), moduleId, Protocol.ZMQ);
            addCurveKeysToSocket(socket, hostAndModuleId.getRight());
            socket.connect("tcp://" + hostAndModuleId.getLeft());
            socket.send(message, 0);
            socket.recv(0);
        }
        log.info(">>>>>> Executed async request {} in {} ms", command.getRqUid(), System.currentTimeMillis() - start);
    }
}
