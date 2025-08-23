package dev.ua.theroer.magicutils.config.example;

import dev.ua.theroer.magicutils.config.annotations.*;
import lombok.Data;
import lombok.Builder;

import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Example configuration class demonstrating MagicUtils config system.
 */
@ConfigFile("example.yml")
@ConfigReloadable(sections = {"items", "settings"})
@Comment("Example configuration for MagicUtils")
public class ExampleConfig {
    
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
    public List<Item> getItemsByType(String type) {
        return items.stream()
            .filter(item -> item.getType().equalsIgnoreCase(type))
            .collect(Collectors.toList());
    }
    
    public List<Item> getItemsWithMinLevel(int minLevel) {
        return items.stream()
            .filter(item -> item.getLevel() >= minLevel)
            .collect(Collectors.toList());
    }
    
    public Item getItemById(String id) {
        return items.stream()
            .filter(item -> item.getId().equals(id))
            .findFirst()
            .orElse(null);
    }
    
    // Getters
    public List<Item> getItems() { return items; }
    public Settings getSettings() { return settings; }
    public boolean isEnabled() { return enabled; }
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
    
    public int getMaxItems() { return maxItems; }
    public boolean isAutoSave() { return autoSave; }
    public int getSaveInterval() { return saveInterval; }
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
                .build()
        );
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