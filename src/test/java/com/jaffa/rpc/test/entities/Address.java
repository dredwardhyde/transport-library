package com.jaffa.rpc.test.entities;

import lombok.*;

import java.io.Serializable;

@Setter
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class Address implements Serializable {

    private String zip;
    private String street;
    private String flat;
    private String city;
}
