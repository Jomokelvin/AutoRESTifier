package dev.autorestifier;

import com.fasterxml.jackson.databind.JsonNode;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EndpointRegistry {
    private final Map<String, Endpoint> endpoints = new ConcurrentHashMap<>();

    public Endpoint expose(String className, String methodName, String descriptor, ClassLoader loader) {
        try {
            Class<?> type = Class.forName(className, false, loader);
            Method found = null;
            for (Method method : type.getDeclaredMethods()) {
                if (method.getName().equals(methodName)
                        && Type.getMethodDescriptor(method).equals(descriptor)) {
                    found = method;
                    break;
                }
            }
            if (found == null) throw new IllegalArgumentException("Method signature was not found in the uploaded class.");
            int modifiers = found.getModifiers();
            if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)) {
                throw new IllegalArgumentException("Only public static methods can be exposed in this MVP.");
            }
            if (Modifier.isAbstract(modifiers) || found.isSynthetic()) {
                throw new IllegalArgumentException("Abstract or synthetic methods cannot be exposed.");
            }
            for (Class<?> parameter : found.getParameterTypes()) {
                if (!isSupported(parameter)) {
                    throw new IllegalArgumentException("Unsupported parameter type: " + parameter.getTypeName()
                            + ". Supported: primitive numeric/boolean/char, wrappers, String.");
                }
            }
            if (found.getReturnType() != void.class && !isSupported(found.getReturnType())) {
                throw new IllegalArgumentException("Unsupported return type: " + found.getReturnType().getTypeName()
                        + ". Supported: primitive numeric/boolean/char, wrappers, String, void.");
            }
            String id = UUID.randomUUID().toString().substring(0, 8);
            Endpoint endpoint = new Endpoint(id, className, methodName, descriptor, found, loader);
            endpoints.put(id, endpoint);
            return endpoint;
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Class not found in uploaded JAR: " + className);
        } catch (LinkageError e) {
            throw new IllegalArgumentException("Could not link the class. Does the JAR require missing dependencies?");
        }
    }

    public Object invoke(String id, JsonNode argsNode) throws Exception {
        Endpoint endpoint = endpoints.get(id);
        if (endpoint == null) throw new IllegalArgumentException("Unknown endpoint ID: " + id);
        if (argsNode == null || !argsNode.isArray()) throw new IllegalArgumentException("Request body must be JSON: {\"args\":[...]}");
        Class<?>[] types = endpoint.method.getParameterTypes();
        if (argsNode.size() != types.length) {
            throw new IllegalArgumentException("Expected " + types.length + " arguments but received " + argsNode.size() + ".");
        }
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) args[i] = convert(argsNode.get(i), types[i], i);
        try {
            Object result = endpoint.method.invoke(null, args);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("endpointId", endpoint.id);
            response.put("result", result);
            return response;
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalArgumentException("Target method threw " + cause.getClass().getSimpleName()
                    + (cause.getMessage() == null ? "" : ": " + cause.getMessage()));
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Could not invoke method: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> allAsMaps() {
        List<Map<String, Object>> result = new ArrayList<>();
        endpoints.values().stream().sorted((a, b) -> a.id.compareTo(b.id)).forEach(e -> result.add(e.toMap()));
        return result;
    }

    public void clear() { endpoints.clear(); }

    private static boolean isSupported(Class<?> c) {
        return c == String.class || c == boolean.class || c == Boolean.class
                || c == byte.class || c == Byte.class || c == short.class || c == Short.class
                || c == int.class || c == Integer.class || c == long.class || c == Long.class
                || c == float.class || c == Float.class || c == double.class || c == Double.class
                || c == char.class || c == Character.class;
    }

    private static Object convert(JsonNode node, Class<?> target, int index) {
        if (node == null || node.isNull()) {
            if (target.isPrimitive()) throw new IllegalArgumentException("Argument " + index + " cannot be null.");
            return null;
        }
        try {
            if (target == String.class) {
                if (!node.isTextual()) throw new IllegalArgumentException("Argument " + index + " must be a string.");
                return node.asText();
            }
            if (target == boolean.class || target == Boolean.class) {
                if (!node.isBoolean()) throw new IllegalArgumentException("Argument " + index + " must be a boolean.");
                return node.booleanValue();
            }
            if (target == char.class || target == Character.class) {
                if (!node.isTextual() || node.asText().length() != 1)
                    throw new IllegalArgumentException("Argument " + index + " must be a one-character string.");
                return node.asText().charAt(0);
            }
            if (!node.isNumber()) throw new IllegalArgumentException("Argument " + index + " must be numeric.");
            if (target == byte.class || target == Byte.class) return node.numberValue().byteValue();
            if (target == short.class || target == Short.class) return node.numberValue().shortValue();
            if (target == int.class || target == Integer.class) return node.numberValue().intValue();
            if (target == long.class || target == Long.class) return node.numberValue().longValue();
            if (target == float.class || target == Float.class) return node.numberValue().floatValue();
            if (target == double.class || target == Double.class) return node.numberValue().doubleValue();
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Argument " + index + " has the wrong JSON type.");
        }
        throw new IllegalArgumentException("Unsupported argument type: " + target.getTypeName());
    }

    public static final class Endpoint {
        final String id, className, methodName, descriptor;
        final Method method;
        @SuppressWarnings("unused") final ClassLoader loader;

        Endpoint(String id, String className, String methodName, String descriptor, Method method, ClassLoader loader) {
            this.id = id;
            this.className = className;
            this.methodName = methodName;
            this.descriptor = descriptor;
            this.method = method;
            this.loader = loader;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("className", className);
            map.put("methodName", methodName);
            map.put("descriptor", descriptor);
            map.put("httpMethod", "POST");
            map.put("path", "/generated/" + id);
            map.put("parameterTypes", java.util.Arrays.stream(method.getParameterTypes()).map(Class::getTypeName).toList());
            map.put("returnType", method.getReturnType().getTypeName());
            return map;
        }
    }
}
