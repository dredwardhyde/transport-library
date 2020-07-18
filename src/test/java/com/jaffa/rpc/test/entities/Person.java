package com.jaffa.rpc.test.entities;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class Person implements Serializable {
    private Integer id;
    private String name;
    private String email;
    private String twitter;
    private Address address;
}