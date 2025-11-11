package dev.fluffyworld.nxbrokenitems.listeners.components;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class ItemValidator {

    public static boolean isWhitelisted(ItemStack item, FileConfiguration config) {
        final List<String> whitelist = config.getStringList("whitelist");
        return whitelist.contains(item.getType().name());
    }

    public static boolean hasBlacklistedCustomData(ItemStack item, FileConfiguration config) {
        final List<String> blacklistCustomData = config.getStringList("blacklist.custom-data");
        
        if (blacklistCustomData.isEmpty()) {
            return false;
        }

        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        final var customData = meta.getPersistentDataContainer();
        
        for (String blacklistedKey : blacklistCustomData) {
            if (customData.getKeys().stream()
                .anyMatch(key -> key.toString().contains(blacklistedKey))) {
                return true;
            }
        }
        
        return false;
    }

    public static boolean hasAdvancedEnchantment(ItemStack item) {
        final ItemMeta meta = item.getItemMeta();
        return meta != null && 
            meta.getPersistentDataContainer().getKeys().stream()
                .anyMatch(key -> key.toString().contains("advancedenchantments:ae_enchantment"));
    }
}
