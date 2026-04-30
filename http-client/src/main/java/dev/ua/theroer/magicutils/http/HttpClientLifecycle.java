package dev.ua.theroer.magicutils.http;

import dev.ua.theroer.magicutils.platform.PlatformLogger;
import java.lang.reflect.Method;
import java.net.http.HttpClient;

final class HttpClientLifecycle {
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
        Method method = resolveMethod(client, methodName);
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

    private static Method resolveMethod(HttpClient client, String methodName) {
        try {
            return client.getClass().getMethod(methodName);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
