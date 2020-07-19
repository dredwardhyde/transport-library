package com.jaffa.rpc.lib.configuration;

import com.jaffa.rpc.lib.JaffaService;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Bean;

@Configuration
@ComponentScan({"com.jaffa.rpc"})
@EnableAspectJAutoProxy(proxyTargetClass = true)
@SuppressWarnings("unused")
public class JaffaRpcConfig {

    @Bean(destroyMethod = "close")
    @DependsOn({"serverEndpoints", "clientEndpoints"})
    public JaffaService jaffaService() {
        return new JaffaService();
    }
}
