package com.jaffa.rpc.lib.exception

import com.jaffa.rpc.lib.entities.Protocol

class JaffaRpcExecutionException : RuntimeException {

    constructor(cause: String?) : super(cause)

    constructor(cause: Throwable?) : super("Exception occurred during RPC call", cause)

}

class JaffaRpcExecutionTimeoutException : RuntimeException("RPC execution timeout")

class JaffaRpcNoRouteException : RuntimeException {

    constructor(service: String?, moduleId: String?) : super(MESSAGE_PREFIX + service + if (moduleId != null) " and module.id $moduleId" else "")

    constructor(service: String?) : super(MESSAGE_PREFIX + service)

    constructor(service: String?, protocol: Protocol) : super(MESSAGE_PREFIX + service + " and protocol " + protocol.shortName)

    companion object {
        private const val MESSAGE_PREFIX = "No route for service: "
    }
}

class JaffaRpcSystemException : RuntimeException {

    constructor(cause: String?) : super(cause)

    constructor(cause: Throwable?) : super("Exception occurred during RPC call", cause)

    companion object {
        const val NO_PROTOCOL_DEFINED = "No known protocol defined"
    }
}