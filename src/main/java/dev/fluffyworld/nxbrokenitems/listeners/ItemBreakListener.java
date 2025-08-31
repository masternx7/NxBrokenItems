package dev.fluffyworld.nxbrokenitems.listeners;

import dev.fluffyworld.nxbrokenitems.NxBrokenItems;
import dev.fluffyworld.nxbrokenitems.model.BrokenItem;
import dev.fluffyworld.nxbrokenitems.utils.MessageUtils;
import dev.fluffyworld.nxbrokenitems.gui.BrokenItemsGUI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.HashMap;

public class ItemBreakListener implements Listener {

    private final NxBrokenItems plugin;
    private final HashMap<UUID, BrokenItemInfo> lastBrokenItems = new HashMap<>();

    public ItemBreakListener(NxBrokenItems plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onItemBreak(PlayerItemBreakEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        ItemStack brokenItem = event.getBrokenItem();
        Material brokenItemType = brokenItem.getType();

        FileConfiguration config = plugin.getConfig();
        List<String> whitelist = config.getStringList("whitelist");

        // Check if item type is in whitelist
        if (!whitelist.contains(brokenItemType.name())) {
            return;
        }

        // Check for Advanced Enchantments

        // Additional blacklist checks
        List<String> blacklistLores = config.getStringList("blacklist.lore");
        ItemMeta meta = brokenItem.getItemMeta();
        if (meta != null && meta.hasLore()) {
            List<String> itemLores = meta.getLore();
            if (itemLores != null) {
                for (String lore : itemLores) {
                    for (String blacklistedLore : blacklistLores) {
                        if (lore.contains(blacklistedLore)) {
                            return;
                        }
                    }
                }
            }
        }

        long currentTime = System.currentTimeMillis();
        if (lastBrokenItems.containsKey(playerUUID)) {
            BrokenItemInfo lastBrokenItemInfo = lastBrokenItems.get(playerUUID);
            if (lastBrokenItemInfo.isSameItem(brokenItem)
                    && (currentTime - lastBrokenItemInfo.getTimestamp() <= 5000)) {
                return;
            }
        }

        boolean repairOnRecovery = config.getBoolean("repair-on-recovery");

        if (repairOnRecovery) {
            if (meta != null && meta instanceof org.bukkit.inventory.meta.Damageable) {
                ((org.bukkit.inventory.meta.Damageable) meta).setDamage(0);
                brokenItem.setItemMeta(meta);
            }
        }

        // Save location information
        Location loc = player.getLocation();
        BrokenItem brokenItemData = new BrokenItem(brokenItem.clone(), loc);

        // Add to database if using MySQL
        if (plugin.getConfig().getBoolean("server.multi-server", false)) {
            plugin.getDatabaseManager().saveBrokenItem(playerUUID, brokenItemData);
        }

        lastBrokenItems.put(playerUUID, new BrokenItemInfo(brokenItem, currentTime));

        FileConfiguration dataConfig = plugin.getDataConfig(playerUUID);
        if (dataConfig == null) {
            dataConfig = plugin.createDataFile(playerUUID);
        }

        if (!dataConfig.isConfigurationSection("restoreItem")) {
            dataConfig.createSection("restoreItem");
        }

        Set<String> keys = dataConfig.getConfigurationSection("restoreItem").getKeys(false);

        for (String key : keys) {
            ItemStack storedItem = dataConfig.getItemStack("restoreItem." + key);
            if (storedItem != null && storedItem.isSimilar(brokenItem)) {
                dataConfig.set("restoreItem." + key, null);
                break;
            }
        }

        int itemIndex = keys.size();

        while (keys.contains(String.valueOf(itemIndex))) {
            itemIndex++;
        }

        dataConfig.set("restoreItem." + itemIndex, brokenItem);
        plugin.saveDataFile(playerUUID, dataConfig);

        String message = config.getString("messages.item-broken");
        if (message != null && !message.isEmpty()) {
            player.sendMessage(MessageUtils.colorize(message));
        }

        // Open GUI if auto-open is enabled
        if (config.getBoolean("settings.auto-open-gui", false)) {
            new BrokenItemsGUI(plugin, player).open();
        }
    }

    private static class BrokenItemInfo {
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

            ItemMeta thisMeta = item.getItemMeta();
            ItemMeta otherMeta = otherItem.getItemMeta();

            if (thisMeta == null && otherMeta == null) {
                return true;
            }

            if (thisMeta == null || otherMeta == null) {
                return false;
            }

            if (!thisMeta.getDisplayName().equals(otherMeta.getDisplayName())) {
                return false;
            }

            if (thisMeta.hasLore() && otherMeta.hasLore()) {
                List<String> thisLore = thisMeta.getLore();
                List<String> otherLore = otherMeta.getLore();
                if (!thisLore.equals(otherLore)) {
                    return false;
                }
            } else if (thisMeta.hasLore() || otherMeta.hasLore()) {
                return false;
            }

            return thisMeta.getEnchants().equals(otherMeta.getEnchants());
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
