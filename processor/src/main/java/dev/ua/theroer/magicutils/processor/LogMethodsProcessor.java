package dev.ua.theroer.magicutils.processor;

import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * Generates Logger* convenience overloads directly onto a generated base class
 * that the annotated type can extend (e.g., Logger extends LoggerMethods).
 */
@SupportedAnnotationTypes("dev.ua.theroer.magicutils.logger.LogMethods")
public class LogMethodsProcessor extends AbstractProcessor {
    private static final String LOG_METHODS_ANNOTATION = "dev.ua.theroer.magicutils.logger.LogMethods";
    private static final String RUNTIME_LOG_LEVEL = "dev.ua.theroer.magicutils.logger.LogLevel";
    private static final String RUNTIME_LOG_TARGET = "dev.ua.theroer.magicutils.logger.LogTarget";
    private static final Set<String> DEFAULT_LEVELS = Set.of("TRACE", "INFO", "WARN", "ERROR", "DEBUG", "SUCCESS");
    private static final String DEFAULT_AUDIENCE_TYPE = "dev.ua.theroer.magicutils.platform.Audience";

    private Filer filer;
    private final Set<String> generatedFiles = new HashSet<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.filer = processingEnv.getFiler();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) {
            return false;
        }

        TypeElement annType = processingEnv.getElementUtils().getTypeElement(LOG_METHODS_ANNOTATION);
        if (annType == null) {
            return false;
        }

        for (Element element : roundEnv.getElementsAnnotatedWith(annType)) {
            if (element.getKind() != ElementKind.CLASS) {
                continue;
            }
            TypeElement type = (TypeElement) element;
            if (type.getModifiers().contains(Modifier.ABSTRACT)) {
                continue;
            }

            processingEnv.getMessager().printMessage(javax.tools.Diagnostic.Kind.NOTE,
                    "Generating log methods for " + type.getQualifiedName());
            generateFor(type);
        }
        return false;
    }

    private void generateFor(TypeElement type) {
        PackageElement pkg = processingEnv.getElementUtils().getPackageOf(type);
        String packageName = pkg.isUnnamed() ? "" : pkg.getQualifiedName().toString();
        String simpleName = type.getSimpleName().toString();
        String generatedName = simpleName + "Methods";
        String qualifiedName = packageName.isEmpty() ? generatedName : packageName + "." + generatedName;

        if (!generatedFiles.add(qualifiedName)) {
            return;
        }

        Set<String> levels = extractLevels(type);
        boolean staticMethods = shouldGenerateStatic(type);
        String audienceType = resolveAudienceType(type);

        String source = buildSource(packageName, generatedName, simpleName, levels, staticMethods, audienceType);
        try {
            JavaFileObject file = filer.createSourceFile(qualifiedName, type);
            try (Writer writer = file.openWriter()) {
                writer.write(source);
            }
        } catch (FilerException ignored) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                    "Skipping generation for " + qualifiedName + " (already generated)", type);
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Failed to generate " + qualifiedName + ": " + e.getMessage(), type);
        }
    }

    private Set<String> extractLevels(TypeElement type) {
        for (AnnotationMirror mirror : type.getAnnotationMirrors()) {
            if (mirror.getAnnotationType().toString().equals(LOG_METHODS_ANNOTATION)) {
                for (var entry : mirror.getElementValues().entrySet()) {
                    ExecutableElement key = entry.getKey();
                    if (key.getSimpleName().contentEquals("levels")) {
                        Object raw = entry.getValue().getValue();
                        if (raw instanceof List) {
                            List<?> values = (List<?>) raw;
                            Set<String> names = new LinkedHashSet<>();
                            for (Object val : values) {
                                if (val instanceof AnnotationValue) {
                                    AnnotationValue av = (AnnotationValue) val;
                                    Object avValue = av.getValue();
                                    if (avValue instanceof VariableElement) {
                                        VariableElement ve = (VariableElement) avValue;
                                        names.add(ve.getSimpleName().toString());
                                    }
                                }
                            }
                            if (!names.isEmpty()) {
                                return names;
                            }
                        }
                    }
                }
            }
        }
        return DEFAULT_LEVELS;
    }

    private String buildSource(String packageName, String generatedName, String loggerClass, Set<String> levels,
            boolean staticMethods, String audienceType) {
        StringBuilder sb = new StringBuilder();
        if (!packageName.isEmpty()) {
            sb.append("package ").append(packageName).append(";\n\n");
        }
        sb.append("@javax.annotation.processing.Generated(\"")
                .append(LogMethodsProcessor.class.getName())
                .append("\")\n");
        sb.append("public abstract class ").append(generatedName).append(" {\n");

        if (staticMethods) {
            for (String level : levels) {
                String methodBase = level.toLowerCase(Locale.ROOT);
                String levelRef = RUNTIME_LOG_LEVEL + "." + level;

                addMethod(sb, true, false, loggerClass, methodBase, "Object msg",
                        loggerClass + ".send(" + levelRef + ", msg, null, null, " + loggerClass + ".getDefaultTarget(), false);");
                addMethod(sb, true, false, loggerClass, methodBase, "String fmt, Object... args",
                        loggerClass + ".send(" + levelRef + ", String.format(fmt, args), null, null, " + loggerClass + ".getDefaultTarget(), false);");

                addMethod(sb, true, false, loggerClass, methodBase + "Console", "Object msg",
                        loggerClass + ".send(" + levelRef + ", msg, null, null, " + RUNTIME_LOG_TARGET + ".CONSOLE, false);");
                addMethod(sb, true, false, loggerClass, methodBase + "Console", "String fmt, Object... args",
                        loggerClass + ".send(" + levelRef + ", String.format(fmt, args), null, null, " + RUNTIME_LOG_TARGET + ".CONSOLE, false);");

                addMethod(sb, true, false, loggerClass, methodBase + "All", "Object msg",
                        loggerClass + ".send(" + levelRef + ", msg, null, null, " + RUNTIME_LOG_TARGET + ".BOTH, true);");
                addMethod(sb, true, false, loggerClass, methodBase + "All", "String fmt, Object... args",
                        loggerClass + ".send(" + levelRef + ", String.format(fmt, args), null, null, " + RUNTIME_LOG_TARGET + ".BOTH, true);");

                addMethod(sb, true, false, loggerClass, methodBase, audienceType + " p, Object msg",
                        loggerClass + ".send(" + levelRef + ", msg, p, null, " + RUNTIME_LOG_TARGET + ".CHAT, false);");
                addMethod(sb, true, false, loggerClass, methodBase, audienceType + " p, String fmt, Object... args",
                        loggerClass + ".send(" + levelRef + ", String.format(fmt, args), p, null, " + RUNTIME_LOG_TARGET + ".CHAT, false);");

                addMethod(sb, true, false, loggerClass, methodBase,
                        "java.util.Collection<? extends " + audienceType + "> players, Object msg",
                        loggerClass + ".send(" + levelRef + ", msg, null, players, " + RUNTIME_LOG_TARGET + ".CHAT, false);");
                addMethod(sb, true, false, loggerClass, methodBase,
                        "java.util.Collection<? extends " + audienceType + "> players, String fmt, Object... args",
                        loggerClass + ".send(" + levelRef + ", String.format(fmt, args), null, players, " + RUNTIME_LOG_TARGET + ".CHAT, false);");
            }

            sb.append("    public static void broadcast(Object message) {\n")
                    .append("        ").append(loggerClass).append(".send(")
                    .append(RUNTIME_LOG_LEVEL).append(".INFO, message, null, null, ")
                    .append(RUNTIME_LOG_TARGET).append(".BOTH, true);\n")
                    .append("    }\n");
        } else {
            // Declare abstract hooks that subclass must implement (PrefixedLogger already has them)
            sb.append("    protected abstract void send(").append(RUNTIME_LOG_LEVEL).append(" level, Object message);\n")
                    .append("    protected abstract void send(").append(RUNTIME_LOG_LEVEL).append(" level, Object message, ").append(audienceType).append(" player);\n")
                    .append("    protected abstract void send(").append(RUNTIME_LOG_LEVEL).append(" level, Object message, ").append(audienceType).append(" player, boolean all);\n")
                    .append("    protected abstract void sendToConsole(").append(RUNTIME_LOG_LEVEL).append(" level, Object message);\n")
                    .append("    protected abstract void sendToPlayers(").append(RUNTIME_LOG_LEVEL)
                    .append(" level, Object message, java.util.Collection<? extends ").append(audienceType).append("> players);\n\n");

            for (String level : levels) {
                String methodBase = level.toLowerCase(Locale.ROOT);
                String levelRef = RUNTIME_LOG_LEVEL + "." + level;

                addMethod(sb, false, true, loggerClass, methodBase, "Object msg",
                        "this.send(" + levelRef + ", msg);");
                addMethod(sb, false, true, loggerClass, methodBase, "String fmt, Object... args",
                        "this.send(" + levelRef + ", String.format(fmt, args));");

                addMethod(sb, false, true, loggerClass, methodBase + "Console", "Object msg",
                        "this.sendToConsole(" + levelRef + ", msg);");
                addMethod(sb, false, true, loggerClass, methodBase + "Console", "String fmt, Object... args",
                        "this.sendToConsole(" + levelRef + ", String.format(fmt, args));");

                addMethod(sb, false, true, loggerClass, methodBase + "All", "Object msg",
                        "this.send(" + levelRef + ", msg, null, true);");
                addMethod(sb, false, true, loggerClass, methodBase + "All", "String fmt, Object... args",
                        "this.send(" + levelRef + ", String.format(fmt, args), null, true);");

                addMethod(sb, false, true, loggerClass, methodBase, audienceType + " p, Object msg",
                        "this.send(" + levelRef + ", msg, p);");
                addMethod(sb, false, true, loggerClass, methodBase, audienceType + " p, String fmt, Object... args",
                        "this.send(" + levelRef + ", String.format(fmt, args), p);");

                addMethod(sb, false, true, loggerClass, methodBase,
                        "java.util.Collection<? extends " + audienceType + "> players, Object msg",
                        "this.sendToPlayers(" + levelRef + ", msg, players);");
                addMethod(sb, false, true, loggerClass, methodBase,
                        "java.util.Collection<? extends " + audienceType + "> players, String fmt, Object... args",
                        "this.sendToPlayers(" + levelRef + ", String.format(fmt, args), players);");
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    private void addMethod(StringBuilder sb, boolean isStatic, boolean addSelfWrapper, String loggerClass, String name, String params, String body) {
        sb.append("    public ").append(isStatic ? "static " : "").append("void ").append(name).append("(").append(params).append(") {\n")
                .append("        ").append(body).append("\n")
                .append("    }\n\n");

        if (!isStatic && addSelfWrapper) {
            String args = String.join(", ", paramNames(params));
            sb.append("    public static void ").append(name).append("(").append(loggerClass).append(" self, ").append(params).append(") {\n")
                    .append("        if (self != null) self.").append(name).append("(").append(args).append(");\n")
                    .append("    }\n\n");
        }
    }

    private List<String> paramNames(String params) {
        List<String> names = new java.util.ArrayList<>();
        for (String part : params.split(",")) {
            String[] tokens = part.trim().split("\\s+");
            if (tokens.length > 0) {
                names.add(tokens[tokens.length - 1].replace("...", ""));
            }
        }
        return names;
    }

    private boolean shouldGenerateStatic(TypeElement type) {
        for (AnnotationMirror mirror : type.getAnnotationMirrors()) {
            if (mirror.getAnnotationType().toString().equals(LOG_METHODS_ANNOTATION)) {
                for (var entry : mirror.getElementValues().entrySet()) {
                    ExecutableElement key = entry.getKey();
                    if (key.getSimpleName().contentEquals("staticMethods")) {
                        Object value = entry.getValue().getValue();
                        if (value instanceof Boolean) {
                            return (Boolean) value;
                        }
                    }
                }
            }
        }
        return true;
    }

    private String resolveAudienceType(TypeElement type) {
        for (AnnotationMirror mirror : type.getAnnotationMirrors()) {
            if (mirror.getAnnotationType().toString().equals(LOG_METHODS_ANNOTATION)) {
                for (var entry : mirror.getElementValues().entrySet()) {
                    ExecutableElement key = entry.getKey();
                    if (key.getSimpleName().contentEquals("audienceType")) {
                        Object value = entry.getValue().getValue();
                        if (value instanceof String) {
                            String resolved = ((String) value).trim();
                            if (!resolved.isEmpty()) {
                                return resolved;
                            }
                        }
                    }
                }
            }
        }
        return DEFAULT_AUDIENCE_TYPE;
    }
}
