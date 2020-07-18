package com.jaffa.rpc.test;

import com.jaffa.rpc.lib.configuration.JaffaRpcConfig;
import com.jaffa.rpc.lib.spring.ClientEndpoints;
import com.jaffa.rpc.lib.spring.ServerEndpoints;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ComponentScan
@Import(JaffaRpcConfig.class)
public class MainConfig {

    @Bean
    public ServerEndpoints serverEndpoints() {
        return new ServerEndpoints(PersonServiceImpl.class, ClientServiceImpl.class);
    }

    @Bean
    public ClientEndpoints clientEndpoints() {
        return new ClientEndpoints(ClientServiceClient.class, PersonServiceClient.class);
    }
}
