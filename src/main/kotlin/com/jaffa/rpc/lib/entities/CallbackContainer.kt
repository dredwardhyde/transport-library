package com.jaffa.rpc.lib.entities

import java.io.Serializable

class CallbackContainer : Serializable {
    var key: String? = null
    var listener: String? = null
    var result: Any? = null
    var resultClass: String? = null

    override fun toString(): String {
        return "CallbackContainer(key=$key, listener=$listener, result=$result, resultClass=$resultClass)"
    }
}