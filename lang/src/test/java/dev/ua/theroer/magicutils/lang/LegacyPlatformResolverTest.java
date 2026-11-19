package dev.ua.theroer.magicutils.lang;

import dev.ua.theroer.magicutils.platform.Platform;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyPlatformResolverTest {

    @Test
    void platformIsReturnedAsIs() {
        Platform platform = (Platform) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {Platform.class}, (proxy, method, args) -> null);

        assertSame(platform, LegacyPlatformResolver.resolve(platform));
    }

    @Test
    void anythingElseWithoutABukkitProviderIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> LegacyPlatformResolver.resolve(null));
        assertThrows(IllegalArgumentException.class, () -> LegacyPlatformResolver.resolve(new Object()));
    }
}
