package com.jaffa.rpc.lib.spring

import com.jaffa.rpc.lib.common.OptionConstants
import com.jaffa.rpc.lib.security.TicketProvider

class ClientEndpoint {

    val endpoint: Class<*>

    val ticketProvider: Class<out TicketProvider>?

    constructor(endpoint: Class<*>, ticketProvider: Class<out TicketProvider>?) {
        require(ticketProvider != null) { OptionConstants.ILLEGAL_ARGS_MESSAGE }
        this.endpoint = endpoint
        this.ticketProvider = ticketProvider
    }

    constructor(endpoint: Class<*>) {
        this.endpoint = endpoint
        ticketProvider = null
    }
}