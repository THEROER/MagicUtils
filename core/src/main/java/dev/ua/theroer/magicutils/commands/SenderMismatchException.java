package dev.ua.theroer.magicutils.commands;

import lombok.Getter;

/**
 * Thrown when the executing sender does not match the required sender type.
 */
public class SenderMismatchException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    /** Human-readable description of expected sender. */
    @Getter
    private final String expected;

    /**
     * Create a new exception describing the expected sender type.
     *
     * @param expected description of required sender
     */
    public SenderMismatchException(String expected) {
        super(expected);
        this.expected = expected;
    }
}
