package dev.ua.theroer.magicutils.bootstrap;

import dev.ua.theroer.magicutils.reflect.ReflectiveAccess;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectiveAccessTest {
    private static final String TEST_NAMESPACE = "reflective-access-test";

    @Test
    void loadClassFallsBackToOwnClassLoaderWhenContextLoaderMisses() {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(new ClassLoader(null) {
        });
        try {
            Optional<Class<?>> loaded = ReflectiveAccess.loadClass(ReflectiveAccess.class.getName());

            assertTrue(loaded.isPresent());
            assertSame(ReflectiveAccess.class, loaded.orElseThrow());
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    @Test
    void mappedLookupsUseRegisteredResolvers() {
        ReflectiveAccess.registerNameResolver(new TestNameResolver());

        Class<?> type = ReflectiveAccess.loadMappedClass(TEST_NAMESPACE, "example.Target").orElseThrow();
        Method method = ReflectiveAccess.publicMappedMethod(
                type,
                TEST_NAMESPACE,
                "example.Target",
                "mappedMethod",
                "()Ljava/lang/String;"
        ).orElseThrow();
        Field field = ReflectiveAccess.publicMappedField(
                type,
                TEST_NAMESPACE,
                "example.Target",
                "MAPPED_FIELD",
                "Ljava/lang/String;"
        ).orElseThrow();

        assertSame(Target.class, type);
        assertEquals("method", ReflectiveAccess.invoke(method, new Target()).orElseThrow());
        assertEquals("field", ReflectiveAccess.readField(field, null).orElseThrow());
    }

    public static final class Target {
        public static final String ACTUAL_FIELD = "field";

        public String actualMethod() {
            return "method";
        }
    }

    private static final class TestNameResolver implements ReflectiveAccess.NameResolver {
        @Override
        public Optional<String> mapClassName(String namespace, String className) {
            if (TEST_NAMESPACE.equals(namespace) && "example.Target".equals(className)) {
                return Optional.of(Target.class.getName());
            }
            return Optional.empty();
        }

        @Override
        public Optional<String> mapMethodName(
                String namespace,
                String ownerClassName,
                String methodName,
                String descriptor
        ) {
            if (TEST_NAMESPACE.equals(namespace) && "mappedMethod".equals(methodName)) {
                return Optional.of("actualMethod");
            }
            return Optional.empty();
        }

        @Override
        public Optional<String> mapFieldName(
                String namespace,
                String ownerClassName,
                String fieldName,
                String descriptor
        ) {
            if (TEST_NAMESPACE.equals(namespace) && "MAPPED_FIELD".equals(fieldName)) {
                return Optional.of("ACTUAL_FIELD");
            }
            return Optional.empty();
        }
    }
}
