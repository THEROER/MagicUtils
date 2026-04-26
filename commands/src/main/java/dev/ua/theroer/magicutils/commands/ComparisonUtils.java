package dev.ua.theroer.magicutils.commands;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Helper methods for comparing argument values without Bukkit dependencies.
 */
public final class ComparisonUtils {

    private static final ClassValue<Method> UUID_METHOD = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            try {
                return type.getMethod("getUniqueId");
            } catch (NoSuchMethodException e) {
                try {
                    return type.getMethod("getUuid");
                } catch (NoSuchMethodException e2) {
                    return null;
                }
            }
        }
    };

    private static final ClassValue<Method> NAME_METHOD = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            try {
                return type.getMethod("getName");
            } catch (NoSuchMethodException e) {
                return null;
            }
        }
    };

    private static final ClassValue<Method> GET_STRING_METHOD = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            try {
                return type.getMethod("getString");
            } catch (NoSuchMethodException e) {
                return null;
            }
        }
    };

    private ComparisonUtils() {
    }

    /**
     * Compare two objects using the configured mode.
     *
     * @param first left value
     * @param second right value
     * @param mode compare strategy
     * @return true if equal under the chosen mode
     */
    public static boolean isEqual(Object first, Object second, CompareMode mode) {
        if (first == null || second == null) {
            return first == second;
        }
        if (mode == CompareMode.EQUALS) {
            return first.equals(second);
        }
        if (mode == CompareMode.UUID || mode == CompareMode.AUTO) {
            UUID aUuid = extractUuid(first);
            UUID bUuid = extractUuid(second);
            if (aUuid != null && bUuid != null) {
                return aUuid.equals(bUuid);
            }
            if (mode == CompareMode.UUID) {
                return false;
            }
        }
        if (mode == CompareMode.NAME || mode == CompareMode.AUTO) {
            String aName = extractName(first);
            String bName = extractName(second);
            if (aName != null && bName != null) {
                return aName.equalsIgnoreCase(bName);
            }
            if (mode == CompareMode.NAME) {
                return false;
            }
        }
        return first.equals(second);
    }

    /**
     * Check if a value represents the sender using the chosen mode.
     *
     * @param sender command sender
     * @param value value to compare
     * @param mode compare strategy
     * @return true if matches sender
     */
    public static boolean isSender(Object sender, Object value, CompareMode mode) {
        return isEqual(value, sender, mode);
    }

    /**
     * Extract UUID via common patterns (UUID, OfflinePlayer, getUniqueId).
     *
     * @param obj target object
     * @return UUID or null
     */
    public static UUID extractUuid(Object obj) {
        if (obj instanceof UUID uuid) {
            return uuid;
        }
        Method method = UUID_METHOD.get(obj.getClass());
        if (method != null) {
            try {
                Object res = method.invoke(obj);
                if (res instanceof UUID uuidRes) {
                    return uuidRes;
                }
            } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    /**
     * Extract name via getName() or toString().
     *
     * @param obj target object
     * @return name or null
     */
    public static String extractName(Object obj) {
        Method method = NAME_METHOD.get(obj.getClass());
        if (method != null) {
            try {
                Object res = method.invoke(obj);
                if (res instanceof String s) {
                    return s;
                }
                if (res != null) {
                    Method getString = GET_STRING_METHOD.get(res.getClass());
                    if (getString != null) {
                        Object strRes = getString.invoke(res);
                        if (strRes instanceof String s) {
                            return s;
                        }
                    }
                }
            } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            }
        }
        return obj != null ? obj.toString() : null;
    }
}
