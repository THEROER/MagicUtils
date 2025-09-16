package dev.ua.theroer.magicutils.lang;

/**
 * Information about a language.
 */
public class LanguageInfo {
    private final String code;
    private final String name;
    private final String author;
    private final String version;

    /**
     * Creates language information.
     * 
     * @param code    the language code
     * @param name    the display name
     * @param author  the author/translator
     * @param version the version
     */
    public LanguageInfo(String code, String name, String author, String version) {
        this.code = code;
        this.name = name;
        this.author = author;
        this.version = version;
    }

    /**
     * Gets the ISO language code.
     * 
     * @return the ISO language code
     */
    public String getCode() {
        return code;
    }

    /**
     * Gets the display name of the language.
     * 
     * @return the display name of the language
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the author of the language.
     * 
     * @return the author of the language
     */
    public String getAuthor() {
        return author;
    }

    /**
     * Gets the version of the language.
     * 
     * @return the version of the language
     */
    public String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return name + " (" + code + ") by " + author + " v" + version;
    }
}