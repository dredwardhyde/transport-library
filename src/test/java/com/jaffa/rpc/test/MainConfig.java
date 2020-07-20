package com.jaffa.rpc.test;

import com.jaffa.rpc.lib.configuration.JaffaRpcConfig;
import com.jaffa.rpc.lib.spring.ClientEndpoint;
import com.jaffa.rpc.lib.spring.ServerEndpoints;
import com.jaffa.rpc.test.services.ClientServiceClient;
import com.jaffa.rpc.test.services.ClientServiceImpl;
import com.jaffa.rpc.test.services.PersonServiceClient;
import com.jaffa.rpc.test.services.PersonServiceImpl;
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
    public ClientEndpoint clientEndpoint() {
        return new ClientEndpoint(ClientServiceClient.class, TicketProviderImpl.class);
    }

    @Bean
    public ClientEndpoint personEndpoint() {
        return new ClientEndpoint(PersonServiceClient.class);
    }
}
