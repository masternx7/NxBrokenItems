package dev.fluffyworld.nxbrokenitems.gui;

import dev.fluffyworld.nxbrokenitems.NxBrokenItems;
import dev.fluffyworld.nxbrokenitems.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class DeleteItemsGUI implements Listener {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_ONLY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    private final NxBrokenItems plugin;
    private final Map<UUID, Integer> playerPageMap = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> confirmDeleteMap = new ConcurrentHashMap<>();

    public DeleteItemsGUI(NxBrokenItems plugin) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openInventory(Player player) {
        openInventory(player, 0);
    }

    public void openInventory(Player player, int page) {
        final UUID playerUUID = player.getUniqueId();
        final FileConfiguration dataConfig = plugin.getDataConfig(playerUUID);

        if (dataConfig == null || !dataConfig.contains("restoreItem")) {
            sendMessage(player, "messages.no-broken-items");
            return;
        }

        final FileConfiguration config = plugin.getConfig();
        final String title = MessageUtils.colorize(config.getString("menu.delete.title", "&cDelete Broken Items"));
        final int size = config.getInt("menu.delete.size", 54);
        final int itemsPerPage = size - 9;

        final Inventory inventory = Bukkit.createInventory(null, size, title);
        final List<ItemStack> items = loadBrokenItems(dataConfig);

        final int startIndex = page * itemsPerPage;
        final int endIndex = Math.min(startIndex + itemsPerPage, items.size());

        items.subList(startIndex, endIndex).forEach(inventory::addItem);

        // Add navigation buttons
        if (page > 0) {
            inventory.setItem(size - 9, createNavigationItem("menu.delete.navigation-buttons.previous-page"));
        }

        if (endIndex < items.size()) {
            inventory.setItem(size - 1, createNavigationItem("menu.delete.navigation-buttons.next-page"));
        }

        player.openInventory(inventory);
        playerPageMap.put(playerUUID, page);
    }

    private List<ItemStack> loadBrokenItems(FileConfiguration dataConfig) {
        final ConfigurationSection restoreSection = dataConfig.getConfigurationSection("restoreItem");
        if (restoreSection == null) {
            return Collections.emptyList();
        }

        return restoreSection.getKeys(false).stream()
            .map(key -> {
                // Try new format first (.item), then fallback to old format
                ItemStack item = dataConfig.getItemStack("restoreItem." + key + ".item");
                if (item == null) {
                    item = dataConfig.getItemStack("restoreItem." + key);
                }
                return item;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private ItemStack createNavigationItem(String configPath) {
        final FileConfiguration config = plugin.getConfig();
        final Material material = Material.getMaterial(
            config.getString(configPath + ".material", "ARROW").toUpperCase());
        
        if (material == null) {
            plugin.getLogger().warning("Invalid material for navigation button: " + configPath);
            return new ItemStack(Material.ARROW);
        }

        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(MessageUtils.colorize(
                config.getString(configPath + ".display-name", "")));
            
            final int customModelData = config.getInt(configPath + ".custom-model-data", 0);
            if (customModelData != 0) {
                meta.setCustomModelData(customModelData);
            }
            
            item.setItemMeta(meta);
        }
        
        return item;
    }

    private void openConfirmationInventory(Player player, ItemStack itemToDelete) {
        final FileConfiguration config = plugin.getConfig();
        final String title = MessageUtils.colorize(config.getString("menu.confirm.title", "&cConfirm Deletion"));
        final int size = config.getInt("menu.confirm.size", 27);

        final Inventory confirmationInventory = Bukkit.createInventory(null, size, title);

        confirmationInventory.setItem(11, createNavigationItem("menu.confirm.buttons.confirm"));
        confirmationInventory.setItem(15, createNavigationItem("menu.confirm.buttons.cancel"));

        player.openInventory(confirmationInventory);
        confirmDeleteMap.put(player.getUniqueId(), itemToDelete);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final String title = event.getView().getTitle();
        final FileConfiguration config = plugin.getConfig();
        final String deleteTitlePrefix = MessageUtils.colorize(
            config.getString("menu.delete.title", "&cDelete Broken Items"));
        final String confirmTitlePrefix = MessageUtils.colorize(
            config.getString("menu.confirm.title", "&cConfirm Deletion"));

        if (title.startsWith(deleteTitlePrefix)) {
            handleDeleteMenuClick(event, player);
        } else if (title.startsWith(confirmTitlePrefix)) {
            handleConfirmMenuClick(event, player);
        }
    }

    private void handleDeleteMenuClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        final ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        final ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null || meta.getDisplayName() == null) {
            openConfirmationInventory(player, clickedItem);
            return;
        }

        final FileConfiguration config = plugin.getConfig();
        final String itemName = meta.getDisplayName();
        final String nextPageName = MessageUtils.colorize(
            config.getString("menu.delete.navigation-buttons.next-page.display-name", "&aNext Page"));
        final String prevPageName = MessageUtils.colorize(
            config.getString("menu.delete.navigation-buttons.previous-page.display-name", "&aPrevious Page"));

        final UUID playerUUID = player.getUniqueId();
        final int currentPage = playerPageMap.getOrDefault(playerUUID, 0);

        if (itemName.equals(nextPageName)) {
            openInventory(player, currentPage + 1);
        } else if (itemName.equals(prevPageName)) {
            openInventory(player, currentPage - 1);
        } else {
            openConfirmationInventory(player, clickedItem);
        }
    }

    private void handleConfirmMenuClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        final ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        final ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null || meta.getDisplayName() == null) {
            return;
        }

        final FileConfiguration config = plugin.getConfig();
        final String itemName = meta.getDisplayName();
        final String confirmName = MessageUtils.colorize(
            config.getString("menu.confirm.buttons.confirm.display-name", "&aConfirm"));
        final String cancelName = MessageUtils.colorize(
            config.getString("menu.confirm.buttons.cancel.display-name", "&cCancel"));

        final UUID playerUUID = player.getUniqueId();
        final ItemStack itemToDelete = confirmDeleteMap.get(playerUUID);

        if (itemName.equals(confirmName)) {
            if (itemToDelete != null) {
                handleItemDeletion(player, itemToDelete);
            }
            confirmDeleteMap.remove(playerUUID);
            openInventory(player, playerPageMap.getOrDefault(playerUUID, 0));
        } else if (itemName.equals(cancelName)) {
            confirmDeleteMap.remove(playerUUID);
            openInventory(player, playerPageMap.getOrDefault(playerUUID, 0));
        }
    }

    private void handleItemDeletion(Player player, ItemStack item) {
        final UUID playerUUID = player.getUniqueId();
        final FileConfiguration dataConfig = plugin.getDataConfig(playerUUID);

        if (dataConfig == null) {
            return;
        }

        final ConfigurationSection restoreSection = dataConfig.getConfigurationSection("restoreItem");
        if (restoreSection == null) {
            return;
        }

        for (String key : restoreSection.getKeys(false)) {
            // Try new format first (.item), then fallback to old format
            ItemStack storedItem = dataConfig.getItemStack("restoreItem." + key + ".item");
            if (storedItem == null) {
                storedItem = dataConfig.getItemStack("restoreItem." + key);
            }
            
            if (storedItem != null && storedItem.isSimilar(item)) {
                dataConfig.set("restoreItem." + key, null);
                plugin.saveDataFile(playerUUID, dataConfig);
                sendMessage(player, "messages.delete-success");
                logDeletion(player.getName(), item);
                return;
            }
        }
    }

    private void logDeletion(String playerName, ItemStack item) {
        final File logFile = new File(plugin.getDataFolder(), "log-item-delete.yml");
        final FileConfiguration logConfig = YamlConfiguration.loadConfiguration(logFile);

        final LocalDateTime now = LocalDateTime.now();
        final String currentTime = now.format(DATE_FORMAT);
        final String currentDate = now.format(DATE_ONLY_FORMAT);

        final ItemMeta meta = item.getItemMeta();
        final String displayName = meta != null && meta.hasDisplayName() 
            ? meta.getDisplayName() 
            : item.getType().name();
        
        final List<String> loreList = meta != null && meta.hasLore() 
            ? meta.getLore() 
            : Collections.emptyList();
        
        final Map<Enchantment, Integer> enchantments = meta != null 
            ? meta.getEnchants() 
            : Collections.emptyMap();

        final String enchantmentsStr = enchantments.isEmpty() 
            ? "" 
            : " with enchantments: " + enchantments.entrySet().stream()
                .map(e -> e.getKey().getKey().getKey() + " " + e.getValue())
                .collect(Collectors.joining(", "));

        final String lore = loreList.isEmpty() 
            ? "" 
            : " with lore: " + String.join(", ", loreList);
        
        final String logEntry = String.format("%s deleted %dx %s%s%s at %s",
            playerName, item.getAmount(), displayName, lore, enchantmentsStr, currentTime);

        final List<String> dailyLogs = new ArrayList<>(logConfig.getStringList(currentDate));
        dailyLogs.add(logEntry);
        logConfig.set(currentDate, dailyLogs);

        try {
            logConfig.save(logFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save deletion log", e);
        }
    }

    private void sendMessage(Player player, String path) {
        final String message = plugin.getConfig().getString(path);
        if (message != null && !message.isEmpty()) {
            player.sendMessage(MessageUtils.colorize(message));
        }
    }
}
