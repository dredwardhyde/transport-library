package com.jaffa.rpc.test.services;

import com.jaffa.rpc.lib.annotations.ApiServer;
import com.jaffa.rpc.lib.entities.RequestContext;
import com.jaffa.rpc.lib.zookeeper.Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@ApiServer
@Component
public class ClientServiceImpl implements ClientService {

    @Override
    public void lol3(String message) {
        log.info("SOURCE MODULE ID: {} MY MODULE ID: {}", RequestContext.getSourceModuleId(), Utils.getRequiredOption("jaffa.rpc.module.id"));
        log.info("TICKET: {}", RequestContext.getTicket());
        log.info("lol3 {}", message);
    }

    @Override
    public void lol4(String message) {
        log.info("SOURCE MODULE ID: {} MY MODULE ID: {}", RequestContext.getSourceModuleId(), Utils.getRequiredOption("jaffa.rpc.module.id"));
        log.info("TICKET: {}", RequestContext.getTicket());
        log.info("lol4 {}", message);
        try {
            Thread.sleep(11_000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
