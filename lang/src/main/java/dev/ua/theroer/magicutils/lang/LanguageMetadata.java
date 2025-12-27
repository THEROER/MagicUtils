package dev.ua.theroer.magicutils.lang;

import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import dev.ua.theroer.magicutils.config.annotations.DefaultValue;
import lombok.Getter;
import dev.ua.theroer.magicutils.config.annotations.Comment;

/**
 * Language metadata
 */
@Getter
public class LanguageMetadata {
    /**
     * Default constructor for LanguageMetadata.
     */
    public LanguageMetadata() {
    }

    @ConfigValue("name")
    @DefaultValue("English")
    @Comment("Display name of the language")
    private String name;

    @ConfigValue("code")
    @DefaultValue("en")
    @Comment("ISO language code")
    private String code;

    @ConfigValue("author")
    @DefaultValue("MagicUtils Team")
    @Comment("Author or translator of this language file")
    private String author;

    @ConfigValue("version")
    @DefaultValue("1.0")
    @Comment("Version of the language file")
    private String version;
}