package com.jaffa.rpc.lib.request;

import com.jaffa.rpc.lib.entities.Command;
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionTimeoutException;
import com.jaffa.rpc.lib.serialization.Serializer;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Setter
@Getter
public abstract class Sender {
    protected long timeout = -1;
    protected String moduleId;
    protected Command command;

    protected abstract byte[] executeSync(byte[] message);

    protected abstract void executeAsync(byte[] message);

    public Object executeSync(@NotNull Command command) {
        byte[] out = Serializer.getCurrent().serialize(command);
        byte[] response = executeSync(out);
        if (Objects.isNull(response)) {
            throw new JaffaRpcExecutionTimeoutException();
        }
        return Serializer.getCurrent().deserializeWithClass(response);
    }

    public void executeAsync(@NotNull Command command) {
        executeAsync(Serializer.getCurrent().serialize(command));
    }
}
