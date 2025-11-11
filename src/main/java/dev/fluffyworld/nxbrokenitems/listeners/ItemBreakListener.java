package dev.fluffyworld.nxbrokenitems.listeners;

import dev.fluffyworld.nxbrokenitems.NxBrokenItems;
import dev.fluffyworld.nxbrokenitems.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ItemBreakListener implements Listener {

    private final NxBrokenItems plugin;
    private final Map<UUID, BrokenItemInfo> lastBrokenItems = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerBrokenItemHashes = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> aboutToBreakItems = new ConcurrentHashMap<>();

    public ItemBreakListener(NxBrokenItems plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        final Player player = event.getPlayer();
        final ItemStack item = event.getItem();
        
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        final ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }

        final int maxDurability = item.getType().getMaxDurability();
        final int currentDamage = damageable.getDamage();
        final int newDamage = currentDamage + event.getDamage();

        // Check if item will break after this damage
        if (newDamage >= maxDurability) {
            // Store the item before it breaks
            aboutToBreakItems.put(player.getUniqueId(), item.clone());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemBreak(PlayerItemBreakEvent event) {
        final Player player = event.getPlayer();
        final UUID playerUUID = player.getUniqueId();
        
        // Get the item we stored before it broke
        ItemStack brokenItem = aboutToBreakItems.remove(playerUUID);
        
        // Fallback to event's broken item if we didn't catch it
        if (brokenItem == null) {
            brokenItem = event.getBrokenItem();
        }
        
        if (brokenItem == null || brokenItem.getType() == Material.AIR) {
            plugin.getLogger().warning("Item break detected but item is AIR for player: " + player.getName());
            return;
        }

        final Material brokenItemType = brokenItem.getType();
        final FileConfiguration config = plugin.getConfig();
        final List<String> whitelist = config.getStringList("whitelist");

        // Check whitelist
        if (!whitelist.contains(brokenItemType.name())) {
            return;
        }

        // Check blacklist lore (now returns false, we save blacklisted items)
        final boolean isBlacklisted = hasBlacklistedCustomData(brokenItem, config);
        // We still save the item even if blacklisted, just mark it

        // Anti-duplication: Check if this is a duplicate break event
        final long currentTime = System.currentTimeMillis();
        if (isDuplicateBreakEvent(playerUUID, brokenItem, currentTime)) {
            plugin.getLogger().log(Level.WARNING, 
                "Prevented duplicate item break event for player: " + player.getName());
            return;
        }

        // Clone the item to preserve it
        final ItemStack itemToProcess = brokenItem.clone();
        
        // Schedule verification task to prevent duplication exploit
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            verifyAndSaveItem(player, playerUUID, itemToProcess, currentTime);
        }, 2L); // Wait 2 ticks to ensure item is actually broken
    }

    private boolean isBlacklisted(ItemStack item, FileConfiguration config) {
        final List<String> blacklistCustomData = config.getStringList("blacklist.custom-data");
        
        if (blacklistCustomData.isEmpty()) {
            return false;
        }

        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        // Get custom data from the item
        final var customData = meta.getPersistentDataContainer();
        
        // Check if any blacklisted custom data key exists
        for (String blacklistedKey : blacklistCustomData) {
            // Check in the format "namespace:key"
            if (customData.getKeys().stream()
                .anyMatch(key -> key.toString().contains(blacklistedKey))) {
                return false; // Don't block saving, just mark as blacklisted
            }
        }
        
        return false;
    }

    private boolean hasBlacklistedCustomData(ItemStack item, FileConfiguration config) {
        final List<String> blacklistCustomData = config.getStringList("blacklist.custom-data");
        
        if (blacklistCustomData.isEmpty()) {
            return false;
        }

        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        // Get custom data from the item
        final var customData = meta.getPersistentDataContainer();
        
        // Check if any blacklisted custom data key exists
        for (String blacklistedKey : blacklistCustomData) {
            if (customData.getKeys().stream()
                .anyMatch(key -> key.toString().contains(blacklistedKey))) {
                return true;
            }
        }
        
        return false;
    }

    private boolean isDuplicateBreakEvent(UUID playerUUID, ItemStack brokenItem, long currentTime) {
        final BrokenItemInfo lastBrokenItemInfo = lastBrokenItems.get(playerUUID);
        
        if (lastBrokenItemInfo == null) {
            return false;
        }

        // Check if same item broken within 5 seconds (possible duplication)
        return lastBrokenItemInfo.isSameItem(brokenItem) && 
               (currentTime - lastBrokenItemInfo.getTimestamp() <= 5000);
    }

    private void verifyAndSaveItem(Player player, UUID playerUUID, ItemStack brokenItem, long currentTime) {
        // Check if item has AdvancedEnchantments
        final ItemMeta itemMeta = brokenItem.getItemMeta();
        final boolean hasAdvancedEnchantment = itemMeta != null && 
            itemMeta.getPersistentDataContainer().getKeys().stream()
                .anyMatch(key -> key.toString().contains("advancedenchantments:ae_enchantment"));

        // Anti-duplication: Verify item is not in player's inventory (only for AdvancedEnchantments)
        if (hasAdvancedEnchantment && playerHasItem(player, brokenItem)) {
            plugin.getLogger().log(Level.WARNING, 
                "Item duplication attempt prevented for player: " + player.getName() + 
                " - AdvancedEnchantment item still in inventory");
            return;
        }

        // Generate unique hash for this item
        final String itemHash = generateItemHash(brokenItem);
        final Set<String> playerHashes = playerBrokenItemHashes.computeIfAbsent(
            playerUUID, k -> ConcurrentHashMap.newKeySet());

        // Check if this exact item was already saved
        if (playerHashes.contains(itemHash)) {
            plugin.getLogger().log(Level.WARNING, 
                "Duplicate item detected for player: " + player.getName());
            return;
        }

        final FileConfiguration config = plugin.getConfig();
        final boolean repairOnRecovery = config.getBoolean("repair-on-recovery");

        // Clone the item to avoid reference issues
        final ItemStack itemToSave = brokenItem.clone();
        
        if (repairOnRecovery) {
            itemToSave.setDurability((short) 0);
        }

        // Update last broken item info
        lastBrokenItems.put(playerUUID, new BrokenItemInfo(itemToSave, currentTime));
        playerHashes.add(itemHash);

        // Save to data file and check for duplicates
        final boolean isDuplicate = saveItemToDataFile(playerUUID, itemToSave);
        
        if (isDuplicate) {
            plugin.getLogger().log(Level.WARNING, 
                "Duplicate item saved within 30 seconds for player: " + player.getName() + " - Kept old item");
        }

        // Send message to player
        final String message = config.getString("messages.item-broken");
        if (message != null && !message.isEmpty()) {
            player.sendMessage(MessageUtils.colorize(message));
        }

        // Clean up old hashes after 10 seconds
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            playerHashes.remove(itemHash);
            if (playerHashes.isEmpty()) {
                playerBrokenItemHashes.remove(playerUUID);
            }
        }, 200L);
    }

    private boolean playerHasItem(Player player, ItemStack brokenItem) {
        return Arrays.stream(player.getInventory().getContents())
            .filter(Objects::nonNull)
            .anyMatch(item -> isSameItemIgnoreDurability(item, brokenItem));
    }

    private boolean isSameItemIgnoreDurability(ItemStack item1, ItemStack item2) {
        if (item1 == null || item2 == null) {
            return false;
        }

        if (item1.getType() != item2.getType()) {
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

        return Objects.equals(meta1.getDisplayName(), meta2.getDisplayName()) &&
               Objects.equals(meta1.getLore(), meta2.getLore()) &&
               Objects.equals(meta1.getEnchants(), meta2.getEnchants());
    }

    private String generateItemHash(ItemStack item) {
        final StringBuilder hash = new StringBuilder();
        hash.append(item.getType().name());
        
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            hash.append(":").append(meta.getDisplayName());
            if (meta.hasLore()) {
                hash.append(":").append(String.join(",", meta.getLore()));
            }
            hash.append(":").append(meta.getEnchants().toString());
        }
        
        hash.append(":").append(System.nanoTime());
        return Integer.toHexString(hash.toString().hashCode());
    }

    private boolean saveItemToDataFile(UUID playerUUID, ItemStack itemToSave) {
        FileConfiguration dataConfig = plugin.getDataConfig(playerUUID);
        if (dataConfig == null) {
            dataConfig = plugin.createDataFile(playerUUID);
        }

        final ConfigurationSection restoreSection = dataConfig.isConfigurationSection("restoreItem") 
            ? dataConfig.getConfigurationSection("restoreItem")
            : dataConfig.createSection("restoreItem");

        final Set<String> keys = restoreSection.getKeys(false);
        final FileConfiguration finalDataConfig = dataConfig;
        final long currentTime = System.currentTimeMillis();
        
        boolean foundDuplicate = false;
        String duplicateKey = null;

        // Check if item has AdvancedEnchantments
        final ItemMeta itemMeta = itemToSave.getItemMeta();
        final boolean hasAdvancedEnchantment = itemMeta != null && 
            itemMeta.getPersistentDataContainer().getKeys().stream()
                .anyMatch(key -> key.toString().contains("advancedenchantments:ae_enchantment"));

        // Only check for duplicates if item has AdvancedEnchantments
        if (hasAdvancedEnchantment) {
            // Check for duplicates within 30 seconds (30000ms)
            for (String key : keys) {
                final ItemStack storedItem = finalDataConfig.getItemStack("restoreItem." + key + ".item");
                if (storedItem != null && isSameItemType(storedItem, itemToSave)) {
                    // Check if this item has a timestamp
                    final long storedTime = finalDataConfig.getLong("restoreItem." + key + ".timestamp", 0);
                    
                    if (storedTime > 0 && (currentTime - storedTime) <= 30000) {
                        // Found duplicate within 30 seconds - mark for deletion
                        foundDuplicate = true;
                        duplicateKey = key;
                        break;
                    } else if (storedTime == 0) {
                        // Old format without timestamp - remove it
                        finalDataConfig.set("restoreItem." + key, null);
                    }
                }
            }
        } else {
            // For non-AdvancedEnchantment items, remove old duplicates without time check
            for (String key : keys) {
                final ItemStack storedItem = finalDataConfig.getItemStack("restoreItem." + key + ".item");
                if (storedItem != null && isSameItemType(storedItem, itemToSave)) {
                    // Remove old duplicate
                    finalDataConfig.set("restoreItem." + key, null);
                }
            }
        }

        // If duplicate found within 30 seconds, don't save new one
        if (foundDuplicate && duplicateKey != null) {
            // Don't remove old item, just don't save the new duplicate
            plugin.getLogger().log(Level.INFO, 
                "Duplicate AdvancedEnchantment item detected for player " + playerUUID + " within 30 seconds - Keeping old item, ignoring new one");
            plugin.saveDataFile(playerUUID, finalDataConfig);
            return true; // Indicate duplicate was found
        }

        // Find next available index
        int itemIndex = 0;
        while (keys.contains(String.valueOf(itemIndex))) {
            itemIndex++;
        }

        // Save item with timestamp in separate structure
        finalDataConfig.set("restoreItem." + itemIndex + ".item", itemToSave);
        finalDataConfig.set("restoreItem." + itemIndex + ".timestamp", currentTime);
        finalDataConfig.set("restoreItem." + itemIndex + ".blacklisted", hasBlacklistedCustomData(itemToSave, plugin.getConfig()));
        plugin.saveDataFile(playerUUID, finalDataConfig);
        
        return false; // No duplicate found
    }

    private boolean isSameItemType(ItemStack item1, ItemStack item2) {
        if (item1 == null || item2 == null) {
            return false;
        }
        
        if (item1.getType() != item2.getType()) {
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
        
        // Compare without durability
        return Objects.equals(meta1.getDisplayName(), meta2.getDisplayName()) &&
               Objects.equals(meta1.getLore(), meta2.getLore()) &&
               Objects.equals(meta1.getEnchants(), meta2.getEnchants());
    }

    private static final class BrokenItemInfo {
        private final ItemStack item;
        private final long timestamp;

        private BrokenItemInfo(ItemStack item, long timestamp) {
            this.item = item;
            this.timestamp = timestamp;
        }

        private boolean isSameItem(ItemStack otherItem) {
            if (otherItem == null) {
                return false;
            }

            if (!item.getType().equals(otherItem.getType())) {
                return false;
            }

            final ItemMeta thisMeta = item.getItemMeta();
            final ItemMeta otherMeta = otherItem.getItemMeta();

            if (thisMeta == null && otherMeta == null) {
                return true;
            }

            if (thisMeta == null || otherMeta == null) {
                return false;
            }

            return Objects.equals(thisMeta.getDisplayName(), otherMeta.getDisplayName()) &&
                   Objects.equals(thisMeta.getLore(), otherMeta.getLore()) &&
                   Objects.equals(thisMeta.getEnchants(), otherMeta.getEnchants());
        }

        private long getTimestamp() {
            return timestamp;
        }
    }
}
