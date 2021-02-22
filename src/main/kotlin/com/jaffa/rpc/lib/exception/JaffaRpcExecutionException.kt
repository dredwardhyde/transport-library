package com.jaffa.rpc.lib.exception

class JaffaRpcExecutionException : RuntimeException {
    constructor(cause: String?) : super(cause)
    constructor(cause: Throwable?) : super("Exception occurred during RPC call", cause)
}