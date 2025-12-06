package dev.ua.theroer.magicutils.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.logger.PrefixedLogger;
import dev.ua.theroer.magicutils.logger.PrefixedLoggerGen;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Advanced GUI system with MiniMessage support and convenience methods.
 */
public class MagicGui {
    private static final PrefixedLogger logger = Logger.create("MagicGui", "[GUI]");

    private final JavaPlugin plugin;
    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> buttonCallbacks = new HashMap<>();
    private final Player owner;
    private final UUID ownerUuid;
    private Consumer<InventoryCloseEvent> closeCallback;

    // Slot policies
    private final Map<Integer, SlotPolicy> slotPolicies = new HashMap<>();

    // Map of slot IDs to slot numbers
    private final Map<String, Integer> slotIdMap = new HashMap<>();

    // Anti-spam protection
    private final Map<Integer, Long> lastClickTime = new HashMap<>();
    private static final long CLICK_COOLDOWN = 150; // milliseconds

    // NBT key for placeholder identification
    private final NamespacedKey PLACEHOLDER_KEY;

    /**
     * Creates a new MagicGui with String title.
     * 
     * @param plugin the plugin instance
     * @param owner  the player who owns this GUI
     * @param size   the size of the inventory
     * @param title  the title of the GUI (supports MiniMessage)
     */
    public MagicGui(JavaPlugin plugin, Player owner, int size, String title) {
        this.plugin = plugin;
        this.owner = owner;
        this.ownerUuid = owner.getUniqueId();
        this.PLACEHOLDER_KEY = new NamespacedKey(plugin, "gui_placeholder");
        Component titleComponent = Logger.parseSmart(title);
        this.inventory = Bukkit.createInventory(owner, size, titleComponent);
    }

    /**
     * Creates a new MagicGui with Component title.
     * 
     * @param plugin the plugin instance
     * @param owner  the player who owns this GUI
     * @param size   the size of the inventory
     * @param title  the title component of the GUI
     */
    public MagicGui(JavaPlugin plugin, Player owner, int size, Component title) {
        this.plugin = plugin;
        this.owner = owner;
        this.ownerUuid = owner.getUniqueId();
        this.PLACEHOLDER_KEY = new NamespacedKey(plugin, "gui_placeholder");
        this.inventory = Bukkit.createInventory(owner, size, title);
    }

    /**
     * Set an item with click callback.
     * 
     * @param slot    inventory slot
     * @param item    item to set
     * @param onClick callback when clicked
     */
    public void setItem(int slot, ItemStack item, Consumer<InventoryClickEvent> onClick) {
        inventory.setItem(slot, item);
        if (onClick != null) {
            buttonCallbacks.put(slot, onClick);
        }
    }

    /**
     * Set an item without click callback.
     * 
     * @param slot inventory slot
     * @param item item to set
     */
    public void setItem(int slot, ItemStack item) {
        setItem(slot, item, null);
    }

    /**
     * Create and set an item with MiniMessage support.
     * 
     * @param slot     inventory slot
     * @param material item material
     * @param name     item name (supports MiniMessage)
     * @param lore     item lore (supports MiniMessage)
     * @param onClick  callback when clicked
     */
    public void setItem(int slot, Material material, String name, List<String> lore,
            Consumer<InventoryClickEvent> onClick) {
        ItemStack item = createItem(material, name, lore);
        setItem(slot, item, onClick);
    }

    /**
     * Create and set an item with Component support.
     * 
     * @param slot     inventory slot
     * @param material item material
     * @param name     item name component
     * @param lore     item lore components
     * @param onClick  callback when clicked
     */
    public void setItem(int slot, Material material, Component name, List<Component> lore,
            Consumer<InventoryClickEvent> onClick) {
        ItemStack item = createItem(material, name, lore);
        setItem(slot, item, onClick);
    }

    /**
     * Create and set an item without click callback.
     * 
     * @param slot     inventory slot
     * @param material item material
     * @param name     item name (supports MiniMessage)
     * @param lore     item lore (supports MiniMessage)
     */
    public void setItem(int slot, Material material, String name, List<String> lore) {
        setItem(slot, material, name, lore, null);
    }

    /**
     * Create and set an item without click callback.
     * 
     * @param slot     inventory slot
     * @param material item material
     * @param name     item name component
     * @param lore     item lore components
     */
    public void setItem(int slot, Material material, Component name, List<Component> lore) {
        setItem(slot, material, name, lore, null);
    }

    /**
     * Create an ItemStack with MiniMessage support.
     * 
     * @param material item material
     * @param name     item name (supports MiniMessage)
     * @param lore     item lore (supports MiniMessage)
     * @return created ItemStack
     */
    public ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (name != null) {
            meta.displayName(Logger.parseSmart(name));
        }

        if (lore != null && !lore.isEmpty()) {
            List<Component> loreComponents = lore.stream()
                    .map(line -> Logger.parseSmart(line))
                    .toList();
            meta.lore(loreComponents);
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create an ItemStack with Component support.
     * 
     * @param material item material
     * @param name     item name component
     * @param lore     item lore components
     * @return created ItemStack
     */
    public ItemStack createItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (name != null) {
            meta.displayName(name);
        }

        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Add a border of items around the GUI.
     * 
     * @param material border material
     * @param name     border item name
     */
    public void addBorder(Material material, String name) {
        ItemStack borderItem = createItem(material, name, null);
        addBorder(borderItem);
    }

    /**
     * Add a border of items around the GUI.
     * 
     * @param material border material
     * @param name     border item name component
     */
    public void addBorder(Material material, Component name) {
        ItemStack borderItem = createItem(material, name, null);
        addBorder(borderItem);
    }

    /**
     * Add a border of items around the GUI.
     * 
     * @param borderItem border item
     */
    public void addBorder(ItemStack borderItem) {
        int size = inventory.getSize();
        int rowLength = 9;
        for (int i = 0; i < size; i++) {
            if (i < rowLength || i >= size - rowLength || i % rowLength == 0 || (i + 1) % rowLength == 0) {
                if (inventory.getItem(i) == null) {
                    inventory.setItem(i, borderItem);
                }
            }
        }
    }

    /**
     * Fill empty slots with an item.
     * 
     * @param material fill material
     * @param name     fill item name
     */
    public void fillEmpty(Material material, String name) {
        ItemStack fillItem = createItem(material, name, null);
        fillEmpty(fillItem);
    }

    /**
     * Fill empty slots with an item.
     * 
     * @param material fill material
     * @param name     fill item name component
     */
    public void fillEmpty(Material material, Component name) {
        ItemStack fillItem = createItem(material, name, null);
        fillEmpty(fillItem);
    }

    /**
     * Fill empty slots with an item.
     * 
     * @param fillItem fill item
     */
    public void fillEmpty(ItemStack fillItem) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, fillItem);
            }
        }
    }

    /**
     * Set a callback for when the GUI is closed.
     * 
     * @param closeCallback close callback
     */
    public void onClose(Consumer<InventoryCloseEvent> closeCallback) {
        this.closeCallback = closeCallback;
    }

    /**
     * Creates a placeholder item with NBT tag for identification.
     * 
     * @param material the material for the placeholder
     * @param name     the display name (supports MiniMessage)
     * @return the placeholder ItemStack
     */
    private ItemStack createPlaceholder(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Set display name
            meta.displayName(Logger.parseSmart(name));
            // Add NBT tag to identify as placeholder
            meta.getPersistentDataContainer().set(PLACEHOLDER_KEY, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Check if an item is a placeholder.
     * 
     * @param item the item to check
     * @return true if the item is a placeholder
     */
    public boolean isPlaceholder(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(PLACEHOLDER_KEY, PersistentDataType.BYTE);
    }

    /**
     * Get all non-placeholder items from editable slots.
     * 
     * @return map of slot to item (excluding placeholders)
     */
    public Map<Integer, ItemStack> getEditableItems() {
        Map<Integer, ItemStack> items = new HashMap<>();
        for (Map.Entry<Integer, SlotPolicy> entry : slotPolicies.entrySet()) {
            if (entry.getValue().editable()) {
                int slot = entry.getKey();
                ItemStack item = inventory.getItem(slot);
                if (item != null && !isPlaceholder(item)) {
                    items.put(slot, item.clone());
                }
            }
        }
        return items;
    }

    /**
     * Get item by slot ID.
     * 
     * @param id the slot ID
     * @return the item in the slot (null if not found or placeholder)
     */
    public ItemStack getItemById(String id) {
        Integer slot = slotIdMap.get(id);
        if (slot == null) {
            return null;
        }
        ItemStack item = inventory.getItem(slot);
        return (item != null && !isPlaceholder(item)) ? item.clone() : null;
    }

    /**
     * Get all items by their IDs.
     * 
     * @return map of ID to item (excluding placeholders)
     */
    public Map<String, ItemStack> getItemsByIds() {
        Map<String, ItemStack> items = new HashMap<>();
        for (Map.Entry<String, Integer> entry : slotIdMap.entrySet()) {
            ItemStack item = getItemById(entry.getKey());
            if (item != null) {
                items.put(entry.getKey(), item);
            }
        }
        return items;
    }

    /**
     * Open the GUI for the owner.
     */
    public void open() {
        MagicGuiListener.ensureRegistered(plugin);
        owner.openInventory(inventory);
        MagicGuiListener.registerGui(owner, this);
    }

    /**
     * Refresh all slots in the GUI.
     */
    public void refresh() {
        // This would be implemented by subclasses or screens
        // that know how to re-render their content
    }

    /**
     * Refresh specific slots in the GUI.
     * 
     * @param slots the slots to refresh
     */
    public void refreshSlots(int... slots) {
        // This would be implemented by subclasses or screens
        // that know how to re-render specific slots
    }

    /**
     * Get the underlying inventory.
     * 
     * @return inventory
     */
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Get the GUI owner.
     * 
     * @return owner player
     */
    public Player getOwner() {
        return owner;
    }

    /**
     * Make a slot editable with full policy.
     * 
     * @param slot   the slot to make editable
     * @param policy the slot policy
     */
    public void makeEditable(int slot, SlotPolicy policy) {
        PrefixedLoggerGen.debug(logger,
                "[MagicGui] Making slot " + slot + " editable with policy: editable=" + policy.editable() +
                        ", consumesItem=" + policy.consumesItem() + ", hasPlaceholder=" + (policy.placeholder() != null)
                        +
                        ", id=" + policy.id());
        slotPolicies.put(slot, policy);

        // Register slot ID if provided
        if (policy.id() != null) {
            slotIdMap.put(policy.id(), slot);
        }

        if (policy.placeholder() != null && inventory.getItem(slot) == null) {
            inventory.setItem(slot, policy.placeholder().clone());
        }
    }

    /**
     * Make a slot editable with optional placeholder.
     * 
     * @param slot         the slot to make editable
     * @param placeholder  placeholder item (null for no placeholder)
     * @param consumesItem true if placing item should consume it from player
     *                     inventory
     */
    public void makeEditable(int slot, ItemStack placeholder, boolean consumesItem) {
        makeEditable(slot, placeholder, consumesItem, null);
    }

    /**
     * Make a slot editable with optional placeholder and change callback.
     * 
     * @param slot         the slot to make editable
     * @param placeholder  placeholder item (null for no placeholder)
     * @param consumesItem true if placing item should consume it from player
     *                     inventory
     * @param onChange     callback when item changes
     */
    public void makeEditable(int slot, ItemStack placeholder, boolean consumesItem, Consumer<ItemStack> onChange) {
        // Add NBT tag to placeholder if not already present
        if (placeholder != null && !isPlaceholder(placeholder)) {
            ItemMeta meta = placeholder.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(PLACEHOLDER_KEY, PersistentDataType.BYTE, (byte) 1);
                placeholder.setItemMeta(meta);
            }
        }

        SlotPolicy policy = SlotPolicy.builder()
                .editable(true)
                .consumesItem(consumesItem)
                .placeholder(placeholder)
                .onChange(onChange)
                .build();
        makeEditable(slot, policy);
    }

    /**
     * Make a slot editable with placeholder using material and name.
     * 
     * @param slot                the slot to make editable
     * @param placeholderMaterial placeholder material (null for no placeholder)
     * @param placeholderName     placeholder name
     * @param consumesItem        true if placing item should consume it from player
     *                            inventory
     */
    public void makeEditable(int slot, Material placeholderMaterial, String placeholderName, boolean consumesItem) {
        makeEditable(slot, placeholderMaterial, placeholderName, consumesItem, null);
    }

    /**
     * Make a slot editable with ID.
     * 
     * @param slot                the slot to make editable
     * @param id                  the slot ID
     * @param placeholderMaterial placeholder material (null for no placeholder)
     * @param placeholderName     placeholder name
     * @param consumesItem        true if placing item should consume it from player
     *                            inventory
     */
    public void makeEditable(int slot, String id, Material placeholderMaterial, String placeholderName,
            boolean consumesItem) {
        ItemStack placeholder = placeholderMaterial != null ? createPlaceholder(placeholderMaterial, placeholderName)
                : null;
        SlotPolicy policy = SlotPolicy.builder()
                .editable(true)
                .consumesItem(consumesItem)
                .placeholder(placeholder)
                .id(id)
                .build();
        makeEditable(slot, policy);
    }

    /**
     * Make a slot editable with placeholder using material and name with change
     * callback.
     * 
     * @param slot                the slot to make editable
     * @param placeholderMaterial placeholder material (null for no placeholder)
     * @param placeholderName     placeholder name
     * @param consumesItem        true if placing item should consume it from player
     *                            inventory
     * @param onChange            callback when item changes
     */
    public void makeEditable(int slot, Material placeholderMaterial, String placeholderName, boolean consumesItem,
            Consumer<ItemStack> onChange) {
        ItemStack placeholder = placeholderMaterial != null ? createPlaceholder(placeholderMaterial, placeholderName)
                : null;
        makeEditable(slot, placeholder, consumesItem, onChange);
    }

    /**
     * Check if a slot is editable.
     * 
     * @param slot the slot to check
     * @return true if editable
     */
    public boolean isEditable(int slot) {
        SlotPolicy policy = slotPolicies.get(slot);
        return policy != null && policy.editable();
    }

    /**
     * Check if GUI has any editable slots.
     * 
     * @return true if has editable slots
     */
    public boolean hasEditableSlots() {
        return slotPolicies.values().stream().anyMatch(SlotPolicy::editable);
    }

    void handleClick(InventoryClickEvent event) {
        PrefixedLoggerGen.debug(logger, "[MagicGui] handleClick called");

        // Check if click is from owner
        if (!event.getWhoClicked().getUniqueId().equals(ownerUuid)) {
            PrefixedLoggerGen.debug(logger, "[MagicGui] Click cancelled - not owner");
            event.setCancelled(true);
            return;
        }

        // Check if view is valid
        if (event.getView() == null || event.getView().getTopInventory() == null) {
            PrefixedLoggerGen.error(logger, "[MagicGui] Invalid inventory view in click event");
            event.setCancelled(true);
            return;
        }

        int slot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        boolean isGuiArea = slot >= 0 && slot < topSize;

        // Debug logging
        String guiTitle = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());
        PrefixedLoggerGen.debug(logger, "[MagicGui] === Click Event ===");
        PrefixedLoggerGen.debug(logger, "[MagicGui] GUI: " + guiTitle);
        PrefixedLoggerGen.debug(logger, "[MagicGui] Slot: " + slot + " (isGuiArea=" + isGuiArea + ")");
        PrefixedLoggerGen.debug(logger,
                "[MagicGui] Action: " + event.getAction() + ", isShiftClick=" + event.isShiftClick());
        PrefixedLoggerGen.debug(logger, "[MagicGui] Cursor: " + event.getCursor());
        PrefixedLoggerGen.debug(logger, "[MagicGui] Current: " + event.getCurrentItem());
        PrefixedLoggerGen.debug(logger, "[MagicGui] Has editable slots: " + hasEditableSlots());
        PrefixedLoggerGen.debug(logger, "[MagicGui] Slot policies count: " + slotPolicies.size());

        // Handle special actions that affect multiple slots
        switch (event.getAction()) {
            case MOVE_TO_OTHER_INVENTORY:
                handleMoveToOther(event, isGuiArea);
                return;
            case COLLECT_TO_CURSOR:
                event.setCancelled(true); // Always cancel collect to cursor in GUIs
                return;
            case NOTHING:
                return; // Allow nothing action
            default:
                break;
        }

        if (isGuiArea) {
            // Clicked in GUI
            SlotPolicy policy = slotPolicies.get(slot);

            if (policy != null && policy.editable()) {
                // Check anti-spam
                if (!checkClickCooldown(slot)) {
                    event.setCancelled(true);
                    return;
                }
                // Handle editable slot
                PrefixedLoggerGen.debug(logger, "[MagicGui] Handling editable slot click");
                handleEditableSlotClick(event, slot, policy);
            } else {
                // Not editable - cancel and check for callback
                PrefixedLoggerGen.debug(logger, "[MagicGui] Slot " + slot + " is not editable, cancelling");
                event.setCancelled(true);
                Consumer<InventoryClickEvent> callback = buttonCallbacks.get(slot);
                if (callback != null) {
                    // Check anti-spam for buttons too
                    if (checkClickCooldown(slot)) {
                        try {
                            callback.accept(event);
                        } catch (Exception e) {
                            PrefixedLoggerGen.error(logger,
                                    "Error in button callback for slot " + slot + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            }
        } else {
            // Clicked in player inventory
            if (!hasEditableSlots()) {
                // No editable slots - block all interactions
                PrefixedLoggerGen.debug(logger, "[MagicGui] Blocking player inventory click - no editable slots");
                event.setCancelled(true);
            } else {
                PrefixedLoggerGen.debug(logger, "[MagicGui] Allowing player inventory click - has editable slots");
            }
        }
    }

    private void handleEditableSlotClick(InventoryClickEvent event, int slot, SlotPolicy policy) {
        Player player = (Player) event.getWhoClicked();

        // Check permission
        if (policy.permission() != null && !policy.permission().test(player)) {
            PrefixedLoggerGen.debug(logger, "[MagicGui] Permission denied for slot " + slot);
            event.setCancelled(true);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        PrefixedLoggerGen.debug(logger, "[MagicGui] Editable slot policy: consumesItem=" + policy.consumesItem() +
                ", hasPlaceholder=" + (policy.placeholder() != null) +
                ", hasValidator=" + (policy.validator() != null) +
                ", hasOnChange=" + (policy.onChange() != null));

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        ItemStack placeholder = policy.placeholder();
        boolean consumesItem = policy.consumesItem();

        PrefixedLoggerGen.debug(logger, "[MagicGui] Editable slot " + slot + " clicked, consumesItem=" + consumesItem
                + ", action=" + event.getAction());

        // Handle placeholder logic
        if (placeholder != null && current != null && current.isSimilar(placeholder)) {
            // Trying to take placeholder
            if (cursor == null || cursor.getType() == Material.AIR) {
                PrefixedLoggerGen.debug(logger, "[MagicGui] Preventing placeholder pickup");
                event.setCancelled(true);
                return;
            }
        }

        // Prepare for validation
        ItemStack newItem = null;
        boolean shouldCancel = false;

        // Handle different actions
        switch (event.getAction()) {
            case PLACE_ALL:
            case PLACE_SOME:
            case PLACE_ONE:
                if (cursor != null && cursor.getType() != Material.AIR) {
                    // Validate item
                    if (policy.validator() != null && !policy.validator().test(player, cursor)) {
                        PrefixedLoggerGen.debug(logger, "[MagicGui] Item validation failed for slot " + slot);
                        event.setCancelled(true);
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        return;
                    }

                    // Check if current is placeholder
                    if (placeholder != null && current != null && current.isSimilar(placeholder)) {
                        // Replace placeholder with item
                        event.setCancelled(true);
                        if (consumesItem) {
                            int amount = 1;
                            if (event.getAction() == InventoryAction.PLACE_ALL) {
                                amount = cursor.getAmount();
                            }
                            ItemStack newStack = cursor.clone();
                            newStack.setAmount(amount);
                            inventory.setItem(slot, newStack);
                            cursor.setAmount(cursor.getAmount() - amount);
                        } else {
                            ItemStack newStack = cursor.clone();
                            if (event.getAction() == InventoryAction.PLACE_ONE) {
                                newStack.setAmount(1);
                            }
                            inventory.setItem(slot, newStack);
                        }
                        // Schedule onChange callback
                        if (plugin != null && plugin.isEnabled() && policy.onChange() != null) {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                policy.onChange().accept(inventory.getItem(slot));
                            });
                        }
                        return;
                    }

                    if (!consumesItem) {
                        // Duplicate mode
                        shouldCancel = true;
                        newItem = cursor.clone();
                        if (event.getAction() == InventoryAction.PLACE_ONE) {
                            newItem.setAmount(1);
                        } else if (event.getAction() == InventoryAction.PLACE_SOME && current != null
                                && current.getType() != Material.AIR) {
                            int maxStack = Math.min(cursor.getType().getMaxStackSize(), inventory.getMaxStackSize());
                            int canPlace = maxStack - current.getAmount();
                            newItem.setAmount(Math.min(cursor.getAmount(), canPlace));
                        }
                    }
                }
                break;

            case SWAP_WITH_CURSOR:
                if (cursor != null && cursor.getType() != Material.AIR) {
                    // Validate item
                    if (policy.validator() != null && !policy.validator().test(player, cursor)) {
                        event.setCancelled(true);
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        return;
                    }

                    // Check if current is placeholder
                    if (placeholder != null && current != null && current.isSimilar(placeholder)) {
                        // Replace placeholder with item, don't swap
                        event.setCancelled(true);
                        if (consumesItem) {
                            inventory.setItem(slot, cursor.clone());
                            event.getCursor().setAmount(0);
                        } else {
                            inventory.setItem(slot, cursor.clone());
                        }
                        // Schedule onChange callback
                        if (plugin != null && plugin.isEnabled() && policy.onChange() != null) {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                policy.onChange().accept(inventory.getItem(slot));
                            });
                        }
                        return;
                    }

                    if (!consumesItem) {
                        shouldCancel = true;
                        newItem = cursor.clone();
                    }
                }
                break;

            case HOTBAR_SWAP:
                int hotbarSlot = event.getHotbarButton();
                if (hotbarSlot >= 0) {
                    ItemStack hotbarItem = player.getInventory().getItem(hotbarSlot);
                    if (hotbarItem != null && hotbarItem.getType() != Material.AIR) {
                        // Validate item
                        if (policy.validator() != null && !policy.validator().test(player, hotbarItem)) {
                            event.setCancelled(true);
                            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                            return;
                        }
                        if (!consumesItem) {
                            shouldCancel = true;
                            newItem = hotbarItem.clone();
                        }
                    }
                }
                break;

            case PICKUP_ALL:
            case PICKUP_SOME:
            case PICKUP_HALF:
            case PICKUP_ONE:
                if (placeholder == null || current == null || !current.isSimilar(placeholder)) {
                    newItem = null;
                }
                break;

            default:
                // Cancel unknown actions
                event.setCancelled(true);
                return;
        }

        if (shouldCancel) {
            event.setCancelled(true);
            if (newItem != null) {
                inventory.setItem(slot, newItem);
            }
        }

        // Schedule post-action updates
        if (plugin != null && plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                ItemStack finalItem = inventory.getItem(slot);

                // Restore placeholder if needed
                if ((finalItem == null || finalItem.getType() == Material.AIR) && placeholder != null) {
                    inventory.setItem(slot, placeholder.clone());
                    finalItem = placeholder;
                }

                // Call onChange if item changed
                if (policy.onChange() != null) {
                    try {
                        policy.onChange().accept(finalItem);
                    } catch (Exception e) {
                        PrefixedLoggerGen.error(logger,
                                "Error in onChange callback for slot " + slot + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    /**
     * Check click cooldown for anti-spam.
     * 
     * @param slot the slot being clicked
     * @return true if click is allowed
     */
    private boolean checkClickCooldown(int slot) {
        long now = System.currentTimeMillis();
        Long lastClick = lastClickTime.get(slot);
        if (lastClick != null && now - lastClick < CLICK_COOLDOWN) {
            return false;
        }
        lastClickTime.put(slot, now);
        return true;
    }

    /**
     * Handle move to other inventory action.
     * 
     * @param event   the click event
     * @param fromGui true if moving from GUI to player inventory
     */
    private void handleMoveToOther(InventoryClickEvent event, boolean fromGui) {
        if (fromGui) {
            // Moving from GUI to player inventory - only allow if editable
            int slot = event.getRawSlot();
            SlotPolicy policy = slotPolicies.get(slot);
            if (policy == null || !policy.editable()) {
                event.setCancelled(true);
            }
        } else if (!hasEditableSlots()) {
            // Moving from player to GUI - only allow if GUI has editable slots
            event.setCancelled(true);
        }
    }

    /**
     * Handle inventory drag event.
     * 
     * @param event the drag event
     */
    void handleDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        // Check if drag is from owner
        if (!event.getWhoClicked().getUniqueId().equals(ownerUuid)) {
            event.setCancelled(true);
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();

        // Check if any dragged slot is in GUI area and not editable
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                SlotPolicy policy = slotPolicies.get(slot);
                if (policy == null || !policy.editable()) {
                    event.setCancelled(true);
                    return;
                }
                // Check permission
                if (policy.permission() != null && !policy.permission().test((Player) event.getWhoClicked())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    /**
     * Get button callback for a slot.
     * 
     * @param slot the slot
     * @return callback or null
     */
    protected Consumer<InventoryClickEvent> getButtonCallback(int slot) {
        return buttonCallbacks.get(slot);
    }

    void handleClose(InventoryCloseEvent event) {
        MagicGuiListener.unregisterGui(owner);
        if (closeCallback != null) {
            try {
                closeCallback.accept(event);
            } catch (Exception e) {
                PrefixedLoggerGen.error(logger, "Error in close callback: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Clean up resources
        buttonCallbacks.clear();
        slotPolicies.clear();
        lastClickTime.clear();
    }
}
