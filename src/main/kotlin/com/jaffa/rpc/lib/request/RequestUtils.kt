package com.jaffa.rpc.lib.request

import com.jaffa.rpc.lib.JaffaService
import com.jaffa.rpc.lib.entities.Protocol
import com.jaffa.rpc.lib.exception.JaffaRpcNoRouteException
import com.jaffa.rpc.lib.zookeeper.Utils

object RequestUtils {
    fun getTopicForService(service: String, moduleId: String?, sync: Boolean): String {
        val serviceInterface = Utils.getServiceInterfaceNameFromClient(service)
        var availableModuleId: String? = moduleId
        if (moduleId != null) {
            Utils.getHostForService(serviceInterface, moduleId, Protocol.KAFKA)
        } else {
            availableModuleId = Utils.getModuleForService(serviceInterface, Protocol.KAFKA)
        }
        val topicName = serviceInterface + "-" + availableModuleId + "-server" + if (sync) "-sync" else "-async"
        return if (!JaffaService.zkClient?.topicExists(topicName)!!) throw JaffaRpcNoRouteException(
            serviceInterface,
            availableModuleId
        ) else topicName
    }
}