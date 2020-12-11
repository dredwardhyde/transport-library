package com.jaffa.rpc.lib.configuration;

import com.jaffa.rpc.lib.JaffaService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan({"com.jaffa.rpc"})
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class JaffaRpcConfig {

    @Bean(destroyMethod = "close")
    @DependsOn({"serverEndpoints"})
    public JaffaService jaffaService() {
        return new JaffaService();
    }
}
