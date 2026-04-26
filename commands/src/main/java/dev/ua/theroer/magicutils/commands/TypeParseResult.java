package dev.ua.theroer.magicutils.commands;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Detailed result for command argument parsing.
 *
 * @param status parse status
 * @param value parsed value
 * @param <T> parsed value type
 */
public record TypeParseResult<T>(@NotNull Status status, @Nullable T value) {

    /**
     * Detailed parsing states.
     */
    public enum Status {
        /** Argument parsed successfully. */
        SUCCESS,
        /** Argument was not provided in the input. */
        MISSING,
        /** Argument was provided but failed validation or parsing. */
        INVALID
    }

    /**
     * Creates a successful parse result.
     *
     * @param value parsed value
     * @param <T> value type
     * @return success result
     */
    public static <T> TypeParseResult<T> success(@Nullable T value) {
        return new TypeParseResult<>(Status.SUCCESS, value);
    }

    /**
     * Creates a missing-value result.
     *
     * @param <T> value type
     * @return missing result
     */
    public static <T> TypeParseResult<T> missing() {
        return new TypeParseResult<>(Status.MISSING, null);
    }

    /**
     * Creates an invalid-value result.
     *
     * @param <T> value type
     * @return invalid result
     */
    public static <T> TypeParseResult<T> invalid() {
        return new TypeParseResult<>(Status.INVALID, null);
    }

    /**
     * Returns true when parsing succeeded.
     *
     * @return success flag
     */
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    /**
     * Returns true when the input was not provided.
     *
     * @return missing flag
     */
    public boolean isMissing() {
        return status == Status.MISSING;
    }

    /**
     * Returns true when the input was provided but invalid.
     *
     * @return invalid flag
     */
    public boolean isInvalid() {
        return status == Status.INVALID;
    }
}
