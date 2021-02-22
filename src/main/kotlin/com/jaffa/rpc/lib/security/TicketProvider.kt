package com.jaffa.rpc.lib.security

interface TicketProvider {
    val ticket: SecurityTicket?
}