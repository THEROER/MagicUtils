package dev.ua.theroer.magicutils.processor;

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

/**
 * Claims MagicUtils annotations to silence javac's "no processor claimed" warnings.
 */
@SupportedSourceVersion(SourceVersion.RELEASE_21)
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
            "dev.ua.theroer.magicutils.annotations.Suggest",
            "dev.ua.theroer.magicutils.logger.LogMethods",
            // Common nullity annotations often reported
            "org.jetbrains.annotations.NotNull",
            "org.jetbrains.annotations.Nullable",
            "javax.annotation.Nullable",
            "javax.annotation.Nonnull",
            // Bukkit event annotations
            "org.bukkit.event.EventHandler"
    );

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return SUPPORTED;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // No code generation; just claim the annotations.
        return true;
    }
}
