package dev.ua.theroer.magicutils.messaging;

/**
 * Callback invoked for each message received on a subscribed channel.
 *
 * @param <T> payload type
 */
@FunctionalInterface
public interface MessageHandler<T> {
    /**
     * Handles a received message.
     *
     * @param message received message with lazy payload decoding
     */
    void onMessage(IncomingMessage<T> message);
}
