package dev.ua.theroer.magicutils.processor;

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

/**
 * Claims MagicUtils annotations to silence javac's "no processor claimed" warnings.
 */
public class NoOpProcessor extends AbstractProcessor {
    /** Default constructor for service loader. */
    public NoOpProcessor() {
    }

    private static final Set<String> SUPPORTED = Set.of(
            // Config annotations
            "dev.ua.theroer.magicutils.config.annotations.ConfigSection",
            "dev.ua.theroer.magicutils.config.annotations.ConfigFile",
            "dev.ua.theroer.magicutils.config.annotations.Comment",
            "dev.ua.theroer.magicutils.config.annotations.DefaultValue",
            "dev.ua.theroer.magicutils.config.annotations.ConfigValue",
            "dev.ua.theroer.magicutils.config.annotations.ConfigReloadable",
            "dev.ua.theroer.magicutils.config.annotations.ConfigSerializable",
            // Command/annotation metadata
            "dev.ua.theroer.magicutils.annotations.DefaultValue",
            "dev.ua.theroer.magicutils.annotations.CommandInfo",
            "dev.ua.theroer.magicutils.annotations.SubCommand",
            "dev.ua.theroer.magicutils.annotations.OptionalArgument",
            "dev.ua.theroer.magicutils.annotations.Suggest"
    );

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return SUPPORTED;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations == null || annotations.isEmpty()) {
            return false;
        }
        for (TypeElement annotation : annotations) {
            if (annotation == null || !SUPPORTED.contains(annotation.getQualifiedName().toString())) {
                return false;
            }
        }
        return true;
    }
}
