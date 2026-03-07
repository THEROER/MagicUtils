package dev.ua.theroer.magicutils.http;

import dev.ua.theroer.magicutils.bootstrap.MagicRuntime;
import dev.ua.theroer.magicutils.bootstrap.MagicRuntimeConfigBinding;
import dev.ua.theroer.magicutils.platform.PlatformLogger;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * High-level runtime profile for named {@link MagicHttpClient} instances.
 *
 * <p>The profile is backed by a {@link MagicRuntimeConfigBinding}, keeps the
 * client registered under a stable runtime name, and rebuilds it automatically
 * when matching config sections change.</p>
 *
 * @param <C> config type driving the profile
 */
public final class MagicHttpClientProfile<C> implements AutoCloseable {
    private final MagicRuntimeConfigBinding<C, MagicHttpClient> binding;

    private MagicHttpClientProfile(MagicRuntimeConfigBinding<C, MagicHttpClient> binding) {
        this.binding = Objects.requireNonNull(binding, "binding");
    }

    /**
     * Creates a profile builder.
     *
     * @param runtime runtime that owns the client
     * @param name runtime component name
     * @param configClass config class driving the client
     * @param <C> config type
     * @return builder instance
     */
    public static <C> Builder<C> builder(MagicRuntime runtime, String name, Class<C> configClass) {
        return new Builder<>(runtime, name, configClass);
    }

    /**
     * Returns the runtime name of this profile.
     *
     * @return profile name
     */
    public String name() {
        return binding.name();
    }

    /**
     * Returns the underlying runtime binding.
     *
     * @return config binding
     */
    public MagicRuntimeConfigBinding<C, MagicHttpClient> binding() {
        return binding;
    }

    /**
     * Returns the current client when available.
     *
     * @return optional current client
     */
    public Optional<MagicHttpClient> current() {
        return binding.current();
    }

    /**
     * Returns the current client or throws when unavailable.
     *
     * @return current client
     */
    public MagicHttpClient require() {
        return binding.require();
    }

    /**
     * Rebuilds the current client from the latest config.
     *
     * @return rebuilt client
     */
    public MagicHttpClient refresh() {
        return binding.refresh();
    }

    @Override
    public void close() {
        binding.close();
    }

    /**
     * Builder for {@link MagicHttpClientProfile}.
     *
     * @param <C> config type
     */
    public static final class Builder<C> {
        private final MagicRuntime runtime;
        private final String name;
        private final Class<C> configClass;
        private final List<String> sections = new ArrayList<>();
        private final List<BiConsumer<C, MagicHttpClient.Builder>> customizers = new ArrayList<>();

        private Builder(MagicRuntime runtime, String name, Class<C> configClass) {
            this.runtime = Objects.requireNonNull(runtime, "runtime");
            this.name = Objects.requireNonNull(name, "name");
            this.configClass = Objects.requireNonNull(configClass, "configClass");
        }

        /**
         * Sets the config sections that should trigger client rebuilds.
         *
         * @param sections reload sections
         * @return builder for chaining
         */
        public Builder<C> sections(String... sections) {
            if (sections == null) {
                return this;
            }
            for (String section : sections) {
                if (section != null && !section.isBlank()) {
                    this.sections.add(section.trim());
                }
            }
            return this;
        }

        /**
         * Applies a builder customization that can read the config instance.
         *
         * @param customizer config-aware builder customizer
         * @return builder for chaining
         */
        public Builder<C> configure(BiConsumer<C, MagicHttpClient.Builder> customizer) {
            customizers.add(Objects.requireNonNull(customizer, "customizer"));
            return this;
        }

        /**
         * Applies a builder customization that does not depend on config.
         *
         * @param customizer builder customizer
         * @return builder for chaining
         */
        public Builder<C> configure(Consumer<MagicHttpClient.Builder> customizer) {
            Objects.requireNonNull(customizer, "customizer");
            return configure((config, builder) -> customizer.accept(builder));
        }

        /**
         * Overrides the logger used by the client.
         *
         * @param loggerProvider logger provider
         * @return builder for chaining
         */
        public Builder<C> logger(Function<C, PlatformLogger> loggerProvider) {
            return configureValue(loggerProvider, MagicHttpClient.Builder::logger);
        }

        /**
         * Sets the base URL from config.
         *
         * @param baseUrlProvider config-driven base URL
         * @return builder for chaining
         */
        public Builder<C> baseUrl(Function<C, String> baseUrlProvider) {
            return configureValue(baseUrlProvider, MagicHttpClient.Builder::baseUrl);
        }

        /**
         * Sets the default User-Agent from config.
         *
         * @param userAgentProvider config-driven user agent
         * @return builder for chaining
         */
        public Builder<C> userAgent(Function<C, String> userAgentProvider) {
            return configureValue(userAgentProvider, MagicHttpClient.Builder::userAgent);
        }

        /**
         * Adds a static header.
         *
         * @param name header name
         * @param value header value
         * @return builder for chaining
         */
        public Builder<C> header(String name, String value) {
            return configure(builder -> builder.header(name, value));
        }

        /**
         * Adds a config-driven header when the value is not null.
         *
         * @param name header name
         * @param valueProvider config-driven header value
         * @return builder for chaining
         */
        public Builder<C> header(String name, Function<C, String> valueProvider) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(valueProvider, "valueProvider");
            return configure((config, builder) -> {
                String value = valueProvider.apply(config);
                if (value != null) {
                    builder.header(name, value);
                }
            });
        }

        /**
         * Adds multiple config-driven headers.
         *
         * @param headersProvider config-driven header map
         * @return builder for chaining
         */
        public Builder<C> headers(Function<C, Map<String, String>> headersProvider) {
            Objects.requireNonNull(headersProvider, "headersProvider");
            return configure((config, builder) -> {
                Map<String, String> headers = headersProvider.apply(config);
                if (headers == null || headers.isEmpty()) {
                    return;
                }
                builder.headers(new LinkedHashMap<>(headers));
            });
        }

        /**
         * Adds an Authorization bearer token when the token is not blank.
         *
         * @param tokenProvider config-driven bearer token
         * @return builder for chaining
         */
        public Builder<C> bearerAuth(Function<C, String> tokenProvider) {
            Objects.requireNonNull(tokenProvider, "tokenProvider");
            return configure((config, builder) -> {
                String token = tokenProvider.apply(config);
                if (token != null && !token.isBlank()) {
                    builder.header("Authorization", "Bearer " + token.trim());
                }
            });
        }

        /**
         * Overrides connect timeout from config.
         *
         * @param timeoutProvider config-driven timeout
         * @return builder for chaining
         */
        public Builder<C> connectTimeout(Function<C, Duration> timeoutProvider) {
            return configureValue(timeoutProvider, MagicHttpClient.Builder::connectTimeout);
        }

        /**
         * Overrides request timeout from config.
         *
         * @param timeoutProvider config-driven timeout
         * @return builder for chaining
         */
        public Builder<C> requestTimeout(Function<C, Duration> timeoutProvider) {
            return configureValue(timeoutProvider, MagicHttpClient.Builder::requestTimeout);
        }

        /**
         * Overrides redirect behavior from config.
         *
         * @param followProvider config-driven redirect toggle
         * @return builder for chaining
         */
        public Builder<C> followRedirects(Function<C, Boolean> followProvider) {
            Objects.requireNonNull(followProvider, "followProvider");
            return configure((config, builder) -> {
                Boolean follow = followProvider.apply(config);
                if (follow != null) {
                    builder.followRedirects(follow);
                }
            });
        }

        /**
         * Overrides HTTP version from config.
         *
         * @param versionProvider config-driven version
         * @return builder for chaining
         */
        public Builder<C> version(Function<C, HttpClient.Version> versionProvider) {
            return configureValue(versionProvider, MagicHttpClient.Builder::version);
        }

        /**
         * Builds the profile and binds it into the runtime.
         *
         * @return bound HTTP client profile
         */
        public MagicHttpClientProfile<C> build() {
            MagicRuntimeConfigBinding<C, MagicHttpClient> binding = runtime.bindConfig(
                    name,
                    configClass,
                    config -> {
                        MagicHttpClient.Builder builder = MagicHttpClient.builder(
                                runtime.platform(),
                                runtime.configManager()
                        );
                        for (BiConsumer<C, MagicHttpClient.Builder> customizer : customizers) {
                            customizer.accept(config, builder);
                        }
                        return builder.build();
                    },
                    sections.toArray(String[]::new)
            );
            return new MagicHttpClientProfile<>(binding);
        }

        private <T> Builder<C> configureValue(
                Function<C, T> valueProvider,
                BiConsumer<MagicHttpClient.Builder, T> applier
        ) {
            Objects.requireNonNull(valueProvider, "valueProvider");
            Objects.requireNonNull(applier, "applier");
            return configure((config, builder) -> {
                T value = valueProvider.apply(config);
                if (value != null) {
                    applier.accept(builder, value);
                }
            });
        }
    }
}
