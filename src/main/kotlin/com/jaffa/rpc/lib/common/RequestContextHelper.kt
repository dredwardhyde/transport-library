package com.jaffa.rpc.lib.common

import com.jaffa.rpc.lib.entities.Command
import com.jaffa.rpc.lib.security.SecurityTicket

object RequestContextHelper {
    val sourceModuleId = ThreadLocal<String>()
    private val securityTicketThreadLocal = ThreadLocal<SecurityTicket>()
    fun getSourceModuleId(): String {
        return sourceModuleId.get()
    }

    fun setSourceModuleId(sourceModuleId: String?) {
        RequestContextHelper.sourceModuleId.set(sourceModuleId)
    }

    @kotlin.jvm.JvmStatic
    val ticket: SecurityTicket?
        get() = securityTicketThreadLocal.get()

    private fun setSecurityTicket(securityTicket: SecurityTicket?) {
        securityTicketThreadLocal.set(securityTicket)
    }

    fun setMetaData(command: Command) {
        setSourceModuleId(command.sourceModuleId)
        setSecurityTicket(command.ticket)
    }

    fun removeMetaData() {
        sourceModuleId.remove()
        securityTicketThreadLocal.remove()
    }
}