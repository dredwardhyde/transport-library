package com.jaffa.rpc.lib.callbacks

interface Callback<T> {

    fun onSuccess(key: String?, result: T)

    fun onError(key: String?, exception: Throwable?)

}