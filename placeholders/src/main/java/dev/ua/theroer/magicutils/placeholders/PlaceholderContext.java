package dev.ua.theroer.magicutils.placeholders;

import dev.ua.theroer.magicutils.platform.Audience;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Context for placeholder rendering.
 */
public final class PlaceholderContext {
    private final Audience audience;
    private final Object ownerKey;
    private final String defaultNamespace;
    private final String argumentSeparator;
    private final Map<String, String> inline;

    private PlaceholderContext(Builder builder) {
        this.audience = builder.audience;
        this.ownerKey = builder.ownerKey;
        this.defaultNamespace = builder.defaultNamespace;
        this.argumentSeparator = builder.argumentSeparator;
        this.inline = builder.inline != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(builder.inline))
                : Collections.emptyMap();
    }

    /**
     * Returns the audience tied to rendering.
     *
     * @return audience or null
     */
    public @Nullable Audience audience() {
        return audience;
    }

    /**
     * Returns the owner key for local placeholder scope.
     *
     * @return owner key or null
     */
    public @Nullable Object ownerKey() {
        return ownerKey;
    }

    /**
     * Returns the default namespace used for unqualified placeholders.
     *
     * @return namespace or null
     */
    public @Nullable String defaultNamespace() {
        return defaultNamespace;
    }

    /**
     * Returns the argument separator for unqualified placeholders.
     *
     * @return argument separator or null
     */
    public @Nullable String argumentSeparator() {
        return argumentSeparator;
    }

    /**
     * Returns inline placeholder values.
     *
     * @return inline placeholder map (never null)
     */
    public Map<String, String> inline() {
        return inline;
    }

    /**
     * Creates a builder for placeholder contexts.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link PlaceholderContext}.
     */
    public static final class Builder {
        private Audience audience;
        private Object ownerKey;
        private String defaultNamespace;
        private String argumentSeparator;
        private Map<String, String> inline;

        private Builder() {
        }

        /**
         * Sets the audience for resolution.
         *
         * @param audience audience to use
         * @return this builder
         */
        public Builder audience(@Nullable Audience audience) {
            this.audience = audience;
            return this;
        }

        /**
         * Sets an owner key for local placeholder scope.
         *
         * @param ownerKey owner key
         * @return this builder
         */
        public Builder ownerKey(@Nullable Object ownerKey) {
            this.ownerKey = ownerKey;
            return this;
        }

        /**
         * Sets the default namespace for unqualified placeholders.
         *
         * @param defaultNamespace namespace id
         * @return this builder
         */
        public Builder defaultNamespace(@Nullable String defaultNamespace) {
            this.defaultNamespace = defaultNamespace;
            return this;
        }

        /**
         * Sets the argument separator for unqualified placeholders.
         *
         * @param argumentSeparator separator string (empty disables)
         * @return this builder
         */
        public Builder argumentSeparator(@Nullable String argumentSeparator) {
            this.argumentSeparator = argumentSeparator;
            return this;
        }

        /**
         * Adds inline placeholder values.
         *
         * @param inline inline map
         * @return this builder
         */
        public Builder inline(@Nullable Map<String, String> inline) {
            this.inline = inline;
            return this;
        }

        /**
         * Builds the placeholder context.
         *
         * @return context instance
         */
        public PlaceholderContext build() {
            return new PlaceholderContext(this);
        }
    }
}
