package dev.fluffyworld.nxbrokenitems.gui;

import dev.fluffyworld.nxbrokenitems.NxBrokenItems;
import dev.fluffyworld.nxbrokenitems.utils.MessageUtils;
import me.clip.placeholderapi.PlaceholderAPI;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class BrokenItemsGUI implements Listener {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_ONLY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String COST_PLACEHOLDER = "{cost}";
    
    private final NxBrokenItems plugin;
    private final Economy economy;
    private final Map<UUID, Integer> playerPageMap = new ConcurrentHashMap<>();

    public BrokenItemsGUI(NxBrokenItems plugin, Economy economy) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.economy = Objects.requireNonNull(economy, "Economy cannot be null");
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
        final int itemsPerPage = size - 9;

        final Inventory inventory = Bukkit.createInventory(null, size, title);
        final List<ItemStack> items = loadBrokenItems(dataConfig);

        final int startIndex = page * itemsPerPage;
        final int endIndex = Math.min(startIndex + itemsPerPage, items.size());

        items.subList(startIndex, endIndex).forEach(inventory::addItem);

        // Add navigation buttons
        if (page > 0) {
            inventory.setItem(size - 9, createNavigationItem("previous-page"));
        }

        if (endIndex < items.size()) {
            inventory.setItem(size - 1, createNavigationItem("next-page"));
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
            .peek(this::addRestorationCostLore)
            .collect(Collectors.toList());
    }

    private ItemStack createNavigationItem(String type) {
        final FileConfiguration config = plugin.getConfig();
        final String basePath = "menu.restore.navigation-buttons." + type;
        
        final Material material = Material.getMaterial(
            config.getString(basePath + ".material", "ARROW").toUpperCase());
        
        if (material == null) {
            plugin.getLogger().warning("Invalid material for navigation button: " + type);
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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final String title = event.getView().getTitle();
        final String titlePrefix = MessageUtils.colorize(
            plugin.getConfig().getString("menu.restore.title", "&cBroken Items List"));

        if (!title.startsWith(titlePrefix)) {
            return;
        }

        event.setCancelled(true);

        final ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        final ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null || meta.getDisplayName() == null) {
            handleItemRestoration(player, clickedItem);
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
            handleItemRestoration(player, clickedItem);
        }
    }

    private int calculateRestorationCost(ItemStack item) {
        final List<Integer> costs = plugin.getConfig().getIntegerList("costs");
        final int defaultCost = plugin.getConfig().getInt("default-cost-without-unbreaking", 500);
        final int advancedEnchantmentCost = plugin.getConfig().getInt("advanced-enchantment-cost", 30000);

        int baseCost = defaultCost;

        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Check if item has AdvancedEnchantments custom data
            final var customData = meta.getPersistentDataContainer();
            boolean hasAdvancedEnchantment = customData.getKeys().stream()
                .anyMatch(key -> key.toString().contains("advancedenchantments:ae_enchantment"));
            
            if (hasAdvancedEnchantment) {
                baseCost = advancedEnchantmentCost;
            }

            // Use the new NamespacedKey system for Unbreaking enchantment
            Enchantment unbreaking = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
            if (unbreaking != null && meta.hasEnchant(unbreaking)) {
                final int unbreakingLevel = meta.getEnchantLevel(unbreaking);
                if (unbreakingLevel > 0 && unbreakingLevel <= costs.size()) {
                    baseCost = costs.get(unbreakingLevel - 1);
                }
            }

            // Check for custom data multipliers (applied after base cost)
            final var multipliers = plugin.getConfig().getConfigurationSection("cost-multipliers");
            
            if (multipliers != null && !customData.isEmpty()) {
                for (String multiplierKey : multipliers.getKeys(false)) {
                    if (customData.getKeys().stream()
                        .anyMatch(key -> key.toString().contains(multiplierKey))) {
                        double multiplier = multipliers.getDouble(multiplierKey, 1.0);
                        baseCost = (int) (baseCost * multiplier);
                        break; // Apply only first matching multiplier
                    }
                }
            }
        }
        
        return baseCost;
    }

    private void addRestorationCostLore(ItemStack item) {
        final int cost = calculateRestorationCost(item);
        final String loreFormat = plugin.getConfig().getString(
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
        // Remove cost lore
        final ItemStack cleanItem = removeCostLore(displayItem.clone());
        
        // Add to inventory
        player.getInventory().addItem(cleanItem);
        
        // Withdraw money
        economy.withdrawPlayer(player, cost);
        
        // Remove from data file
        dataConfig.set("restoreItem." + key, null);
        plugin.saveDataFile(playerUUID, dataConfig);
        
        // Send success message
        sendMessage(player, "messages.restore-success");
        
        // Log recovery
        logRecovery(player.getName(), cleanItem);
        
        // Close inventory
        player.closeInventory();
    }

    private ItemStack removeCostLore(ItemStack item) {
        final ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasLore()) {
            final List<String> lore = meta.getLore().stream()
                .filter(line -> !line.contains("ราคาที่ต้องจ่าย:") && 
                              !line.contains("Restoration Cost"))
                .collect(Collectors.toList());
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isSameItem(ItemStack item1, ItemStack item2) {
        if (item1 == null || item2 == null || item1.getType() != item2.getType()) {
            return false;
        }

        final ItemMeta meta1 = item1.getItemMeta();
        final ItemMeta meta2 = item2.getItemMeta();

        if (meta1 == null && meta2 == null) {
            return true;
        }

        if (meta1 == null || meta2 == null) {
            return false;
        }

        // Create clones to compare without lore
        final ItemStack clone1 = item1.clone();
        final ItemStack clone2 = item2.clone();
        final ItemMeta cloneMeta1 = clone1.getItemMeta();
        final ItemMeta cloneMeta2 = clone2.getItemMeta();
        
        if (cloneMeta1 != null && cloneMeta2 != null) {
            cloneMeta1.setLore(null);
            cloneMeta2.setLore(null);
            clone1.setItemMeta(cloneMeta1);
            clone2.setItemMeta(cloneMeta2);
        }

        return clone1.isSimilar(clone2);
    }

    private void logRecovery(String playerName, ItemStack item) {
        final File logFile = new File(plugin.getDataFolder(), "log-recovery.yml");
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
        
        final String logEntry = String.format("%s restored %dx %s%s%s at %s",
            playerName, item.getAmount(), displayName, lore, enchantmentsStr, currentTime);

        final List<String> dailyLogs = new ArrayList<>(logConfig.getStringList(currentDate));
        dailyLogs.add(logEntry);
        logConfig.set(currentDate, dailyLogs);

        try {
            logConfig.save(logFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save recovery log", e);
        }
    }

    private void sendMessage(Player player, String path) {
        final String message = plugin.getConfig().getString(path);
        if (message != null && !message.isEmpty()) {
            player.sendMessage(MessageUtils.colorize(message));
        }
    }
}
