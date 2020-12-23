package com.jaffa.rpc.lib.spring;

import com.jaffa.rpc.lib.common.OptionConstants;
import com.jaffa.rpc.lib.security.TicketProvider;
import lombok.Getter;

import java.util.Objects;

@Getter
public class ClientEndpoint {
    private final Class<?> endpoint;
    private final Class<? extends TicketProvider> ticketProvider;

    public ClientEndpoint(Class<?> endpoint, Class<? extends TicketProvider> ticketProvider) {
        if (Objects.isNull(endpoint) || Objects.isNull(ticketProvider))
            throw new IllegalArgumentException(OptionConstants.ILLEGAL_ARGS_MESSAGE);
        this.endpoint = endpoint;
        this.ticketProvider = ticketProvider;
    }

    public ClientEndpoint(Class<?> endpoint) {
        this.endpoint = endpoint;
        this.ticketProvider = null;
    }
}
