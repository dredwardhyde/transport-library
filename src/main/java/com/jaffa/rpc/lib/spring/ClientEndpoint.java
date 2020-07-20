package com.jaffa.rpc.lib.spring;

import com.jaffa.rpc.lib.security.TicketProvider;
import lombok.Getter;

@Getter
public class ClientEndpoint {
    private final Class<?> endpoint;
    private final Class<? extends TicketProvider> ticketProvider;

    public ClientEndpoint(Class<?> endpoint, Class<? extends TicketProvider> ticketProvider) {
        this.endpoint = endpoint;
        this.ticketProvider = ticketProvider;
    }

    public ClientEndpoint(Class<?> endpoint) {
        this.endpoint = endpoint;
        this.ticketProvider = null;
    }
}
