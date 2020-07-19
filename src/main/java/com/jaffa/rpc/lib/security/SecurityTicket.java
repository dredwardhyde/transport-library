package com.jaffa.rpc.lib.security;

import lombok.ToString;
import lombok.Setter;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;

@ToString
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SecurityTicket implements Serializable {
    private String user;
    private String token;
}
