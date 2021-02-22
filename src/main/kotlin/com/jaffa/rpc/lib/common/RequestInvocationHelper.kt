package com.jaffa.rpc.lib.common

import com.jaffa.rpc.lib.entities.CallbackContainer
import com.jaffa.rpc.lib.entities.Command
import com.jaffa.rpc.lib.entities.ExceptionHolder
import com.jaffa.rpc.lib.entities.RequestContextHelper
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException
import com.jaffa.rpc.lib.serialization.Serializer
import com.jaffa.rpc.lib.ui.AdminServer
import com.jaffa.rpc.lib.zookeeper.Utils
import org.apache.commons.lang3.ArrayUtils
import org.apache.commons.lang3.ClassUtils
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.*

object RequestInvocationHelper {

    private val log = LoggerFactory.getLogger(RequestInvocationHelper::class.java)

    val wrappedServices: MutableMap<Class<*>, Any> = HashMap()

    var context: ApplicationContext? = null

    @Throws(ClassNotFoundException::class)
    private fun getTargetService(command: Command): Any? {
        return wrappedServices[Class.forName(Utils.getServiceInterfaceNameFromClient(command.serviceClass))]
    }

    @Throws(ClassNotFoundException::class, NoSuchMethodException::class)
    private fun getTargetMethod(command: Command): Method? {
        val wrappedService = getTargetService(command)
        return if (ArrayUtils.isNotEmpty(command.methodArgs)) {
            val methodArgClasses: Array<Class<*>?> = arrayOfNulls(command.methodArgs.size)
            for (i in command.methodArgs.indices) {
                methodArgClasses[i] = Class.forName(command.methodArgs[i])
            }
            wrappedService?.javaClass?.getMethod(command.methodName, *methodArgClasses)
        } else {
            wrappedService?.javaClass?.getMethod(command.methodName)
        }
    }

    operator fun invoke(command: Command): Any? {
        val result: Any?
        val targetService: Any?
        val targetMethod: Method?
        try {
            targetService = getTargetService(command)
            targetMethod = getTargetMethod(command)
        } catch (exception: Exception) {
            throw JaffaRpcExecutionException(exception)
        }
        return try {
            RequestContextHelper.setMetaData(command)
            result = if (ArrayUtils.isNotEmpty(command.methodArgs)) {
                targetMethod?.invoke(targetService, *command.args)
            } else {
                targetMethod?.invoke(targetService)
            }
            if (targetMethod?.returnType == Void.TYPE) {
                Void.TYPE
            } else {
                result
            }
        } catch (e: Exception) {
            e.cause
        } finally {
            RequestContextHelper.removeMetaData()
        }
    }

    fun getResult(result: Any?): Any? {
        return if (result is Throwable && Serializer.isKryo) {
            val sw = StringWriter()
            result.printStackTrace(PrintWriter(sw))
            ExceptionHolder(sw.toString())
        } else result
    }

    private fun primitiveToWrapper(clz: Class<*>): Class<*> {
        return if (clz == Void.TYPE) Void::class.java else ClassUtils.primitiveToWrapper(clz)
    }

    @Throws(ClassNotFoundException::class, NoSuchMethodException::class)
    fun constructCallbackContainer(command: Command, result: Any?): CallbackContainer {
        val callbackContainer = CallbackContainer()
        callbackContainer.key = command.callbackKey
        callbackContainer.listener = command.callbackClass
        callbackContainer.result = getResult(result)
        val targetMethod = getTargetMethod(command)
        if (targetMethod != null) {
            callbackContainer.resultClass = primitiveToWrapper(targetMethod.returnType).name
        }
        return callbackContainer
    }

    @Throws(ClassNotFoundException::class, NoSuchMethodException::class, IllegalAccessException::class, InvocationTargetException::class)
    fun processCallbackContainer(callbackContainer: CallbackContainer?): Boolean {
        val key = callbackContainer?.key
        val command: Command? = FinalizationHelper.eventsToConsume.remove(callbackContainer?.key)
        return if (command != null) {
            val callbackClass = Class.forName(callbackContainer?.listener)
            val callBackBean = context?.getBean(callbackClass)
            val onErrorMethod = callbackClass.getMethod("onError", String::class.java, Throwable::class.java)
            val result = callbackContainer?.result
            val resultClazz = Class.forName(callbackContainer?.resultClass)
            if (result is ExceptionHolder) {
                onErrorMethod.invoke(callBackBean, key, JaffaRpcExecutionException(result.stackTrace))
            } else if (result is Throwable) {
                if (!Serializer.isKryo) {
                    onErrorMethod.invoke(callBackBean, key, JaffaRpcExecutionException(result))
                } else {
                    throw JaffaRpcSystemException("Same serialization protocol must be enabled cluster-wide!")
                }
            } else {
                val method = callbackClass.getMethod("onSuccess", String::class.java, resultClazz)
                if (resultClazz == Void::class.java) {
                    method.invoke(callBackBean, key, null)
                } else method.invoke(callBackBean, key, result)
            }
            AdminServer.addMetric(command)
            true
        } else {
            log.warn("Response {} already expired", callbackContainer?.key)
            false
        }
    }
}