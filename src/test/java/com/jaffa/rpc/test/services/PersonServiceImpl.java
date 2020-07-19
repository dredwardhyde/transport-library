package com.jaffa.rpc.test.services;

import com.jaffa.rpc.lib.annotations.ApiServer;
import com.jaffa.rpc.lib.zookeeper.Utils;
import com.jaffa.rpc.test.entities.Address;
import com.jaffa.rpc.test.entities.Person;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@ApiServer
@Component
public class PersonServiceImpl implements PersonService {

    private final List<Person> people = new ArrayList<>();

    private final AtomicInteger idProvider = new AtomicInteger(1);

    @Override
    public synchronized int add(String name, String email, Address address) {
        Person p = new Person();
        p.setEmail(email);
        p.setName(name);
        p.setId(idProvider.addAndGet(1));
        p.setAddress(address);
        people.add(p);
        return p.getId();
    }

    @Override
    public synchronized Person get(final Integer id) {
        for (Person p : this.people) {
            if (p.getId().equals(id)) {
                return p;
            }
        }
        return null;
    }

    @Override
    public void lol() {
        // No-op
    }

    @Override
    public void lol2(String message) {
        // No-op
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public String getHeavy(String heavy) {
        return heavy;
    }

    @Override
    public Person testError() {
        throw new RuntimeException("very bad in " + Utils.getRequiredOption("jaffa.rpc.module.id"));
    }
}