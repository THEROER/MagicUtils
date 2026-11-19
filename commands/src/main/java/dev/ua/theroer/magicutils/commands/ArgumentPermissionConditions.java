package dev.ua.theroer.magicutils.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Evaluates {@link PermissionConditionType} conditions that decide whether an
 * argument's permission must be checked for the parsed values of an invocation.
 *
 * @param <S> sender type
 */
final class ArgumentPermissionConditions<S> {
    private final TypeParserRegistry<S> typeParserRegistry;

    ArgumentPermissionConditions(TypeParserRegistry<S> typeParserRegistry) {
        this.typeParserRegistry = typeParserRegistry;
    }

    boolean shouldCheckPermission(CommandArgument argument, Object value, Map<String, Object> valuesByName,
            Map<String, CommandArgument> argumentsByName, S sender) {
        PermissionConditionType condition = argument.getPermissionCondition();
        String[] names = argument.getPermissionConditionArgs();

        List<ArgValue> targets = collectTargets(argument, value, names, valuesByName, argumentsByName);

        switch (condition) {
            case ALWAYS:
                return true;
            case NOT_NULL:
                return targets.stream().anyMatch(av -> av.value != null);
            case SELF:
                return targets.stream().anyMatch(av -> av.value != null && isSenderMatch(sender, av.meta(), av.value()));
            case OTHER:
            case ANY_OTHER:
                return targets.stream().anyMatch(av -> av.value != null && !isSenderMatch(sender, av.meta(), av.value()));
            case DISTINCT:
                return isDistinct(sender, targets, false);
            case ALL_DISTINCT:
                return isDistinct(sender, targets, true);
            case EXISTS:
                return targets.stream().anyMatch(av -> av.value != null);
            case EQUALS:
                return areAllEqual(sender, targets);
            case NOT_EQUALS:
                return areAllDistinct(sender, targets);
            default:
                return false;
        }
    }

    private List<ArgValue> collectTargets(CommandArgument selfArgument, Object selfValue, String[] names,
            Map<String, Object> valuesByName, Map<String, CommandArgument> argumentsByName) {
        List<ArgValue> targets = new ArrayList<>();
        if (names != null && names.length > 0) {
            for (String name : names) {
                Object val = valuesByName.get(name);
                CommandArgument meta = argumentsByName.getOrDefault(name, selfArgument);
                targets.add(new ArgValue(meta, val));
            }
        } else {
            targets.add(new ArgValue(selfArgument, selfValue));
        }
        return targets;
    }

    private boolean isDistinct(S sender, List<ArgValue> targets, boolean requireAllDistinct) {
        List<ArgValue> nonNull = targets.stream().filter(av -> av.value != null).toList();
        if (nonNull.size() < 2) {
            return false;
        }

        for (int i = 0; i < nonNull.size(); i++) {
            for (int j = i + 1; j < nonNull.size(); j++) {
                ArgValue a = nonNull.get(i);
                ArgValue b = nonNull.get(j);
                boolean equal = areEqual(sender, a.meta(), a.value(), b.meta(), b.value());
                if (requireAllDistinct && equal) {
                    return false;
                }
                if (!requireAllDistinct && !equal) {
                    return true;
                }
            }
        }

        return requireAllDistinct;
    }

    private boolean areAllEqual(S sender, List<ArgValue> targets) {
        List<ArgValue> nonNull = targets.stream().filter(av -> av.value != null).toList();
        if (nonNull.size() < 2) {
            return false;
        }
        ArgValue first = nonNull.get(0);
        for (int i = 1; i < nonNull.size(); i++) {
            ArgValue other = nonNull.get(i);
            if (!areEqual(sender, first.meta(), first.value(), other.meta(), other.value())) {
                return false;
            }
        }
        return true;
    }

    private boolean areAllDistinct(S sender, List<ArgValue> targets) {
        return isDistinct(sender, targets, true);
    }

    private boolean isSenderMatch(S sender, CommandArgument argument, Object value) {
        CompareMode mode = argument.getCompareMode();
        TypeParser<S, ?> parser = typeParserRegistry.findParserForType(argument.getType());
        if (parser != null) {
            try {
                return parser.isSender(sender, value, mode);
            } catch (Exception ignored) {
            }
        }
        return defaultIsSender(sender, value, mode);
    }

    private boolean areEqual(S sender, CommandArgument leftMeta, Object left, CommandArgument rightMeta,
            Object right) {
        CompareMode leftMode = leftMeta != null ? leftMeta.getCompareMode() : CompareMode.AUTO;
        CompareMode rightMode = rightMeta != null ? rightMeta.getCompareMode() : CompareMode.AUTO;

        TypeParser<S, ?> leftParser = leftMeta != null ? typeParserRegistry.findParserForType(leftMeta.getType()) : null;
        if (leftParser != null) {
            try {
                return leftParser.isEqual(sender, left, right, leftMode);
            } catch (Exception ignored) {
            }
        }

        TypeParser<S, ?> rightParser = rightMeta != null ? typeParserRegistry.findParserForType(rightMeta.getType()) : null;
        if (rightParser != null) {
            try {
                return rightParser.isEqual(sender, left, right, rightMode);
            } catch (Exception ignored) {
            }
        }

        return defaultEquals(sender, left, right, leftMode != null ? leftMode : rightMode);
    }

    private boolean defaultIsSender(S sender, Object value, CompareMode mode) {
        return ComparisonUtils.isSender(sender, value, mode);
    }

    private boolean defaultEquals(S sender, Object first, Object second, CompareMode mode) {
        return ComparisonUtils.isEqual(first, second, mode);
    }

    private record ArgValue(CommandArgument meta, Object value) {
    }
}
