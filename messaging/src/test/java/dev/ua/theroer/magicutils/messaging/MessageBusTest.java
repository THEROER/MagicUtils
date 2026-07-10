package dev.ua.theroer.magicutils.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class MessageBusTest {
    record Greeting(String text) {
    }

    /** Wires two buses (a proxy and a backend) over linked loopback transports. */
    private static final class Pair implements AutoCloseable {
        final MessageBus proxy;
        final MessageBus backend;

        Pair(MessageSource proxySource, MessageSource backendSource) {
            LoopbackTransport proxyTransport = new LoopbackTransport();
            LoopbackTransport backendTransport = new LoopbackTransport();
            proxyTransport.link(backendTransport);
            this.proxy = MessageBus.builder(proxySource, proxyTransport).build();
            this.backend = MessageBus.builder(backendSource, backendTransport).build();
        }

        @Override
        public void close() {
            proxy.close();
            backend.close();
        }
    }

    @Test
    void backendReceivesProxyBroadcast() {
        MessageSource proxy = MessageSource.proxy("proxy");
        MessageSource backend = MessageSource.backend("b1", "lobby");
        try (Pair pair = new Pair(proxy, backend)) {
            List<String> received = new CopyOnWriteArrayList<>();
            pair.backend.subscribe("greet", Greeting.class, msg -> received.add(msg.payload().text()));

            pair.proxy.broadcast("greet", new Greeting("hi"));

            assertEquals(List.of("hi"), received);
        }
    }

    @Test
    void senderDoesNotReceiveOwnMessage() {
        MessageSource proxy = MessageSource.proxy("proxy");
        MessageSource backend = MessageSource.backend("b1", "lobby");
        try (Pair pair = new Pair(proxy, backend)) {
            List<String> received = new CopyOnWriteArrayList<>();
            pair.proxy.subscribe("greet", Greeting.class, msg -> received.add(msg.payload().text()));

            pair.proxy.broadcast("greet", new Greeting("echo?"));

            assertTrue(received.isEmpty(), "sender must not receive its own broadcast");
        }
    }

    @Test
    void proxyTargetIsRejectedByBackend() {
        MessageSource proxy = MessageSource.proxy("proxy");
        MessageSource backend = MessageSource.backend("b1", "lobby");
        try (Pair pair = new Pair(proxy, backend)) {
            List<String> received = new CopyOnWriteArrayList<>();
            pair.backend.subscribe("c", Greeting.class, msg -> received.add(msg.payload().text()));

            pair.proxy.publish(Target.proxy(), "c", new Greeting("for-proxy"));

            assertTrue(received.isEmpty(), "backend must ignore proxy-targeted messages");
        }
    }

    @Test
    void serverTargetMatchesByName() {
        MessageSource proxy = MessageSource.proxy("proxy");
        MessageSource lobby = MessageSource.backend("b1", "lobby");
        try (Pair pair = new Pair(proxy, lobby)) {
            List<String> received = new CopyOnWriteArrayList<>();
            pair.backend.subscribe("c", Greeting.class, msg -> received.add(msg.payload().text()));

            pair.proxy.publish(Target.server("survival"), "c", new Greeting("nope"));
            assertTrue(received.isEmpty(), "wrong server name must not match");

            pair.proxy.publish(Target.server("lobby"), "c", new Greeting("yes"));
            assertEquals(List.of("yes"), received);
        }
    }

    @Test
    void playerTargetUsesHostsPredicate() {
        MessageSource proxy = MessageSource.proxy("proxy");
        MessageSource backend = MessageSource.backend("b1", "lobby");
        UUID hosted = UUID.randomUUID();
        try (Pair pair = new Pair(proxy, backend)) {
            pair.backend.hostsPlayer(hosted::equals);
            List<String> received = new CopyOnWriteArrayList<>();
            pair.backend.subscribe("c", Greeting.class, msg -> received.add(msg.payload().text()));

            pair.proxy.publish(Target.player(UUID.randomUUID()), "c", new Greeting("other"));
            assertTrue(received.isEmpty(), "non-hosted player must not match");

            pair.proxy.publish(Target.player(hosted), "c", new Greeting("mine"));
            assertEquals(List.of("mine"), received);
        }
    }

    @Test
    void unsubscribeStopsDelivery() {
        MessageSource proxy = MessageSource.proxy("proxy");
        MessageSource backend = MessageSource.backend("b1", "lobby");
        try (Pair pair = new Pair(proxy, backend)) {
            List<String> received = new CopyOnWriteArrayList<>();
            var handle = pair.backend.subscribe("c", Greeting.class, msg -> received.add(msg.payload().text()));

            pair.proxy.broadcast("c", new Greeting("one"));
            handle.close();
            pair.proxy.broadcast("c", new Greeting("two"));

            assertEquals(List.of("one"), received);
        }
    }
}
