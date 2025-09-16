package dev.ua.theroer.magicutils.config.example;

import dev.ua.theroer.magicutils.config.annotations.*;
import lombok.Data;
import lombok.Builder;

import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Example configuration class demonstrating MagicUtils config system.
 * This class showcases various configuration features including config values,
 * sections, validation, and default value providers.
 * 
 * <p>
 * The configuration is automatically loaded from example.yml and supports
 * reloading of specific sections (items and settings).
 * </p>
 * 
 * <p>
 * Constructor is automatically handled by the MagicUtils config system.
 * </p>
 */
@ConfigFile("example.yml")
@ConfigReloadable(sections = { "items", "settings" })
@Comment("Example configuration for MagicUtils")
public class ExampleConfig {

    /**
     * Default constructor for ExampleConfig.
     */
    public ExampleConfig() {
    }

    @ConfigValue("items")
    @Comment("List of available items")
    @DefaultValue(provider = DefaultItemsProvider.class)
    @ListProcessor(ItemValidator.class)
    private List<Item> items;

    @ConfigSection("settings")
    @Comment("General settings")
    private Settings settings = new Settings();

    @ConfigValue("enabled")
    @DefaultValue("true")
    @Comment("Whether the system is enabled")
    private boolean enabled;

    // Custom methods for the plugin to use
    /**
     * Gets all items matching the specified type.
     * 
     * @param type the item type to filter by (case-insensitive)
     * @return list of items matching the specified type
     */
    public List<Item> getItemsByType(String type) {
        return items.stream()
                .filter(item -> item.getType().equalsIgnoreCase(type))
                .collect(Collectors.toList());
    }

    /**
     * Gets all items with level greater than or equal to the specified minimum
     * level.
     * 
     * @param minLevel the minimum level threshold
     * @return list of items with level >= minLevel
     */
    public List<Item> getItemsWithMinLevel(int minLevel) {
        return items.stream()
                .filter(item -> item.getLevel() >= minLevel)
                .collect(Collectors.toList());
    }

    /**
     * Gets the item with the specified ID.
     * 
     * @param id the unique identifier of the item
     * @return the item with matching ID, or null if not found
     */
    public Item getItemById(String id) {
        return items.stream()
                .filter(item -> item.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    // Getters
    /**
     * Gets the list of configured items.
     * 
     * @return the list of items from the configuration
     */
    public List<Item> getItems() {
        return items;
    }

    /**
     * Gets the settings section of the configuration.
     * 
     * @return the settings configuration section
     */
    public Settings getSettings() {
        return settings;
    }

    /**
     * Gets whether the system is enabled.
     * 
     * @return true if the system is enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }
}

/**
 * Item data class.
 */
@ConfigSerializable
@Data
@Builder
class Item {
    private String id;
    private String name;
    private String type;
    private int level;
    private List<String> attributes;
}

/**
 * Settings section.
 */
class Settings {
    @ConfigValue("max-items")
    @DefaultValue("100")
    @Comment("Maximum number of items allowed")
    private int maxItems;

    @ConfigValue("auto-save")
    @DefaultValue("true")
    @Comment("Whether to auto-save changes")
    private boolean autoSave;

    @ConfigValue("save-interval")
    @DefaultValue("300")
    @Comment("Save interval in seconds")
    private int saveInterval;

    /**
     * Gets the maximum number of items allowed.
     * 
     * @return the maximum item count
     */
    public int getMaxItems() {
        return maxItems;
    }

    /**
     * Gets whether auto-save is enabled.
     * 
     * @return true if auto-save is enabled, false otherwise
     */
    public boolean isAutoSave() {
        return autoSave;
    }

    /**
     * Gets the save interval in seconds.
     * 
     * @return the save interval in seconds
     */
    public int getSaveInterval() {
        return saveInterval;
    }
}

/**
 * Default items provider.
 */
class DefaultItemsProvider implements DefaultValueProvider<List<Item>> {
    @Override
    public List<Item> provide() {
        return Arrays.asList(
                Item.builder()
                        .id("sword_basic")
                        .name("Basic Sword")
                        .type("weapon")
                        .level(1)
                        .attributes(Arrays.asList("damage:5", "durability:100"))
                        .build(),
                Item.builder()
                        .id("shield_basic")
                        .name("Basic Shield")
                        .type("armor")
                        .level(1)
                        .attributes(Arrays.asList("defense:3", "durability:150"))
                        .build());
    }
}

/**
 * Item validator.
 */
class ItemValidator implements ListItemProcessor<Item> {
    @Override
    public ProcessResult<Item> process(Item item, int index) {
        // Validate ID
        if (item.getId() == null || item.getId().isEmpty()) {
            return ProcessResult.replaceWithDefault();
        }

        // Fix level bounds
        if (item.getLevel() < 1) {
            item.setLevel(1);
            return ProcessResult.modified(item);
        }

        if (item.getLevel() > 100) {
            item.setLevel(100);
            return ProcessResult.modified(item);
        }

        // Ensure attributes list exists
        if (item.getAttributes() == null) {
            item.setAttributes(Arrays.asList());
            return ProcessResult.modified(item);
        }

        return ProcessResult.ok(item);
    }
}