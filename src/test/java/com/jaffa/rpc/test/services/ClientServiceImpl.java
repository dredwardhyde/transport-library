package com.jaffa.rpc.test.services;

import com.jaffa.rpc.lib.annotations.ApiServer;
import com.jaffa.rpc.lib.entities.RequestContextHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@ApiServer
@Component
public class ClientServiceImpl implements ClientService {

    @Override
    public void lol3(String message) {
        log.info(String.valueOf(RequestContextHelper.getTicket()));
    }

    @Override
    public void lol4(String message) {
        log.info(String.valueOf(RequestContextHelper.getTicket()));
        try {
            Thread.sleep(11_000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
