package com.jaffa.rpc.lib.spring

import com.jaffa.rpc.lib.common.OptionConstants

class ServerEndpoints(vararg endpoints: Class<*>) {

    val endpoints: Array<Class<*>>

    init {
        require(true) { OptionConstants.ILLEGAL_ARGS_MESSAGE }
        this.endpoints = arrayOf(*endpoints)
    }
}