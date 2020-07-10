package com.jaffa.rpc.lib.common;

import com.jaffa.rpc.lib.entities.CallbackContainer;
import com.jaffa.rpc.lib.entities.Command;
import com.jaffa.rpc.lib.entities.ExceptionHolder;
import com.jaffa.rpc.lib.entities.RequestContext;
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException;
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException;
import com.jaffa.rpc.lib.serialization.Serializer;
import com.jaffa.rpc.lib.ui.AdminServer;
import com.jaffa.rpc.lib.zookeeper.Utils;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ClassUtils;
import org.springframework.context.ApplicationContext;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RequestInvoker {

    @Getter
    private static final Map<Class<?>, Object> wrappedServices = new HashMap<>();

    @Setter
    private static ApplicationContext context;

    private static Object getTargetService(Command command) throws ClassNotFoundException {
        return wrappedServices.get(Class.forName(Utils.getServiceInterfaceNameFromClient(command.getServiceClass())));
    }

    private static Method getTargetMethod(Command command) throws ClassNotFoundException, NoSuchMethodException {
        Object wrappedService = getTargetService(command);
        if (ArrayUtils.isNotEmpty(command.getMethodArgs())) {
            Class<?>[] methodArgClasses = new Class[command.getMethodArgs().length];
            for (int i = 0; i < command.getMethodArgs().length; i++) {
                methodArgClasses[i] = Class.forName(command.getMethodArgs()[i]);
            }
            return wrappedService.getClass().getMethod(command.getMethodName(), methodArgClasses);
        } else {
            return wrappedService.getClass().getMethod(command.getMethodName());
        }
    }

    public static Object invoke(Command command) {
        try {
            RequestContext.setMetaData(command);
            Object targetService = getTargetService(command);
            Method targetMethod = getTargetMethod(command);
            Object result;
            if (ArrayUtils.isNotEmpty(command.getMethodArgs())) {
                result = targetMethod.invoke(targetService, command.getArgs());
            } else {
                result = targetMethod.invoke(targetService);
            }
            if (targetMethod.getReturnType().equals(Void.TYPE)) {
                return Void.TYPE;
            } else {
                return result;
            }
        } catch (Exception e) {
            return e.getCause();
        } finally {
            RequestContext.removeMetaData();
        }
    }

    public static Object getResult(Object result) {
        if (result instanceof Throwable && Serializer.isKryo) {
            StringWriter sw = new StringWriter();
            ((Throwable) result).printStackTrace(new PrintWriter(sw));
            return new ExceptionHolder(sw.toString());
        } else return result;
    }

    private static Class<?> primitiveToWrapper(Class<?> clz){
        if(clz.equals(void.class))
            return Void.class;
        else
            return ClassUtils.primitiveToWrapper(clz);
    }

    public static CallbackContainer constructCallbackContainer(Command command, Object result) throws ClassNotFoundException, NoSuchMethodException {
        CallbackContainer callbackContainer = new CallbackContainer();
        callbackContainer.setKey(command.getCallbackKey());
        callbackContainer.setListener(command.getCallbackClass());
        callbackContainer.setResult(getResult(result));
        Method targetMethod = getTargetMethod(command);
        callbackContainer.setResultClass(primitiveToWrapper(targetMethod.getReturnType()).getName());
        return callbackContainer;
    }

    public static boolean processCallbackContainer(CallbackContainer callbackContainer) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        String key = callbackContainer.getKey();
        Command command = FinalizationWorker.getEventsToConsume().remove(callbackContainer.getKey());
        if (Objects.nonNull(command)) {
            Class<?> callbackClass = Class.forName(callbackContainer.getListener());
            Object callBackBean = context.getBean(callbackClass);
            Method onErrorMethod = callbackClass.getMethod("onError", String.class, Throwable.class);
            Object result = callbackContainer.getResult();
            Class<?> resultClazz = Class.forName(callbackContainer.getResultClass());
            if (result instanceof ExceptionHolder) {
                onErrorMethod.invoke(callBackBean, key, new JaffaRpcExecutionException(((ExceptionHolder) result).getStackTrace()));
            } else if (result instanceof Throwable) {
                if (!Serializer.isKryo) {
                    onErrorMethod.invoke(callBackBean, key, new JaffaRpcExecutionException((Throwable) result));
                } else {
                    throw new JaffaRpcSystemException("Same serialization protocol must be enabled cluster-wide!");
                }
            } else {
                Method method = callbackClass.getMethod("onSuccess", String.class, resultClazz);
                if (resultClazz.equals(Void.class)) {
                    method.invoke(callBackBean, key, null);
                } else
                    method.invoke(callBackBean, key, result);
            }
            AdminServer.addMetric(command);
            return true;
        } else {
            log.warn("Response {} already expired", callbackContainer.getKey());
            return false;
        }
    }
}
