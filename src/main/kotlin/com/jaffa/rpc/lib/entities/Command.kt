package com.jaffa.rpc.lib.entities

import com.jaffa.rpc.lib.security.SecurityTicket
import java.io.Serializable

class Command : Serializable {
    var serviceClass: String? = null
    var methodName: String? = null
    var methodArgs: Array<String> = emptyArray()
    var args: Array<Any?> = emptyArray()
    var callbackClass: String? = null
    var callbackKey: String? = null
    var callBackHost: String? = null
    var sourceModuleId: String? = null
    var rqUid: String? = null
    var ticket: SecurityTicket? = null
    var asyncExpireTime: Long = 0
    var requestTime: Long = 0
    var localRequestTime: Long = 0

    override fun toString(): String {
        return "Command(serviceClass=$serviceClass, " +
                "methodName=$methodName, " +
                "methodArgs=${methodArgs.contentToString()}, " +
                "args=${args.contentToString()}, " +
                "callbackClass=$callbackClass, " +
                "callbackKey=$callbackKey, " +
                "callBackHost=$callBackHost, " +
                "sourceModuleId=$sourceModuleId, " +
                "rqUid=$rqUid, ticket=$ticket, " +
                "asyncExpireTime=$asyncExpireTime, " +
                "requestTime=$requestTime, " +
                "localRequestTime=$localRequestTime)"
    }
}