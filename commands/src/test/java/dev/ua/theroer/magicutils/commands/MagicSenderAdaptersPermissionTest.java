package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.platform.Audience;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicSenderAdaptersPermissionTest {
    private static final String ADAPTER_ID = "magic-sender-adapters-permission-test";

    @AfterEach
    void cleanup() {
        MagicSenderAdapters.unregister(ADAPTER_ID);
    }

    @Test
    void usesWrappedSenderDefaultPermissionCheckWhenNoFallbackOverrideIsProvided() {
        Object sender = new Object();
        MagicSenderAdapters.register(ADAPTER_ID, new TestAdapter(sender));

        assertTrue(MagicSender.hasPermission(sender, "default.allowed"));
        assertFalse(MagicSender.hasPermission(sender, "override.allowed"));
    }

    @Test
    void allowsFallbackOverrideWhenAdapterProvidesCustomPermissionHandling() {
        Object sender = new Object();
        MagicSenderAdapters.register(ADAPTER_ID, new TestAdapter(sender));

        assertTrue(MagicSender.hasPermission(sender, "override.allowed", 4));
        assertFalse(MagicSender.hasPermission(sender, "override.allowed", 3));
    }

    private record TestAdapter(Object rawSender) implements MagicSenderAdapter {
        @Override
        public boolean supports(Object sender) {
            return rawSender == sender;
        }

        @Override
        public MagicSender wrap(Object sender) {
            if (rawSender != sender) {
                return null;
            }
            return new MagicSender() {
                @Override
                public Audience audience() {
                    return component -> {
                    };
                }

                @Override
                public String name() {
                    return "test";
                }

                @Override
                public boolean hasPermission(String permission) {
                    return "default.allowed".equals(permission);
                }

                @Override
                public boolean hasPermission(String permission, int fallbackOpLevel) {
                    return "override.allowed".equals(permission) && fallbackOpLevel == 4;
                }

                @Override
                public Object handle() {
                    return rawSender;
                }
            };
        }
    }
}
