package dev.ua.theroer.magicutils.platform.fabric;

import dev.ua.theroer.magicutils.reflect.ReflectiveAccess;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;

final class FabricReflectionMappings {
    static final String NAMED_NAMESPACE = "named";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private FabricReflectionMappings() {
    }

    static void install() {
        if (INSTALLED.compareAndSet(false, true)) {
            ReflectiveAccess.registerNameResolver(new Resolver());
        }
    }

    private static Optional<MappingResolver> mappingResolver() {
        try {
            return Optional.ofNullable(FabricLoader.getInstance().getMappingResolver());
        } catch (RuntimeException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    private static final class Resolver implements ReflectiveAccess.NameResolver {
        @Override
        public Optional<String> mapClassName(String namespace, String className) {
            if (!isSupported(namespace)) {
                return Optional.empty();
            }
            return mappingResolver().map(resolver -> resolver.mapClassName(namespace, className));
        }

        @Override
        public Optional<String> mapMethodName(
                String namespace,
                String ownerClassName,
                String methodName,
                String descriptor
        ) {
            if (!isSupported(namespace) || descriptor == null || descriptor.isBlank()) {
                return Optional.empty();
            }
            return mappingResolver().map(resolver ->
                    resolver.mapMethodName(namespace, ownerClassName, methodName, descriptor)
            );
        }

        @Override
        public Optional<String> mapFieldName(
                String namespace,
                String ownerClassName,
                String fieldName,
                String descriptor
        ) {
            if (!isSupported(namespace) || descriptor == null || descriptor.isBlank()) {
                return Optional.empty();
            }
            return mappingResolver().map(resolver ->
                    resolver.mapFieldName(namespace, ownerClassName, fieldName, descriptor)
            );
        }

        private static boolean isSupported(String namespace) {
            return namespace != null && !namespace.isBlank();
        }
    }
}
