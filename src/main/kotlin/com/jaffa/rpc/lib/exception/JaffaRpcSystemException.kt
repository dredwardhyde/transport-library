package com.jaffa.rpc.lib.exception

class JaffaRpcSystemException : RuntimeException {
    constructor(cause: String?) : super(cause)
    constructor(cause: Throwable?) : super("Exception occurred during RPC call", cause)

    companion object {
        const val NO_PROTOCOL_DEFINED = "No known protocol defined"
    }
}