package com.jaffa.rpc.lib.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

@NoArgsConstructor
@Getter
@Setter
@ToString
@SuppressWarnings("squid:S1948")
public class CallbackContainer implements Serializable {
    private String key;
    private String listener;
    private Object result;
    private String resultClass;
}
