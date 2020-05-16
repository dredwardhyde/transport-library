package com.jaffa.rpc.lib.callbacks;

@SuppressWarnings("unused")
public interface Callback<T> {

    void onSuccess(String key, T result);

    void onError(String key, Throwable exception);
}
