package dev.fluffyworld.nxbrokenitems.listeners.components;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Objects;

public final class BrokenItemInfo {
    private final ItemStack item;
    private final long timestamp;

    public BrokenItemInfo(ItemStack item, long timestamp) {
        this.item = item;
        this.timestamp = timestamp;
    }

    public boolean isSameItem(ItemStack otherItem) {
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

    public long getTimestamp() {
        return timestamp;
    }
}
