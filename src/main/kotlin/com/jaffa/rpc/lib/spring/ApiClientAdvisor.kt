package com.jaffa.rpc.lib.spring

import com.jaffa.rpc.lib.JaffaService
import com.jaffa.rpc.lib.annotations.ApiClient
import com.jaffa.rpc.lib.common.OptionConstants
import com.jaffa.rpc.lib.entities.Command
import com.jaffa.rpc.lib.entities.Protocol
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException
import com.jaffa.rpc.lib.request.RequestImpl
import com.jaffa.rpc.lib.security.TicketProvider
import com.jaffa.rpc.lib.zookeeper.Utils
import org.aopalliance.aop.Advice
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.slf4j.LoggerFactory
import org.springframework.aop.Pointcut
import org.springframework.aop.support.AbstractPointcutAdvisor
import org.springframework.aop.support.StaticMethodMatcherPointcut
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.lang.NonNull
import org.springframework.stereotype.Component
import java.lang.reflect.Method
import java.net.UnknownHostException
import java.util.*

@Component
class ApiClientAdvisor : AbstractPointcutAdvisor() {

    private val log = LoggerFactory.getLogger(ApiClientAdvisor::class.java)

    @Transient
    private val interceptor: MethodInterceptor

    @Transient
    private val pointcut: StaticMethodMatcherPointcut = ApiClientAnnotationOnClassOrInheritedInterfacePointcut()

    @Autowired
    @Transient
    private val context: ApplicationContext? = null
    private fun setMetadata(command: Command) {
        try {
            if (Utils.rpcProtocol == Protocol.ZMQ) command.callBackHost = Utils.zeroMQCallbackBindAddress
            if (Utils.rpcProtocol == Protocol.HTTP) command.callBackHost = Utils.httpCallbackStringAddress
            if (Utils.rpcProtocol == Protocol.GRPC) command.callBackHost = Utils.zeroMQCallbackBindAddress
        } catch (e: UnknownHostException) {
            log.error("Error during metadata setting", e)
            throw JaffaRpcSystemException(e)
        }
        command.sourceModuleId = OptionConstants.MODULE_ID
        command.rqUid = UUID.randomUUID().toString()
    }

    @NonNull
    override fun getPointcut(): Pointcut {
        return pointcut
    }

    @NonNull
    override fun getAdvice(): Advice {
        return interceptor
    }

    private class ApiClientAnnotationOnClassOrInheritedInterfacePointcut : StaticMethodMatcherPointcut() {
        override fun matches(@NonNull method: Method, @NonNull targetClass: Class<*>?): Boolean {
            return AnnotationUtils.findAnnotation(targetClass, ApiClient::class.java) != null
        }
    }

    init {
        interceptor = MethodInterceptor { invocation: MethodInvocation ->
            val command = Command()
            setMetadata(command)
            val client = invocation.method.declaringClass.interfaces[0]
            command.serviceClass = client.name
            val ticketProvideClass: Class<out TicketProvider>? = JaffaService.clientsAndTicketProviders[client]
            if (ticketProvideClass != null) {
                val ticketProvider = context?.getBean(ticketProvideClass)
                command.ticket = ticketProvider?.ticket
            }
            command.methodName = invocation.method.name
            command.args = invocation.arguments
            if (invocation.method.parameterCount != 0) {
                command.methodArgs = Arrays.stream(invocation.method.parameterTypes)
                        .map { obj: Class<*> -> obj.name }
                        .toArray { size -> arrayOfNulls<String>(size) }
            }
            RequestImpl<Any>(command)
        }
    }
}