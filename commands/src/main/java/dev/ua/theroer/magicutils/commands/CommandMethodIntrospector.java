package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.*;
import dev.ua.theroer.magicutils.lang.InternalMessages;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads the annotation-declared shape of a {@link MagicCommand} class: its
 * {@code @SubCommand} methods, its direct {@code execute} method, the
 * {@link CommandArgument}s each method's parameters declare, and the short usage line.
 * Backs the public static helpers on {@link MagicCommand}.
 */
final class CommandMethodIntrospector {

    private CommandMethodIntrospector() {
    }

    static List<MagicCommand.SubCommandInfo> subCommands(Class<?> clazz) {
        List<MagicCommand.SubCommandInfo> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                SubCommand sub = method.getAnnotation(SubCommand.class);
                if (sub == null) {
                    continue;
                }
                String signature = buildSignature(method);
                if (seen.add(signature)) {
                    result.add(new MagicCommand.SubCommandInfo(sub, method));
                }
            }
        }
        return result;
    }

    static Method findExecuteMethod(Class<?> clazz) {
        for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals("execute") && !method.isAnnotationPresent(SubCommand.class)) {
                    return method;
                }
            }
        }
        return null;
    }

    static List<CommandArgument> arguments(Method method) {
        List<CommandArgument> args = new ArrayList<>();
        Parameter[] parameters = method.getParameters();
        Annotation[][] paramAnnotations = method.getParameterAnnotations();

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            String name = param.getName();
            ParamName annotatedName = param.getAnnotation(ParamName.class);
            if (annotatedName != null && !annotatedName.value().isEmpty()) {
                name = annotatedName.value();
            }
            Class<?> type = param.getType();
            boolean optional = false;
            String defaultValue = null;
            List<String> suggestions = new ArrayList<>();
            String permission = null;
            PermissionConditionType permissionCondition = PermissionConditionType.ALWAYS;
            String[] permissionConditionArgs = new String[0];
            CompareMode compareMode = CompareMode.AUTO;
            String permissionMessage = InternalMessages.CMD_NO_PERMISSION.get();
            String permissionNode = null;
            boolean includeArgumentSegment = true;
            MagicPermissionDefault permissionDefault = MagicPermissionDefault.OP;
            boolean greedy = false;
            boolean hasPermissionAnnotation = false;
            AllowedSender[] allowedSenders = new AllowedSender[] { AllowedSender.ANY };
            boolean isSenderParameter = false;
            boolean nullable = false;
            List<String> optionShortNames = new ArrayList<>();
            List<String> optionLongNames = new ArrayList<>();
            boolean isFlag = false;
            List<String> contextArgs = new ArrayList<>();

            for (Annotation annotation : paramAnnotations[i]) {
                if (annotation instanceof DefaultValue) {
                    defaultValue = ((DefaultValue) annotation).value();
                    optional = true;
                }
                if (annotation instanceof OptionalArgument || isNullableAnnotation(annotation)) {
                    optional = true;
                    nullable = true;
                }
                if (annotation instanceof Suggest) {
                    Suggest suggest = (Suggest) annotation;
                    suggestions.addAll(Arrays.asList(suggest.value()));
                    if (suggest.contextArgs() != null && suggest.contextArgs().length > 0) {
                        contextArgs.addAll(Arrays.asList(suggest.contextArgs()));
                    }
                }
                if (annotation instanceof Permission) {
                    Permission perm = (Permission) annotation;
                    permission = perm.value();
                    permissionCondition = perm.condition();
                    permissionConditionArgs = perm.conditionArgs();
                    compareMode = perm.compare();
                    permissionNode = perm.node();
                    includeArgumentSegment = perm.includeArgumentSegment();
                    permissionDefault = perm.defaultValue();
                    hasPermissionAnnotation = true;
                    if ((permissionCondition == PermissionConditionType.ALWAYS || permissionCondition == null)
                            && perm.when() != null && !perm.when().isEmpty()) {
                        ParsedCondition parsed = parseCondition(perm.when());
                        if (parsed != null) {
                            permissionCondition = parsed.condition();
                            permissionConditionArgs = parsed.args();
                        }
                    }
                    permissionMessage = perm.message();
                }
                if (annotation instanceof Greedy) {
                    greedy = true;
                }
                if (annotation instanceof Sender senderAnno) {
                    isSenderParameter = true;
                    allowedSenders = senderAnno.value();
                }
                if (annotation instanceof Option option) {
                    optionShortNames.addAll(Arrays.asList(option.shortNames()));
                    optionLongNames.addAll(Arrays.asList(option.longNames()));
                    if (option.flag()) {
                        isFlag = true;
                    }
                }
            }

            if (isFlag) {
                optional = true;
                if (defaultValue == null) {
                    defaultValue = "false";
                }
            }

            CommandArgument.Builder builder = CommandArgument.builder(name, type);
            if (nullable)
                builder.nullable();
            else if (optional)
                builder.optional();
            if (defaultValue != null)
                builder.defaultValue(defaultValue);
            if (!suggestions.isEmpty())
                builder.suggestions(suggestions);
            if (hasPermissionAnnotation) {
                builder.markPermissionDeclared();
                builder.permission(permission != null ? permission : "");
            }
            builder.permissionCondition(permissionCondition);
            builder.permissionConditionArgs(permissionConditionArgs);
            builder.compareMode(compareMode);
            builder.permissionNode(permissionNode);
            builder.includeArgumentSegment(includeArgumentSegment);
            builder.permissionDefault(permissionDefault);
            if (permissionMessage != null)
                builder.permissionMessage(permissionMessage);
            if (greedy)
                builder.greedy();
            if (permissionMessage != null)
                builder.permissionMessage(permissionMessage);
            if (isSenderParameter) {
                builder.sender(allowedSenders);
            }
            if (!optionShortNames.isEmpty() || !optionLongNames.isEmpty()) {
                builder.option(optionShortNames.toArray(new String[0]),
                        optionLongNames.toArray(new String[0]),
                        isFlag);
            }
            builder.contextArgs(contextArgs.toArray(new String[0]));

            args.add(builder.build());
        }
        return args;
    }

    static String usage(Class<?> clazz) {
        CommandInfo info = clazz.getAnnotation(CommandInfo.class);
        if (info == null)
            return null;

        StringBuilder usage = new StringBuilder("/" + info.name());
        List<MagicCommand.SubCommandInfo> subCommands = subCommands(clazz);

        if (!subCommands.isEmpty()) {
            Set<String> topLevel = new LinkedHashSet<>();
            for (MagicCommand.SubCommandInfo sub : subCommands) {
                String[] path = sub.annotation.path();
                if (path != null && path.length > 0 && path[0] != null && !path[0].trim().isEmpty()) {
                    topLevel.add(path[0].trim());
                } else {
                    topLevel.add(sub.annotation.name());
                }
            }
            usage.append(" <");
            int index = 0;
            for (String name : topLevel) {
                if (index > 0) {
                    usage.append("|");
                }
                usage.append(name);
                index++;
            }
            usage.append(">");
        }

        return usage.toString();
    }

    private static ParsedCondition parseCondition(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        int open = trimmed.indexOf('(');
        int close = trimmed.lastIndexOf(')');
        if (open == -1 || close <= open) {
            return null;
        }
        String keyword = trimmed.substring(0, open).trim();
        String argsPart = trimmed.substring(open + 1, close);
        String[] parts = Arrays.stream(argsPart.split("[, ]+")).filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        return switch (keyword) {
            case "self" -> new ParsedCondition(PermissionConditionType.SELF, parts);
            case "other", "anyother" -> new ParsedCondition(
                    parts.length > 1 ? PermissionConditionType.ANY_OTHER : PermissionConditionType.OTHER, parts);
            case "not_null", "notnull" -> new ParsedCondition(PermissionConditionType.NOT_NULL, parts);
            case "distinct" -> new ParsedCondition(PermissionConditionType.DISTINCT, parts);
            case "all_distinct", "alldistinct" -> new ParsedCondition(PermissionConditionType.ALL_DISTINCT, parts);
            case "equals", "eq" -> new ParsedCondition(PermissionConditionType.EQUALS, parts);
            case "not_equals", "noteq", "neq" -> new ParsedCondition(PermissionConditionType.NOT_EQUALS, parts);
            case "exists" -> new ParsedCondition(PermissionConditionType.EXISTS, parts);
            default -> null;
        };
    }

    private record ParsedCondition(PermissionConditionType condition, String[] args) {
    }

    private static String buildSignature(Method method) {
        StringBuilder sb = new StringBuilder(method.getName()).append('(');
        Class<?>[] params = method.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(params[i].getName());
        }
        return sb.append(')').toString();
    }

    private static boolean isNullableAnnotation(Annotation annotation) {
        if (annotation == null) {
            return false;
        }
        String name = annotation.annotationType().getName();
        if (name == null) {
            return false;
        }
        return name.endsWith(".Nullable") || name.endsWith(".CheckForNull");
    }
}
