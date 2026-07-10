package dev.ua.theroer.magicutils.messaging.redis;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.ua.theroer.magicutils.messaging.MessageCodec;
import dev.ua.theroer.magicutils.messaging.MessageEnvelope;
import dev.ua.theroer.magicutils.messaging.MessageTransport;
import dev.ua.theroer.magicutils.platform.PlatformLogger;

import redis.clients.jedis.BinaryJedisPubSub;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;

/**
 * Redis pub/sub messaging transport (backed by Jedis).
 *
 * <p>Publishing borrows a pooled connection and issues {@code PUBLISH channel bytes};
 * receiving runs a dedicated daemon thread blocked in {@code SUBSCRIBE}, reconnecting
 * with backoff when the connection drops. Redis fans a message out to every
 * subscriber, so {@link MessageEnvelope} carries the {@link dev.ua.theroer.magicutils.messaging.Target}
 * and the bus filters on receipt.</p>
 *
 * <p>This class references Jedis types directly and is only loaded when the Redis
 * transport is selected, keeping Jedis an optional dependency of the messaging
 * module.</p>
 */
public final class RedisMessageTransport implements MessageTransport {
    private final RedisConfig.Redis config;
    private final MessageCodec codec;
    private final PlatformLogger logger;
    private final byte[] channelBytes;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile JedisPool pool;
    private volatile Subscriber subscriber;
    private volatile Thread subscriberThread;
    private volatile EnvelopeSink sink;

    /**
     * Creates a Redis transport from resolved settings.
     *
     * @param config redis settings
     * @param codec envelope codec
     * @param logger platform logger
     */
    public RedisMessageTransport(RedisConfig.Redis config, MessageCodec codec, PlatformLogger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.channelBytes = config.getChannel().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public String name() {
        return "redis";
    }

    @Override
    public void start(EnvelopeSink sink) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        this.sink = Objects.requireNonNull(sink, "sink");
        this.pool = buildPool();

        Thread thread = new Thread(this::runSubscriber, "MagicUtils-Redis-Subscriber");
        thread.setDaemon(true);
        this.subscriberThread = thread;
        thread.start();
    }

    @Override
    public void publish(MessageEnvelope envelope) {
        JedisPool current = pool;
        if (!running.get() || current == null) {
            throw new IllegalStateException("Redis transport is not running");
        }
        byte[] bytes = codec.encode(envelope);
        try (Jedis jedis = current.getResource()) {
            jedis.publish(channelBytes, bytes);
        }
    }

    @Override
    public boolean isConnected() {
        JedisPool current = pool;
        return running.get() && current != null && !current.isClosed();
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        Subscriber current = subscriber;
        if (current != null && current.isSubscribed()) {
            try {
                current.unsubscribe();
            } catch (RuntimeException ignored) {
                // best effort
            }
        }
        Thread thread = subscriberThread;
        if (thread != null) {
            thread.interrupt();
        }
        JedisPool current2 = pool;
        if (current2 != null) {
            current2.close();
        }
        pool = null;
        subscriber = null;
        subscriberThread = null;
    }

    private JedisPool buildPool() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(Math.max(1, config.getPoolMaxTotal()));
        poolConfig.setTestOnBorrow(true);

        DefaultJedisClientConfig.Builder clientConfig = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(config.getTimeoutMillis())
                .socketTimeoutMillis(config.getTimeoutMillis())
                .database(config.getDatabase())
                .ssl(config.isSsl());
        if (!config.getPassword().isBlank()) {
            clientConfig.password(config.getPassword());
        }
        if (!config.getUsername().isBlank()) {
            clientConfig.user(config.getUsername());
        }
        HostAndPort address = new HostAndPort(config.getHost(), config.getPort());
        return new JedisPool(poolConfig, address, clientConfig.build());
    }

    private void runSubscriber() {
        long backoffMillis = 500L;
        while (running.get()) {
            Subscriber current = new Subscriber();
            this.subscriber = current;
            try (Jedis jedis = pool.getResource()) {
                backoffMillis = 500L;
                jedis.subscribe(current, channelBytes);
            } catch (RuntimeException error) {
                if (!running.get()) {
                    return;
                }
                logger.warn("Redis subscriber disconnected, reconnecting in " + backoffMillis + "ms", error);
            }
            if (!running.get()) {
                return;
            }
            try {
                Thread.sleep(backoffMillis);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            backoffMillis = Math.min(backoffMillis * 2, 10_000L);
        }
    }

    private void onRedisMessage(byte[] payload) {
        EnvelopeSink target = sink;
        if (target == null) {
            return;
        }
        try {
            MessageEnvelope envelope = codec.decode(payload);
            target.accept(envelope);
        } catch (RuntimeException error) {
            logger.warn("Failed to decode message from Redis", error);
        }
    }

    private final class Subscriber extends BinaryJedisPubSub {
        @Override
        public void onMessage(byte[] channel, byte[] message) {
            onRedisMessage(message);
        }
    }
}
