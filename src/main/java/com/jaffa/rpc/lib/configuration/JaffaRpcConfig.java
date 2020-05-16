package com.jaffa.rpc.lib.configuration;

import com.jaffa.rpc.lib.JaffaService;
import org.springframework.context.annotation.*;

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
