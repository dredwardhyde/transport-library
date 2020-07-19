package com.jaffa.rpc.lib.zeromq;

import com.jaffa.rpc.lib.common.Options;
import com.jaffa.rpc.lib.entities.Protocol;
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException;
import com.jaffa.rpc.lib.request.Sender;
import com.jaffa.rpc.lib.zookeeper.Utils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.Objects;

@Slf4j
public class ZeroMqRequestSender extends Sender {

    public static final ZContext context = new ZContext(10);

    public static void addCurveKeysToSocket(ZMQ.Socket socket, String moduleId) {
        if (Boolean.parseBoolean(System.getProperty(Options.ZMQ_CURVE_ENABLED, String.valueOf(false)))) {
            socket.setCurvePublicKey(CurveUtils.getServerPublicKey().getBytes());
            socket.setCurveSecretKey(CurveUtils.getServerSecretKey().getBytes());
            String clientPublicKey = CurveUtils.getClientPublicKey(moduleId);
            if (Objects.isNull(clientPublicKey))
                throw new JaffaRpcExecutionException("No Curve client key was provided for jaffa.rpc.module.id " + moduleId);
            socket.setCurveServerKey(clientPublicKey.getBytes());
        }
    }

    @Override
    protected byte[] executeSync(byte[] message) {
        long start = System.currentTimeMillis();
        byte[] response;
        try (ZMQ.Socket socket = context.createSocket(SocketType.REQ)) {
            Pair<String, String> hostAndModuleId = Utils.getHostForService(command.getServiceClass(), moduleId, Protocol.ZMQ);
            addCurveKeysToSocket(socket, hostAndModuleId.getRight());
            socket.connect("tcp://" + hostAndModuleId.getLeft());
            socket.send(message, 0);
            socket.setReceiveTimeOut((int) (this.timeout == -1 ? 1000 * 60 * 60 : this.timeout));
            response = socket.recv(0);
        }
        log.trace(">>>>>> Executed sync request {} in {} ms", command.getRqUid(), System.currentTimeMillis() - start);
        return response;
    }

    @Override
    protected void executeAsync(byte[] message) {
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
