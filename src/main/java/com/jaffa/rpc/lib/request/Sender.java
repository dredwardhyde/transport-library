package com.jaffa.rpc.lib.request;

import com.jaffa.rpc.lib.entities.Command;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class Sender {
    protected long timeout = -1;
    protected String moduleId;
    protected Command command;

    public byte[] executeSync(byte[] message) {
        throw new UnsupportedOperationException();
    }

    public void executeAsync(byte[] message) {
        throw new UnsupportedOperationException();
    }

    public Object executeSync(Command command) {
        throw new UnsupportedOperationException();
    }

    public void executeAsync(Command command) {
        throw new UnsupportedOperationException();
    }
}
