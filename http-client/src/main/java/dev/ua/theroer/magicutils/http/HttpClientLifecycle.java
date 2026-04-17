package dev.ua.theroer.magicutils.http;

import dev.ua.theroer.magicutils.platform.PlatformLogger;
import java.lang.reflect.Method;
import java.net.http.HttpClient;

final class HttpClientLifecycle {
    private static final Method CLOSE_METHOD = resolveMethod("close");
    private static final Method SHUTDOWN_METHOD = resolveMethod("shutdown");

    private HttpClientLifecycle() {
    }

    static void closeOwnedClient(HttpClient client, PlatformLogger logger, String failureMessage) {
        if (client == null) {
            return;
        }
        if (invokeNoArg(client, "close", logger, failureMessage)) {
            return;
        }
        invokeNoArg(client, "shutdown", logger, failureMessage);
    }

    private static boolean invokeNoArg(
            HttpClient client,
            String methodName,
            PlatformLogger logger,
            String failureMessage
    ) {
        Method method = switch (methodName) {
            case "close" -> CLOSE_METHOD;
            case "shutdown" -> SHUTDOWN_METHOD;
            default -> resolveMethod(methodName);
        };
        if (method == null) {
            return false;
        }
        try {
            method.invoke(client);
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            if (logger != null) {
                logger.warn(failureMessage, error);
            }
            return true;
        }
    }

    private static Method resolveMethod(String methodName) {
        try {
            return HttpClient.class.getMethod(methodName);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
