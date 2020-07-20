package com.jaffa.rpc.lib.spring;

import com.jaffa.rpc.lib.annotations.ApiClient;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.StubMethod;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.lang.NonNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static net.bytebuddy.matcher.ElementMatchers.any;

@Slf4j
@Configuration
@DependsOn({"serverEndpoints"})
public class BeanStubLoader implements BeanDefinitionRegistryPostProcessor {
    @Override
    public void postProcessBeanDefinitionRegistry(@NonNull BeanDefinitionRegistry registry) {

        Map<String, ClientEndpoint> clientEndpoints = null;
        try {
            clientEndpoints = ((DefaultListableBeanFactory) registry).getBeansOfType(ClientEndpoint.class);
        } catch (BeansException e) {
            log.info("No client endpoints were defined");
        }
        if (Objects.isNull(clientEndpoints)) return;
        ClassLoader cl = BeanStubLoader.class.getClassLoader();
        Set<Class<?>> annotated = new HashSet<>();
        for (Class<?> client : clientEndpoints.values().stream().map(ClientEndpoint::getEndpoint).collect(Collectors.toList())) {
            boolean isClient = client.isAnnotationPresent(ApiClient.class);
            log.info("Client endpoint: {} isClient: {}", client.getName(), isClient);
            if (!isClient)
                throw new IllegalArgumentException("Class " + client.getName() + " is not annotated as ApiClient!");
            annotated.add(client);
        }
        annotated.stream().filter(Class::isInterface).forEach(c -> {
            Class<?> stubClass = new ByteBuddy()
                    .subclass(c)
                    .method(any())
                    .intercept(StubMethod.INSTANCE)
                    .make().load(cl, ClassLoadingStrategy.Default.INJECTION)
                    .getLoaded();
            BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(stubClass);
            registry.registerBeanDefinition(c.getSimpleName() + "Stub", builder.getBeanDefinition());
        });
    }

    @Override
    public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory configurableListableBeanFactory) {
        // No-op
    }
}