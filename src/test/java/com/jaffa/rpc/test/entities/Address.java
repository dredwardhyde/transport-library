package com.jaffa.rpc.test.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

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
