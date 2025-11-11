package dev.fluffyworld.nxbrokenitems.listeners.components;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Objects;

public final class DuplicateChecker {

    public static boolean isSameItemType(ItemStack item1, ItemStack item2) {
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

    public static boolean isSameItemIgnoreDurability(ItemStack item1, ItemStack item2) {
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

    public static String generateItemHash(ItemStack item) {
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
}
