package dev.ua.theroer.magicutils.logger;

import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.Platform;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Handles recipient selection and thread-safe delivery for logger messages.
 */
public final class LogDispatcher {
    private LogDispatcher() {
    }

    /**
     * Resolves concrete recipients based on provided audience(s) and broadcast flag.
     *
     * @param audience direct audience recipient
     * @param audiences collection of audiences to include
     * @param broadcast whether message should be sent to all audiences
     * @param target target channel (chat or console)
     * @param platform platform adapter for online audience snapshots
     * @return collection of audiences to deliver chat messages to
     */
    public static Collection<Audience> determineRecipients(
            Audience audience,
            Collection<? extends Audience> audiences,
            boolean broadcast,
            LogTarget target,
            Platform platform) {

        if (target == LogTarget.CONSOLE) {
            return Collections.emptyList();
        }

        if (broadcast && platform != null) {
            return new ArrayList<>(platform.onlinePlayers());
        }

        List<Audience> recipients = new ArrayList<>();
        if (audience != null) {
            recipients.add(audience);
        }
        if (audiences != null && !audiences.isEmpty()) {
            recipients.addAll(audiences);
        }
        return recipients;
    }

    /**
     * Sends the component to console and/or chat in a thread-safe way.
     *
     * @param platform platform adapter
     * @param component message component to send
     * @param recipients resolved chat recipients (ignored for console-only)
     * @param target LogTarget describing where to deliver
     */
    public static void deliver(Platform platform, Component component, Collection<Audience> recipients, LogTarget target) {
        if (platform == null) {
            return;
        }

        if (target == LogTarget.CONSOLE || target == LogTarget.BOTH) {
            Audience console = platform.console();
            if (console != null) {
                console.send(component);
            }
        }

        if (target == LogTarget.CHAT || target == LogTarget.BOTH) {
            if (recipients == null || recipients.isEmpty()) {
                return;
            }
            Runnable deliver = () -> recipients.forEach(a -> a.send(component));
            if (platform.isMainThread()) {
                deliver.run();
            } else {
                platform.runOnMain(deliver);
            }
        }
    }
}
