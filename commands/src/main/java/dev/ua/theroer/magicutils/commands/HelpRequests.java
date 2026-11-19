package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static dev.ua.theroer.magicutils.commands.HelpCommandSupport.getSubCommandActions;
import static dev.ua.theroer.magicutils.commands.HelpCommandSupport.resolveCommandInfo;
import static dev.ua.theroer.magicutils.commands.HelpCommandSupport.safeTrim;

/**
 * Parses help arguments into a request: target command/subcommand lookup, query and page.
 */
final class HelpRequests {

    private HelpRequests() {
    }

    record HelpRequest(MagicCommand detailTarget,
                       CommandManager.CommandAction<?> detailSub,
                       String query,
                       int page) {
    }

    static HelpRequest parseRequest(CommandManager<?> manager, String commandName, String subCommand) {
        String first = safeTrim(commandName);
        String second = safeTrim(subCommand);

        if (first == null) {
            return new HelpRequest(null, null, "", 1);
        }

        Integer page = parsePage(first);
        if (page != null) {
            String query = second != null ? second : "";
            return new HelpRequest(null, null, query, page);
        }

        MagicCommand target = findCommand(manager, first);
        if (target != null) {
            if (second != null) {
                CommandManager.CommandAction<?> sub = findSubCommand(manager, target, second);
                if (sub != null) {
                    return new HelpRequest(target, sub, "", 1);
                }
                Integer subPage = parsePage(second);
                if (subPage != null) {
                    return new HelpRequest(null, null, first, subPage);
                }
            }
            return new HelpRequest(target, null, "", 1);
        }

        Integer secondPage = parsePage(second);
        if (secondPage != null) {
            return new HelpRequest(null, null, first, secondPage);
        }

        String query = second != null ? first + " " + second : first;
        return new HelpRequest(null, null, query, 1);
    }

    static HelpRequest parseRequest(List<CommandManager<?>> managers, String commandName, String subCommand) {
        String first = safeTrim(commandName);
        String second = safeTrim(subCommand);

        if (first == null) {
            return new HelpRequest(null, null, "", 1);
        }

        Integer page = parsePage(first);
        if (page != null) {
            String query = second != null ? second : "";
            return new HelpRequest(null, null, query, page);
        }

        for (CommandManager<?> manager : managers) {
            MagicCommand target = findCommand(manager, first);
            if (target != null) {
                if (second != null) {
                    CommandManager.CommandAction<?> sub = findSubCommand(manager, target, second);
                    if (sub != null) {
                        return new HelpRequest(target, sub, "", 1);
                    }
                    Integer subPage = parsePage(second);
                    if (subPage != null) {
                        return new HelpRequest(null, null, first, subPage);
                    }
                }
                return new HelpRequest(target, null, "", 1);
            }
        }

        Integer secondPage = parsePage(second);
        if (secondPage != null) {
            return new HelpRequest(null, null, first, secondPage);
        }

        String query = second != null ? first + " " + second : first;
        return new HelpRequest(null, null, query, 1);
    }

    static MagicCommand findCommand(CommandManager<?> manager, String commandName) {
        String lookup = commandName.toLowerCase(Locale.ROOT);
        for (MagicCommand cmd : manager.getAll()) {
            CommandInfo info = resolveCommandInfo(manager, cmd);
            if (info == null) {
                continue;
            }
            if (info.name().equalsIgnoreCase(lookup)) {
                return cmd;
            }
            for (String alias : info.aliases()) {
                if (alias.equalsIgnoreCase(lookup)) {
                    return cmd;
                }
            }
        }
        return null;
    }

    static CommandManager.CommandAction<?> findSubCommand(CommandManager<?> manager,
                                                          MagicCommand command,
                                                          String name) {
        if (manager == null || command == null || name == null || name.isEmpty()) {
            return null;
        }
        List<String> segments = splitSubCommandPath(name);
        for (CommandManager.CommandAction<?> sub : getSubCommandActions(manager, command)) {
            if (matchesSubCommandPath(sub, segments)) {
                return sub;
            }
        }
        return null;
    }

    static CommandManager.CommandAction<?> findSubCommand(List<CommandManager<?>> managers,
                                                          MagicCommand command,
                                                          String name) {
        if (managers == null || managers.isEmpty() || command == null || name == null || name.isEmpty()) {
            return null;
        }
        for (CommandManager<?> manager : managers) {
            CommandManager.CommandAction<?> sub = findSubCommand(manager, command, name);
            if (sub != null) {
                return sub;
            }
        }
        return null;
    }

    static List<String> splitSubCommandPath(String raw) {
        if (raw == null) {
            return List.of();
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        String[] parts = trimmed.split("\\s+");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            result.add(part.trim());
        }
        return result;
    }

    static boolean matchesSubCommandPath(CommandManager.CommandAction<?> sub, List<String> segments) {
        if (sub == null || segments == null || segments.isEmpty()) {
            return false;
        }
        List<String> full = sub.fullPathSegments();
        if (full.size() != segments.size()) {
            return false;
        }
        int lastIndex = full.size() - 1;
        for (int i = 0; i < full.size(); i++) {
            String expected = full.get(i);
            String actual = segments.get(i);
            if (expected == null || actual == null) {
                return false;
            }
            if (i == lastIndex) {
                if (expected.equalsIgnoreCase(actual)) {
                    continue;
                }
                boolean aliasMatch = false;
                for (String alias : sub.aliases()) {
                    if (alias != null && alias.equalsIgnoreCase(actual)) {
                        aliasMatch = true;
                        break;
                    }
                }
                if (!aliasMatch) {
                    return false;
                }
            } else if (!expected.equalsIgnoreCase(actual)) {
                return false;
            }
        }
        return true;
    }

    static Integer parsePage(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        try {
            int value = Integer.parseInt(input);
            return value > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
