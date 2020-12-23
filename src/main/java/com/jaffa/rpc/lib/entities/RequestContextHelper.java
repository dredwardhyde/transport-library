package com.jaffa.rpc.lib.entities;

import com.jaffa.rpc.lib.security.SecurityTicket;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RequestContextHelper {

    private static final ThreadLocal<String> sourceModuleId = new ThreadLocal<>();
    private static final ThreadLocal<SecurityTicket> securityTicketThreadLocal = new ThreadLocal<>();

    public static String getSourceModuleId() {
        return sourceModuleId.get();
    }

    public static void setSourceModuleId(String sourceModuleId) {
        RequestContextHelper.sourceModuleId.set(sourceModuleId);
    }

    public static SecurityTicket getTicket() {
        return securityTicketThreadLocal.get();
    }

    public static void setSecurityTicket(SecurityTicket securityTicket) {
        RequestContextHelper.securityTicketThreadLocal.set(securityTicket);
    }

    public static void setMetaData(@NotNull Command command) {
        setSourceModuleId(command.getSourceModuleId());
        setSecurityTicket(command.getTicket());
    }

    public static void removeMetaData() {
        sourceModuleId.remove();
        securityTicketThreadLocal.remove();
    }
}
