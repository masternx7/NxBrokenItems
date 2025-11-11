package dev.fluffyworld.nxbrokenitems.gui;

import dev.fluffyworld.nxbrokenitems.NxBrokenItems;
import dev.fluffyworld.nxbrokenitems.gui.components.*;
import dev.fluffyworld.nxbrokenitems.utils.MessageUtils;
import me.clip.placeholderapi.PlaceholderAPI;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class BrokenItemsGUI implements Listener {

    private static final String COST_PLACEHOLDER = "{cost}";
    
    private final NxBrokenItems plugin;
    private final Economy economy;
    private final Map<UUID, Integer> playerPageMap = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> selectedItemMap = new ConcurrentHashMap<>();
    
    private final ItemCostCalculator costCalculator;
    private final ItemLogger itemLogger;
    private final InventoryBuilder inventoryBuilder;

    public BrokenItemsGUI(NxBrokenItems plugin, Economy economy) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.economy = Objects.requireNonNull(economy, "Economy cannot be null");
        this.costCalculator = new ItemCostCalculator(plugin.getConfig());
        this.itemLogger = new ItemLogger(plugin.getDataFolder(), plugin.getLogger());
        this.inventoryBuilder = new InventoryBuilder(plugin.getConfig(), plugin.getLogger());
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
        final String title = MessageUtils.colorize(config.getString("menu.restore.title", "&cBroken Items List"));
        final int size = config.getInt("menu.restore.size", 54);

        final List<ItemStack> items = inventoryBuilder.loadBrokenItems(dataConfig);
        final Inventory inventory = inventoryBuilder.createBrokenItemsInventory(items, page, title, size);

        player.openInventory(inventory);
        playerPageMap.put(playerUUID, page);
    }

    private List<ItemStack> loadBrokenItems(FileConfiguration dataConfig) {
        return inventoryBuilder.loadBrokenItems(dataConfig);
    }

    private ItemStack createNavigationItem(String type) {
        return null;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final String title = event.getView().getTitle();
        final FileConfiguration config = plugin.getConfig();
        
        final String listTitlePrefix = MessageUtils.colorize(
            config.getString("menu.restore.title", "&cBroken Items List"));
        final String confirmTitlePrefix = MessageUtils.colorize(
            config.getString("menu.confirm.title", "&cConfirm Action"));

        if (title.startsWith(listTitlePrefix)) {
            handleListMenuClick(event, player);
        } else if (title.startsWith(confirmTitlePrefix)) {
            handleConfirmMenuClick(event, player);
        }
    }

    private void handleListMenuClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        final ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        final ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null || meta.getDisplayName() == null) {
            openConfirmationMenu(player, clickedItem);
            return;
        }

        final String itemName = meta.getDisplayName();
        final FileConfiguration config = plugin.getConfig();
        final String nextPageName = MessageUtils.colorize(
            config.getString("menu.restore.navigation-buttons.next-page.display-name", "&aNext Page"));
        final String prevPageName = MessageUtils.colorize(
            config.getString("menu.restore.navigation-buttons.previous-page.display-name", "&aPrevious Page"));

        final UUID playerUUID = player.getUniqueId();
        final int currentPage = playerPageMap.getOrDefault(playerUUID, 0);

        if (itemName.equals(nextPageName)) {
            openInventory(player, currentPage + 1);
        } else if (itemName.equals(prevPageName)) {
            openInventory(player, currentPage - 1);
        } else {
            openConfirmationMenu(player, clickedItem);
        }
    }

    private void openConfirmationMenu(Player player, ItemStack selectedItem) {
        final FileConfiguration config = plugin.getConfig();
        final String title = MessageUtils.colorize(
            config.getString("menu.confirm.title", "&cConfirm Action"));
        final int size = config.getInt("menu.confirm.size", 27);

        final Inventory confirmInventory = inventoryBuilder.createConfirmationInventory(title, size);

        player.openInventory(confirmInventory);
        selectedItemMap.put(player.getUniqueId(), selectedItem);
    }

    private ItemStack createConfirmButton(String type) {
        return null;
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
        final String restoreName = MessageUtils.colorize(
            config.getString("menu.confirm.buttons.restore.display-name", "&aRestore"));
        final String deleteName = MessageUtils.colorize(
            config.getString("menu.confirm.buttons.delete.display-name", "&cDelete"));
        final String backName = MessageUtils.colorize(
            config.getString("menu.confirm.buttons.back.display-name", "&e&lBack to List"));

        final UUID playerUUID = player.getUniqueId();

        if (itemName.equals(backName)) {
            selectedItemMap.remove(playerUUID);
            final int currentPage = playerPageMap.getOrDefault(playerUUID, 0);
            openInventory(player, currentPage);
            return;
        }

        final ItemStack selectedItem = selectedItemMap.get(playerUUID);

        if (selectedItem == null) {
            player.closeInventory();
            return;
        }

        if (itemName.equals(restoreName)) {
            handleItemRestoration(player, selectedItem);
        } else if (itemName.equals(deleteName)) {
            handleItemDeletion(player, selectedItem);
        }

        selectedItemMap.remove(playerUUID);
    }

    private int calculateRestorationCost(ItemStack item) {
        return costCalculator.calculateRestorationCost(item);
    }

    private void addRestorationCostLore(ItemStack item) {
        inventoryBuilder.addRestorationCostLore(item);
    }

    private void handleItemRestoration(Player player, ItemStack item) {
        final UUID playerUUID = player.getUniqueId();
        final FileConfiguration dataConfig = plugin.getDataConfig(playerUUID);
        
        if (dataConfig == null) {
            sendMessage(player, "messages.no-broken-items");
            return;
        }

        final int cost = calculateRestorationCost(item) * item.getAmount();

        // Check if inventory is full
        if (PlaceholderAPI.setPlaceholders(player, "%fluffy_isfull%").equalsIgnoreCase("true")) {
            sendMessage(player, "messages.inventory-full");
            return;
        }

        // Check if player has enough money
        if (!economy.has(player, cost)) {
            player.sendMessage(MessageUtils.colorize(
                plugin.getConfig().getString("messages.not-enough-money")
                    .replace(COST_PLACEHOLDER, String.valueOf(cost))));
            return;
        }

        // Find and restore the item
        final ConfigurationSection restoreSection = dataConfig.getConfigurationSection("restoreItem");
        if (restoreSection == null) {
            sendMessage(player, "messages.no-broken-items");
            return;
        }

        for (String key : restoreSection.getKeys(false)) {
            // Try new format first (.item), then fallback to old format
            ItemStack storedItem = dataConfig.getItemStack("restoreItem." + key + ".item");
            if (storedItem == null) {
                storedItem = dataConfig.getItemStack("restoreItem." + key);
            }
            
            if (storedItem != null && isSameItem(storedItem, item)) {
                // Check if item is blacklisted
                boolean isBlacklisted = dataConfig.getBoolean("restoreItem." + key + ".blacklisted", false);
                
                if (isBlacklisted) {
                    player.closeInventory();
                    sendMessage(player, "messages.blacklisted-item");
                    sendMessage(player, "messages.contact-admin");
                    return;
                }
                
                processRestoration(player, playerUUID, item, storedItem, cost, key, dataConfig);
                return;
            }
        }
    }

    private void processRestoration(Player player, UUID playerUUID, ItemStack displayItem, 
                                   ItemStack storedItem, int cost, String key, 
                                   FileConfiguration dataConfig) {
        final ItemStack cleanItem = inventoryBuilder.removeCostLore(displayItem.clone());
        
        player.getInventory().addItem(cleanItem);
        economy.withdrawPlayer(player, cost);
        
        dataConfig.set("restoreItem." + key, null);
        plugin.saveDataFile(playerUUID, dataConfig);
        
        sendMessage(player, "messages.restore-success");
        itemLogger.logRecovery(player.getName(), cleanItem);
        
        player.closeInventory();
    }

    private ItemStack removeCostLore(ItemStack item) {
        return inventoryBuilder.removeCostLore(item);
    }

    private boolean isSameItem(ItemStack item1, ItemStack item2) {
        return ItemComparator.isSameItem(item1, item2);
    }

    private void logRecovery(String playerName, ItemStack item) {
        itemLogger.logRecovery(playerName, item);
    }

    private void sendMessage(Player player, String path) {
        final String message = plugin.getConfig().getString(path);
        if (message != null && !message.isEmpty()) {
            player.sendMessage(MessageUtils.colorize(message));
        }
    }

    private void handleItemDeletion(Player player, ItemStack item) {
        final UUID playerUUID = player.getUniqueId();
        final FileConfiguration dataConfig = plugin.getDataConfig(playerUUID);

        if (dataConfig == null) {
            player.closeInventory();
            return;
        }

        final ConfigurationSection restoreSection = dataConfig.getConfigurationSection("restoreItem");
        if (restoreSection == null) {
            player.closeInventory();
            return;
        }

        final ItemStack cleanItem = inventoryBuilder.removeCostLore(item.clone());

        for (String key : restoreSection.getKeys(false)) {
            ItemStack storedItem = dataConfig.getItemStack("restoreItem." + key + ".item");
            if (storedItem == null) {
                storedItem = dataConfig.getItemStack("restoreItem." + key);
            }
            
            if (storedItem != null && ItemComparator.isSameItem(storedItem, cleanItem)) {
                dataConfig.set("restoreItem." + key, null);
                plugin.saveDataFile(playerUUID, dataConfig);
                sendMessage(player, "messages.delete-success");
                itemLogger.logDeletion(player.getName(), cleanItem);
                player.closeInventory();
                return;
            }
        }

        player.closeInventory();
    }

    private void logDeletion(String playerName, ItemStack item) {
        itemLogger.logDeletion(playerName, item);
    }
}
