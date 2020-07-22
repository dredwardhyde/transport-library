package com.jaffa.rpc.test.grpc;

import com.jaffa.rpc.lib.entities.Command;
import com.jaffa.rpc.lib.entities.Protocol;
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException;
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionTimeoutException;
import com.jaffa.rpc.lib.exception.JaffaRpcNoRouteException;
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException;
import com.jaffa.rpc.lib.grpc.GrpcRequestSender;
import com.jaffa.rpc.lib.grpc.receivers.GrpcAsyncResponseReceiver;
import com.jaffa.rpc.lib.zookeeper.Utils;
import com.jaffa.rpc.test.ZooKeeperExtension;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Slf4j
@SuppressWarnings({"squid:S5786"})
@ExtendWith({ZooKeeperExtension.class})
public class GrpcSecondTest {

    static {
        System.setProperty("jaffa.rpc.protocol", "grpc");
        System.setProperty("jaffa.rpc.module.id", "test.server");
        System.setProperty("jaffa.rpc.protocol.grpc.use.ssl", "true");
        System.setProperty("jaffa.rpc.protocol.grpc.ssl.server.key.location", "src/test/resources/xxx");
        System.setProperty("jaffa.rpc.protocol.grpc.ssl.server.store.location", "src/test/resources/xxx");
        System.setProperty("jaffa.rpc.protocol.grpc.ssl.client.key.location", "src/test/resources/xxx");
        System.setProperty("jaffa.rpc.protocol.grpc.ssl.client.keystore.location", "src/test/resources/xxx");
        System.setProperty("jaffa.rpc.protocol.grpc.ssl.client.truststore.location", "src/test/resources/xxx");
    }

    @Test
    public void stage1() {
        Utils.connect("localhost:2181");
        GrpcAsyncResponseReceiver grpcAsyncResponseReceiver = new GrpcAsyncResponseReceiver();
        try {
            grpcAsyncResponseReceiver.run();
            Assertions.fail();
        } catch (JaffaRpcSystemException jaffaRpcSystemException) {
            //No-op
        }
        GrpcRequestSender grpcRequestSender = new GrpcRequestSender();
        Command command = new Command();
        grpcRequestSender.setCommand(command);
        try {
            grpcRequestSender.executeSync(new byte[]{});
            Assertions.fail();
        } catch (UnsupportedOperationException unsupportedOperationException) {
            //No-op
        }
        try {
            grpcRequestSender.executeAsync(new byte[]{});
            Assertions.fail();
        } catch (UnsupportedOperationException unsupportedOperationException) {
            //No-op
        }
        try {
            grpcRequestSender.executeSync(new Command());
            Assertions.fail();
        } catch (JaffaRpcExecutionException jaffaRpcExecutionException) {
            //No-op
        }
        try {
            grpcRequestSender.executeAsync(new Command());
            Assertions.fail();
        } catch (JaffaRpcExecutionException jaffaRpcExecutionException) {
            //No-op
        }
        Utils.registerService("xxx", Protocol.GRPC);
        command.setServiceClass("xxx");
        try {
            grpcRequestSender.executeSync(command);
            Assertions.fail();
        }catch (JaffaRpcExecutionException jaffaRpcNoRouteException){
            //No-op
        }
        System.setProperty("jaffa.rpc.protocol.grpc.use.ssl", "false");
        try {
            grpcRequestSender.executeSync(command);
            Assertions.fail();
        }catch (JaffaRpcNoRouteException jaffaRpcNoRouteException){
            //No-op
        }
        try {
            grpcRequestSender.executeAsync(command);
            Assertions.fail();
        }catch (JaffaRpcNoRouteException jaffaRpcNoRouteException){
            //No-op
        }
        Method method = null;
        try {
            method = GrpcRequestSender.class.getDeclaredMethod("processStatusException", StatusRuntimeException.class);
            method.setAccessible(true);
            StatusRuntimeException statusRuntimeException = new StatusRuntimeException(Status.UNAVAILABLE);
            method.invoke(grpcRequestSender, statusRuntimeException);
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException exception) {
            Assertions.fail();
        } catch (InvocationTargetException invocationTargetException) {
            Assertions.assertEquals(JaffaRpcNoRouteException.class, invocationTargetException.getCause().getClass());
        }
        Assertions.assertNotNull(method);
        try {
            StatusRuntimeException statusRuntimeException = new StatusRuntimeException(Status.DEADLINE_EXCEEDED);
            method.invoke(grpcRequestSender, statusRuntimeException);
        } catch (SecurityException | IllegalAccessException exception) {
            Assertions.fail();
        } catch (InvocationTargetException invocationTargetException) {
            Assertions.assertEquals(JaffaRpcExecutionTimeoutException.class, invocationTargetException.getCause().getClass());
        }
        try {
            StatusRuntimeException statusRuntimeException = new StatusRuntimeException(Status.DATA_LOSS);
            method.invoke(grpcRequestSender, statusRuntimeException);
        } catch (SecurityException | IllegalAccessException exception) {
            Assertions.fail();
        } catch (InvocationTargetException invocationTargetException) {
            Assertions.assertEquals(JaffaRpcExecutionException.class, invocationTargetException.getCause().getClass());
        }
    }
}
