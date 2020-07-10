package com.jaffa.rpc.lib.request;

import com.jaffa.rpc.lib.JaffaService;
import com.jaffa.rpc.lib.entities.Protocol;
import com.jaffa.rpc.lib.exception.JaffaRpcNoRouteException;
import com.jaffa.rpc.lib.zookeeper.Utils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Objects;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RequestUtils {
    public static String getTopicForService(String service, String moduleId, boolean sync) {
        String serviceInterface = Utils.getServiceInterfaceNameFromClient(service);
        String availableModuleId = moduleId;
        if (Objects.nonNull(moduleId)) {
            Utils.getHostForService(serviceInterface, moduleId, Protocol.KAFKA);
        } else {
            availableModuleId = Utils.getModuleForService(serviceInterface, Protocol.KAFKA);
        }
        String topicName = serviceInterface + "-" + availableModuleId + "-server" + (sync ? "-sync" : "-async");
        if (!JaffaService.getZkClient().topicExists(topicName))
            throw new JaffaRpcNoRouteException(serviceInterface, availableModuleId);
        else
            return topicName;
    }
}
