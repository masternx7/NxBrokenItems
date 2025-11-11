package dev.fluffyworld.nxbrokenitems.gui.components;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Objects;

public final class ItemComparator {

    public static boolean isSameItem(ItemStack item1, ItemStack item2) {
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
}
