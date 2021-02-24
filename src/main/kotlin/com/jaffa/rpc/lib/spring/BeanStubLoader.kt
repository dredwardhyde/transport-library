package com.jaffa.rpc.lib.spring

import com.jaffa.rpc.lib.annotations.ApiClient
import com.jaffa.rpc.lib.spring.ClientEndpoint
import net.bytebuddy.ByteBuddy
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy
import net.bytebuddy.implementation.StubMethod
import net.bytebuddy.matcher.ElementMatchers
import org.slf4j.LoggerFactory
import org.springframework.beans.BeansException
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.lang.NonNull
import java.util.*
import java.util.stream.Collectors

@Configuration
@DependsOn("serverEndpoints")
open class BeanStubLoader : BeanDefinitionRegistryPostProcessor {
    private val log = LoggerFactory.getLogger(BeanStubLoader::class.java)

    override fun postProcessBeanDefinitionRegistry(@NonNull registry: BeanDefinitionRegistry) {
        var clientEndpoints: Map<String?, ClientEndpoint>? = null
        try {
            clientEndpoints = (registry as DefaultListableBeanFactory).getBeansOfType(ClientEndpoint::class.java)
        } catch (e: BeansException) {
            log.info("No client endpoints were defined")
        }
        if (clientEndpoints == null) return
        val annotated: MutableSet<Class<*>> = HashSet()
        for (client in clientEndpoints.values.stream().map { obj: ClientEndpoint -> obj.endpoint }
                .collect(Collectors.toList())) {
            val isClient = client.isAnnotationPresent(ApiClient::class.java)
            log.info("Client endpoint: {} isClient: {}", client.name, isClient)
            require(isClient) { "Class " + client.name + " is not annotated as ApiClient!" }
            annotated.add(client)
        }
        annotated.stream().filter { obj: Class<*> -> obj.isInterface }.forEach { c: Class<*> ->
            val stubClass = ByteBuddy()
                    .subclass(c)
                    .method(ElementMatchers.any<Any>())
                    .intercept(StubMethod.INSTANCE)
                    .make().load(BeanStubLoader::class.java.classLoader, ClassLoadingStrategy.Default.INJECTION)
                    .loaded
            registry.registerBeanDefinition(c.simpleName + "Stub", BeanDefinitionBuilder.genericBeanDefinition(stubClass).beanDefinition)
        }
    }

    override fun postProcessBeanFactory(@NonNull configurableListableBeanFactory: ConfigurableListableBeanFactory) {
        // No-op
    }
}