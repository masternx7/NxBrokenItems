package dev.fluffyworld.nxbrokenitems.listeners;

import dev.fluffyworld.nxbrokenitems.NxBrokenItems;
import dev.fluffyworld.nxbrokenitems.listeners.components.*;
import dev.fluffyworld.nxbrokenitems.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
    private final ItemSaver itemSaver;

    public ItemBreakListener(NxBrokenItems plugin) {
        this.plugin = plugin;
        this.itemSaver = new ItemSaver(plugin);
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

        if (newDamage >= maxDurability) {
            aboutToBreakItems.put(player.getUniqueId(), item.clone());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemBreak(PlayerItemBreakEvent event) {
        final Player player = event.getPlayer();
        final UUID playerUUID = player.getUniqueId();
        
        ItemStack brokenItem = aboutToBreakItems.remove(playerUUID);
        
        if (brokenItem == null) {
            brokenItem = event.getBrokenItem();
        }
        
        if (brokenItem == null || brokenItem.getType() == Material.AIR) {
            plugin.getLogger().warning("Item break detected but item is AIR for player: " + player.getName());
            return;
        }

        final FileConfiguration config = plugin.getConfig();

        if (!ItemValidator.isWhitelisted(brokenItem, config)) {
            return;
        }

        final long currentTime = System.currentTimeMillis();
        
        if (ItemValidator.hasAdvancedEnchantment(brokenItem) && isDuplicateBreakEvent(playerUUID, brokenItem, currentTime)) {
            plugin.getLogger().log(Level.WARNING, 
                "Prevented duplicate item break event for player: " + player.getName());
            return;
        }

        final ItemStack itemToProcess = brokenItem.clone();
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            verifyAndSaveItem(player, playerUUID, itemToProcess, currentTime);
        }, 2L);
    }

    private boolean isDuplicateBreakEvent(UUID playerUUID, ItemStack brokenItem, long currentTime) {
        final BrokenItemInfo lastBrokenItemInfo = lastBrokenItems.get(playerUUID);
        
        if (lastBrokenItemInfo == null) {
            return false;
        }

        return lastBrokenItemInfo.isSameItem(brokenItem) && 
               (currentTime - lastBrokenItemInfo.getTimestamp() <= 5000);
    }

    private void verifyAndSaveItem(Player player, UUID playerUUID, ItemStack brokenItem, long currentTime) {
        final boolean hasAdvancedEnchantment = ItemValidator.hasAdvancedEnchantment(brokenItem);

        if (hasAdvancedEnchantment && playerHasItem(player, brokenItem)) {
            plugin.getLogger().log(Level.WARNING, 
                "Item duplication attempt prevented for player: " + player.getName() + 
                " - AdvancedEnchantment item still in inventory");
            return;
        }

        final String itemHash = DuplicateChecker.generateItemHash(brokenItem);
        final Set<String> playerHashes = playerBrokenItemHashes.computeIfAbsent(
            playerUUID, k -> ConcurrentHashMap.newKeySet());

        if (playerHashes.contains(itemHash)) {
            plugin.getLogger().log(Level.WARNING, 
                "Duplicate item detected for player: " + player.getName());
            return;
        }

        final FileConfiguration config = plugin.getConfig();
        final boolean repairOnRecovery = config.getBoolean("repair-on-recovery");

        final ItemStack itemToSave = brokenItem.clone();
        
        if (repairOnRecovery) {
            itemToSave.setDurability((short) 0);
        }

        lastBrokenItems.put(playerUUID, new BrokenItemInfo(itemToSave, currentTime));
        playerHashes.add(itemHash);

        final boolean isDuplicate = itemSaver.saveItemToDataFile(playerUUID, itemToSave);
        
        if (isDuplicate) {
            plugin.getLogger().log(Level.WARNING, 
                "Duplicate item saved within 30 seconds for player: " + player.getName() + " - Kept old item");
        }

        final String message = config.getString("messages.item-broken");
        if (message != null && !message.isEmpty()) {
            player.sendMessage(MessageUtils.colorize(message));
        }

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
            .anyMatch(item -> DuplicateChecker.isSameItemIgnoreDurability(item, brokenItem));
    }
}
