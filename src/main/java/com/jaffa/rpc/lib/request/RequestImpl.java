package com.jaffa.rpc.lib.request;

import com.jaffa.rpc.lib.callbacks.Callback;
import com.jaffa.rpc.lib.common.FinalizationHelper;
import com.jaffa.rpc.lib.common.OptionConstants;
import com.jaffa.rpc.lib.entities.Command;
import com.jaffa.rpc.lib.entities.ExceptionHolder;
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException;
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException;
import com.jaffa.rpc.lib.ui.AdminServer;
import com.jaffa.rpc.lib.zookeeper.Utils;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RequestImpl<T> implements Request<T> {

    private final Command command;
    private final Sender sender;
    private long timeout = -1;
    private String moduleId;

    public RequestImpl(@NotNull Command command) {
        this.command = command;
        try {
            sender = Utils.getCurrentSenderClass().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new JaffaRpcSystemException("Can not initialize sender!");
        }
    }

    @Override
    public RequestImpl<T> withTimeout(long timeout, @NotNull TimeUnit unit) {
        if (Objects.isNull(unit))
            throw new IllegalArgumentException(OptionConstants.ILLEGAL_ARGS_MESSAGE);
        if (timeout < 0)
            throw new IllegalArgumentException(OptionConstants.NEGATIVE_TIMEOUT_MESSAGE);
        this.timeout = unit.toMillis(timeout);
        return this;
    }

    @Override
    public RequestImpl<T> onModule(@NotNull String moduleId) {
        if (Objects.isNull(moduleId))
            throw new IllegalArgumentException(OptionConstants.ILLEGAL_ARGS_MESSAGE);
        this.moduleId = moduleId;
        return this;
    }

    private void initSender() {
        sender.setCommand(command);
        sender.setModuleId(moduleId);
        sender.setTimeout(timeout);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T executeSync() {
        initSender();
        command.setRequestTime(System.currentTimeMillis());
        command.setLocalRequestTime(System.nanoTime());
        Object result = sender.executeSync(command);
        AdminServer.addMetric(command);
        if (result instanceof ExceptionHolder)
            throw new JaffaRpcExecutionException(((ExceptionHolder) result).getStackTrace());
        if (result instanceof Throwable)
            throw new JaffaRpcExecutionException((Throwable) result);
        return (T) result;
    }

    @Override
    public void executeAsync(@NotNull String key, @NotNull Class<? extends Callback<T>> listener) {
        if (Objects.isNull(key) || Objects.isNull(listener))
            throw new IllegalArgumentException(OptionConstants.ILLEGAL_ARGS_MESSAGE);
        initSender();
        command.setCallbackClass(listener.getName());
        command.setCallbackKey(key);
        command.setRequestTime(System.currentTimeMillis());
        command.setLocalRequestTime(System.nanoTime());
        command.setAsyncExpireTime(System.currentTimeMillis() + (timeout != -1 ? timeout : 1000 * 60 * 60));
        log.debug("Async command {} added to finalization queue", command);
        FinalizationHelper.getEventsToConsume().put(command.getCallbackKey(), command);
        sender.executeAsync(command);
    }
}
