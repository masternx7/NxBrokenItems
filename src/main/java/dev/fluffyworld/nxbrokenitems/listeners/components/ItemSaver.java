package dev.fluffyworld.nxbrokenitems.listeners.components;

import dev.fluffyworld.nxbrokenitems.NxBrokenItems;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class ItemSaver {

    private final NxBrokenItems plugin;

    public ItemSaver(NxBrokenItems plugin) {
        this.plugin = plugin;
    }

    public boolean saveItemToDataFile(UUID playerUUID, ItemStack itemToSave) {
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

        final boolean hasAdvancedEnchantment = ItemValidator.hasAdvancedEnchantment(itemToSave);

        if (hasAdvancedEnchantment) {
            for (String key : keys) {
                final ItemStack storedItem = finalDataConfig.getItemStack("restoreItem." + key + ".item");
                if (storedItem != null && DuplicateChecker.isSameItemType(storedItem, itemToSave)) {
                    final long storedTime = finalDataConfig.getLong("restoreItem." + key + ".timestamp", 0);
                    
                    if (storedTime > 0 && (currentTime - storedTime) <= 30000) {
                        foundDuplicate = true;
                        duplicateKey = key;
                        break;
                    } else if (storedTime == 0) {
                        finalDataConfig.set("restoreItem." + key, null);
                    }
                }
            }
        } else {
            for (String key : keys) {
                final ItemStack storedItem = finalDataConfig.getItemStack("restoreItem." + key + ".item");
                if (storedItem != null && DuplicateChecker.isSameItemType(storedItem, itemToSave)) {
                    finalDataConfig.set("restoreItem." + key, null);
                }
            }
        }

        if (foundDuplicate && duplicateKey != null) {
            plugin.getLogger().log(Level.INFO, 
                "Duplicate AdvancedEnchantment item detected for player " + playerUUID + " within 30 seconds - Keeping old item, ignoring new one");
            plugin.saveDataFile(playerUUID, finalDataConfig);
            return true;
        }

        int itemIndex = 0;
        while (keys.contains(String.valueOf(itemIndex))) {
            itemIndex++;
        }

        finalDataConfig.set("restoreItem." + itemIndex + ".item", itemToSave);
        finalDataConfig.set("restoreItem." + itemIndex + ".timestamp", currentTime);
        finalDataConfig.set("restoreItem." + itemIndex + ".blacklisted", ItemValidator.hasBlacklistedCustomData(itemToSave, plugin.getConfig()));
        plugin.saveDataFile(playerUUID, finalDataConfig);
        
        return false;
    }
}
