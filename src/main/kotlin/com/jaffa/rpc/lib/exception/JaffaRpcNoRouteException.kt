package com.jaffa.rpc.lib.exception

import com.jaffa.rpc.lib.entities.Protocol

class JaffaRpcNoRouteException : RuntimeException {
    constructor(
        service: String?,
        moduleId: String?
    ) : super(MESSAGE_PREFIX + service + if (moduleId != null) " and module.id $moduleId" else "")

    constructor(service: String?) : super(MESSAGE_PREFIX + service)
    constructor(
        service: String?,
        protocol: Protocol
    ) : super(MESSAGE_PREFIX + service + " and protocol " + protocol.shortName)

    companion object {
        private const val MESSAGE_PREFIX = "No route for service: "
    }
}