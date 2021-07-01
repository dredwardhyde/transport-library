package com.jaffa.rpc.lib.common

import com.jaffa.rpc.lib.entities.Command
import com.jaffa.rpc.lib.security.SecurityTicket

object RequestContextHelper {

    private val sourceModuleId = ThreadLocal<String>()

    private val securityTicketThreadLocal = ThreadLocal<SecurityTicket>()

    @kotlin.jvm.JvmStatic
    fun getSourceModuleId(): String {
        return sourceModuleId.get()
    }

    @kotlin.jvm.JvmStatic
    val ticket: SecurityTicket?
        get() = securityTicketThreadLocal.get()

    fun setMetaData(command: Command) {
        sourceModuleId.set(command.sourceModuleId)
        securityTicketThreadLocal.set(command.ticket)
    }

    fun removeMetaData() {
        sourceModuleId.remove()
        securityTicketThreadLocal.remove()
    }
}