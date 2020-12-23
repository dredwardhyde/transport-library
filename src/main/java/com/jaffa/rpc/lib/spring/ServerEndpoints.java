package com.jaffa.rpc.lib.spring;

import com.jaffa.rpc.lib.common.OptionConstants;
import lombok.Getter;

import java.util.Objects;

@Getter
public class ServerEndpoints {
    private final Class<?>[] endpoints;

    public ServerEndpoints(Class<?>... endpoints) {
        if (Objects.isNull(endpoints))
            throw new IllegalArgumentException(OptionConstants.ILLEGAL_ARGS_MESSAGE);
        this.endpoints = endpoints;
    }
}
