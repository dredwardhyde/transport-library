package com.jaffa.rpc.test.services;

import com.jaffa.rpc.lib.annotations.Api;
import com.jaffa.rpc.test.entities.Address;
import com.jaffa.rpc.test.entities.Person;

@Api
public interface PersonService {

    String TEST = "TEST";

    static void lol3() {
        System.out.println("lol3");
    }

    int add(String name, String email, Address address);

    Person get(Integer id);

    void lol();

    void lol2(String message);

    String getName();

    String getHeavy(String heavy);

    Person testError();

}
