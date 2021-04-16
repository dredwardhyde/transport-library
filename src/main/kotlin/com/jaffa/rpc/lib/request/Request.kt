package com.jaffa.rpc.lib.request

import com.jaffa.rpc.lib.callbacks.Callback
import java.util.concurrent.TimeUnit

interface Request<T> {

    fun withTimeout(timeout: Long?, unit: TimeUnit?): Request<T>

    fun executeSync(): T

    fun onModule(moduleId: String?): Request<T>

    fun executeAsync(key: String?, listener: Class<out Callback<T>>?)

}