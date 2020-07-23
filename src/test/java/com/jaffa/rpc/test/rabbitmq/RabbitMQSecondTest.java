package com.jaffa.rpc.test.rabbitmq;

import com.jaffa.rpc.lib.JaffaService;
import com.jaffa.rpc.lib.common.OptionConstants;
import com.jaffa.rpc.lib.entities.Command;
import com.jaffa.rpc.lib.entities.Protocol;
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException;
import com.jaffa.rpc.lib.exception.JaffaRpcNoRouteException;
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException;
import com.jaffa.rpc.lib.rabbitmq.RabbitMQRequestSender;
import com.jaffa.rpc.lib.zookeeper.Utils;
import com.jaffa.rpc.test.ZooKeeperExtension;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.RabbitConnectionFactoryBean;

import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@SuppressWarnings({"squid:S5786"})
@ExtendWith({ZooKeeperExtension.class})
public class RabbitMQSecondTest {
    static {
        System.setProperty("jaffa.rpc.module.id", "test.server");
        System.setProperty("jaffa.rpc.protocol", "rabbit");
    }

    @Test
    public void stage1() {
        Utils.connect("localhost:2181");
        Utils.registerService("xxx", Protocol.RABBIT);
        RabbitConnectionFactoryBean factory = new RabbitConnectionFactoryBean();
        factory.setHost("localhost");
        factory.setPort(5672);
        factory.setUsername(System.getProperty(OptionConstants.RABBIT_LOGIN, "guest"));
        factory.setPassword(System.getProperty(OptionConstants.RABBIT_PASSWORD, "guest"));
        JaffaService.setConnectionFactory(new CachingConnectionFactory(factory.getRabbitConnectionFactory()));
        try {
            RabbitMQRequestSender.init();
            fail();
        } catch (JaffaRpcSystemException jaffaRpcSystemException) {
            //No-op
        }
        RabbitMQRequestSender rabbitMQRequestSender = new RabbitMQRequestSender();
        Command command = new Command();
        command.setRqUid("xxx");
        command.setServiceClass("xxx");
        rabbitMQRequestSender.setTimeout(500);
        try {
            rabbitMQRequestSender.executeAsync(new byte[]{});
            fail();
        } catch (JaffaRpcExecutionException jaffaRpcExecutionException) {
            //No-op
        }
        try {
            rabbitMQRequestSender.executeSync(new byte[]{});
            fail();
        } catch (JaffaRpcExecutionException jaffaRpcExecutionException) {
            //No-op
        }
        rabbitMQRequestSender.setCommand(command);
        rabbitMQRequestSender.setModuleId("lol.server");
        try {
            rabbitMQRequestSender.executeSync(new byte[]{});
            fail();
        } catch (JaffaRpcNoRouteException jaffaRpcNoRouteException) {
            //No-op
        }
        try {
            rabbitMQRequestSender.executeAsync(new byte[]{});
            fail();
        } catch (JaffaRpcNoRouteException jaffaRpcNoRouteException) {
            //No-op
        }
    }
}
