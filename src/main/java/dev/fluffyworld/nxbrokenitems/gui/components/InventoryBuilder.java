package dev.fluffyworld.nxbrokenitems.gui.components;

import dev.fluffyworld.nxbrokenitems.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class InventoryBuilder {

    private static final String COST_PLACEHOLDER = "{cost}";

    private final FileConfiguration config;
    private final Logger logger;
    private final ItemCostCalculator costCalculator;

    public InventoryBuilder(FileConfiguration config, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.costCalculator = new ItemCostCalculator(config);
    }

    public Inventory createBrokenItemsInventory(List<ItemStack> items, int page, String title, int size) {
        final int itemsPerPageOffset = config.getInt("menu.restore.items-per-page-offset", 9);
        final int itemsPerPage = size - itemsPerPageOffset;
        final Inventory inventory = Bukkit.createInventory(null, size, title);

        final int startIndex = page * itemsPerPage;
        final int endIndex = Math.min(startIndex + itemsPerPage, items.size());

        items.subList(startIndex, endIndex).forEach(inventory::addItem);

        if (page > 0) {
            final int prevSlot = config.getInt("menu.restore.navigation-buttons.previous-page.slot", -9);
            final int prevSlotCalculated = prevSlot < 0 ? size + prevSlot : prevSlot;
            inventory.setItem(prevSlotCalculated, createNavigationItem("previous-page"));
        }

        if (endIndex < items.size()) {
            final int nextSlot = config.getInt("menu.restore.navigation-buttons.next-page.slot", -1);
            final int nextSlotCalculated = nextSlot < 0 ? size + nextSlot : nextSlot;
            inventory.setItem(nextSlotCalculated, createNavigationItem("next-page"));
        }

        return inventory;
    }

    public Inventory createConfirmationInventory(String title, int size) {
        final Inventory confirmInventory = Bukkit.createInventory(null, size, title);
        
        final int restoreSlot = config.getInt("menu.confirm.buttons.restore.slot", 11);
        final int deleteSlot = config.getInt("menu.confirm.buttons.delete.slot", 15);
        final int backSlot = config.getInt("menu.confirm.buttons.back.slot", 13);
        
        confirmInventory.setItem(restoreSlot, createConfirmButton("restore"));
        confirmInventory.setItem(deleteSlot, createConfirmButton("delete"));
        confirmInventory.setItem(backSlot, createConfirmButton("back"));
        
        return confirmInventory;
    }

    public List<ItemStack> loadBrokenItems(FileConfiguration dataConfig) {
        final ConfigurationSection restoreSection = dataConfig.getConfigurationSection("restoreItem");
        if (restoreSection == null) {
            return Collections.emptyList();
        }

        return restoreSection.getKeys(false).stream()
            .map(key -> {
                ItemStack item = dataConfig.getItemStack("restoreItem." + key + ".item");
                if (item == null) {
                    item = dataConfig.getItemStack("restoreItem." + key);
                }
                return item;
            })
            .filter(Objects::nonNull)
            .peek(this::addRestorationCostLore)
            .collect(Collectors.toList());
    }

    public void addRestorationCostLore(ItemStack item) {
        final int cost = costCalculator.calculateRestorationCost(item);
        final String loreFormat = config.getString(
            "menu.restore.lore.format", "&eRestoration Cost: &6{cost}");
        final String formattedLore = MessageUtils.colorize(
            loreFormat.replace(COST_PLACEHOLDER, String.valueOf(cost)));

        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final List<String> lore = meta.hasLore() 
                ? new ArrayList<>(meta.getLore()) 
                : new ArrayList<>();
            lore.add(formattedLore);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
    }

    public ItemStack removeCostLore(ItemStack item) {
        final ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasLore()) {
            final String loreFormat = config.getString(
                "menu.restore.lore.format", "&eRestoration Cost: &6{cost}");
            final String cleanFormat = MessageUtils.colorize(loreFormat).replaceAll("§[0-9a-fk-or]", "");
            final String pattern = cleanFormat.replace("{cost}", "");
            
            final List<String> lore = meta.getLore().stream()
                .filter(line -> {
                    final String cleanLine = line.replaceAll("§[0-9a-fk-or]", "");
                    return !cleanLine.contains(pattern.trim());
                })
                .collect(Collectors.toList());
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createNavigationItem(String type) {
        final String basePath = "menu.restore.navigation-buttons." + type;
        
        final Material material = Material.getMaterial(
            config.getString(basePath + ".material", "ARROW").toUpperCase());
        
        if (material == null) {
            logger.warning("Invalid material for navigation button: " + type);
            return new ItemStack(Material.ARROW);
        }

        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(MessageUtils.colorize(
                config.getString(basePath + ".display-name", "")));
            
            final int customModelData = config.getInt(basePath + ".custom-model-data", 0);
            if (customModelData != 0) {
                meta.setCustomModelData(customModelData);
            }
            
            item.setItemMeta(meta);
        }
        
        return item;
    }

    private ItemStack createConfirmButton(String type) {
        final String basePath = "menu.confirm.buttons." + type;
        
        final Material material = Material.getMaterial(
            config.getString(basePath + ".material", "LIME_WOOL").toUpperCase());
        
        if (material == null) {
            logger.warning("Invalid material for confirm button: " + type);
            return new ItemStack(Material.LIME_WOOL);
        }

        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(MessageUtils.colorize(
                config.getString(basePath + ".display-name", "")));
            
            final int customModelData = config.getInt(basePath + ".custom-model-data", 0);
            if (customModelData != 0) {
                meta.setCustomModelData(customModelData);
            }
            
            item.setItemMeta(meta);
        }
        
        return item;
    }
}
