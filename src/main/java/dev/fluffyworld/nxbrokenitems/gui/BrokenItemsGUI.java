package dev.fluffyworld.nxbrokenitems.gui;

import dev.fluffyworld.nxbrokenitems.NxBrokenItems;
import dev.fluffyworld.nxbrokenitems.utils.MessageUtils;
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

import java.util.ArrayList;
import java.util.List;

public class BrokenItemsGUI implements Listener {
    private final NxBrokenItems plugin;

    public BrokenItemsGUI(NxBrokenItems plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection guiSection = config.getConfigurationSection("broken-items");
        if (guiSection == null) {
            player.sendMessage(MessageUtils.colorize("&cGUI configuration not found!"));
            return;
        }

        String title = MessageUtils.colorize(guiSection.getString("title", "&8Broken Items List"));
        int size = guiSection.getInt("size", 54);
        Inventory inv = Bukkit.createInventory(null, size, title);

        // Set background items
        ConfigurationSection bgSection = guiSection.getConfigurationSection("items.background");
        if (bgSection != null) {
            setBackgroundItems(inv, bgSection);
        }

        // Set navigation items (close button)
        ConfigurationSection closeSection = guiSection.getConfigurationSection("items.close");
        if (closeSection != null) {
            setCloseButton(inv, closeSection);
        }

        // Add broken items
        FileConfiguration data = plugin.getDataConfig(player.getUniqueId());
        if (data != null && data.contains("restoreItem")) {
            setBrokenItems(inv, data, guiSection);
        }

        player.openInventory(inv);
    }

    private void setBackgroundItems(Inventory inv, ConfigurationSection section) {
        String materialName = section.getString("material", "BLACK_STAINED_GLASS_PANE");
        Material material = Material.valueOf(materialName);
        String name = MessageUtils.colorize(section.getString("name", " "));

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            int modelData = section.getInt("custom-model-data", 0);
            if (modelData > 0) {
                meta.setCustomModelData(modelData);
            }
            item.setItemMeta(meta);
        }

        for (int slot : section.getIntegerList("slots")) {
            inv.setItem(slot, item);
        }
    }

    private void setCloseButton(Inventory inv, ConfigurationSection section) {
        Material material = Material.valueOf(section.getString("material", "BARRIER"));
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtils.colorize(section.getString("name", "&cClose")));
            int modelData = section.getInt("custom-model-data", 0);
            if (modelData > 0) {
                meta.setCustomModelData(modelData);
            }
            item.setItemMeta(meta);
        }
        inv.setItem(section.getInt("slot", 49), item);
    }

    private void setBrokenItems(Inventory inv, FileConfiguration data, ConfigurationSection guiSection) {
        ConfigurationSection itemsSection = data.getConfigurationSection("restoreItem");
        if (itemsSection == null)
            return;

        ConfigurationSection brokenItemSection = guiSection.getConfigurationSection("items.broken-item");
        if (brokenItemSection == null)
            return;

        String slotsStr = brokenItemSection.getString("slots", "9-44");
        String[] slotsRange = slotsStr.split("-");
        int startSlot = Integer.parseInt(slotsRange[0]);
        int endSlot = slotsRange.length > 1 ? Integer.parseInt(slotsRange[1]) : startSlot;
        int currentSlot = startSlot;

        for (String key : itemsSection.getKeys(false)) {
            if (currentSlot > endSlot)
                break;

            ItemStack item = itemsSection.getItemStack(key);
            if (item != null) {
                inv.setItem(currentSlot++, item);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;
        Player player = (Player) event.getWhoClicked();

        String title = MessageUtils.colorize(plugin.getConfig().getString("broken-items.title", "&8Broken Items List"));
        if (!event.getView().getTitle().equals(title))
            return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR)
            return;

        int slot = event.getSlot();
        int closeSlot = plugin.getConfig().getInt("broken-items.items.close.slot", 49);

        if (slot == closeSlot) {
            player.closeInventory();
            return;
        }

        FileConfiguration data = plugin.getDataConfig(player.getUniqueId());
        if (data == null || !data.contains("restoreItem"))
            return;

        String slotsStr = plugin.getConfig().getString("broken-items.items.broken-item.slots", "9-44");
        String[] slotsRange = slotsStr.split("-");
        int startSlot = Integer.parseInt(slotsRange[0]);
        int endSlot = slotsRange.length > 1 ? Integer.parseInt(slotsRange[1]) : startSlot;

        if (slot >= startSlot && slot <= endSlot) {
            restoreItem(player, clickedItem, data);
        }
    }

    private void restoreItem(Player player, ItemStack item, FileConfiguration data) {
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(MessageUtils.colorize("&cYour inventory is full!"));
            return;
        }

        // Remove from broken items
        ConfigurationSection items = data.getConfigurationSection("restoreItem");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ItemStack storedItem = items.getItemStack(key);
                if (storedItem != null && storedItem.equals(item)) {
                    data.set("restoreItem." + key, null);
                    plugin.saveDataFile(player.getUniqueId(), data);
                    break;
                }
            }
        }

        // Give item to player
        player.getInventory().addItem(item.clone());
        player.sendMessage(MessageUtils.colorize("&aItem has been restored!"));

        // Refresh inventory
        open(player);
    }
}

public class BrokenItemsGUI implements Listener {
    private final NxBrokenItems plugin;

    public BrokenItemsGUI(NxBrokenItems plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openInventory(Player player, int page) {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection guiSection = config.getConfigurationSection(GUI_PATH);
        if (guiSection == null) {
            player.sendMessage(MessageUtils.colorize("&cGUI configuration not found!"));
            return;
        }

        String title = MessageUtils.colorize(guiSection.getString("title", "&8Broken Items List"));
        int size = guiSection.getInt("size", 54);
        Inventory inv = Bukkit.createInventory(null, size, title);

        // Set background items
        ConfigurationSection bgSection = guiSection.getConfigurationSection("items.background");
        if (bgSection != null) {
            setBackgroundItems(inv, bgSection);
        }

        // Set broken items
        FileConfiguration playerData = plugin.getDataConfig(player.getUniqueId());
        if (playerData != null && playerData.contains("restoreItem")) {
            setBrokenItems(inv, playerData, guiSection, page);
        }

        // Set navigation items
        ConfigurationSection navSection = guiSection.getConfigurationSection("items");
        if (navSection != null) {
            setNavigationItems(inv, navSection, page);
        }

        player.openInventory(inv);
        playerPageMap.put(player.getUniqueId(), page);
    }

    private void setBackgroundItems(Inventory inv, ConfigurationSection section) {
        String materialName = section.getString("material", "BLACK_STAINED_GLASS_PANE");
        Material material = Material.valueOf(materialName);
        String name = MessageUtils.colorize(section.getString("name", " "));
        int modelData = section.getInt("custom-model-data", 0);

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (modelData > 0) {
                meta.setCustomModelData(modelData);
            }
            item.setItemMeta(meta);
        }

        List<Integer> slots = section.getIntegerList("slots");
        for (int slot : slots) {
            inv.setItem(slot, item);
        }
    }

    private void setBrokenItems(Inventory inv, FileConfiguration playerData, ConfigurationSection guiSection,
            int page) {
        ConfigurationSection brokenSection = guiSection.getConfigurationSection("items.broken-item");
        if (brokenSection == null)
            return;

        ConfigurationSection itemsSection = playerData.getConfigurationSection("restoreItem");
        if (itemsSection == null)
            return;

        List<String> slots = Arrays.asList(brokenSection.getString("slots", "9-44").split("-"));
        int startSlot = Integer.parseInt(slots.get(0));
        int endSlot = slots.size() > 1 ? Integer.parseInt(slots.get(1)) : startSlot;

        int itemsPerPage = endSlot - startSlot + 1;
        int startIndex = (page - 1) * itemsPerPage;

        Set<String> keys = itemsSection.getKeys(false);
        List<String> keyList = new ArrayList<>(keys);

        for (int i = 0; i < itemsPerPage && (startIndex + i) < keyList.size(); i++) {
            String key = keyList.get(startIndex + i);
            ItemStack brokenItem = itemsSection.getItemStack(key);
            if (brokenItem != null) {
                inv.setItem(startSlot + i, brokenItem);
            }
        }
    }

    private void setNavigationItems(Inventory inv, ConfigurationSection section, int page) {
        setNavigationItem(inv, section.getConfigurationSection("previous-page"), page > 1);
        setNavigationItem(inv, section.getConfigurationSection("next-page"), true); // We'll handle this in click event
        setNavigationItem(inv, section.getConfigurationSection("close"), true);
    }

    private void setNavigationItem(Inventory inv, ConfigurationSection section, boolean show) {
        if (section == null || !show)
            return;

        String materialName = section.getString("material");
        if (materialName == null)
            return;

        Material material = Material.valueOf(materialName);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtils.colorize(section.getString("name", "")));
            int modelData = section.getInt("custom-model-data", 0);
            if (modelData > 0) {
                meta.setCustomModelData(modelData);
            }
            List<String> lore = section.getStringList("lore");
            if (!lore.isEmpty()) {
                meta.setLore(MessageUtils.colorize(lore));
            }
            item.setItemMeta(meta);
        }

        int slot = section.getInt("slot", 0);
        inv.setItem(slot, item);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;
        Player player = (Player) event.getWhoClicked();

        if (!playerPageMap.containsKey(player.getUniqueId()))
            return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR)
            return;

        int slot = event.getSlot();
        int currentPage = playerPageMap.get(player.getUniqueId());

        ConfigurationSection guiSection = plugin.getConfig().getConfigurationSection(GUI_PATH);
        if (guiSection == null)
            return;

        // Handle navigation clicks
        if (slot == guiSection.getInt("items.previous-page.slot", 45) && currentPage > 1) {
            openInventory(player, currentPage - 1);
            return;
        }
        if (slot == guiSection.getInt("items.next-page.slot", 53)) {
            openInventory(player, currentPage + 1);
            return;
        }
        if (slot == guiSection.getInt("items.close.slot", 49)) {
            player.closeInventory();
            return;
        }

        // Handle broken item clicks
        String slots = guiSection.getString("items.broken-item.slots", "9-44");
        String[] slotRange = slots.split("-");
        int startSlot = Integer.parseInt(slotRange[0]);
        int endSlot = slotRange.length > 1 ? Integer.parseInt(slotRange[1]) : startSlot;

        if (slot >= startSlot && slot <= endSlot) {
            handleBrokenItemClick(player, clickedItem);
        }
    }

    private void handleBrokenItemClick(Player player, ItemStack item) {
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(MessageUtils
                    .colorize(plugin.getConfig().getString("messages.inventory-full", "&cYour inventory is full!")));
            return;
        }

        // Give item to player
        player.getInventory().addItem(item.clone());

        // Remove from broken items
        FileConfiguration playerData = plugin.getDataConfig(player.getUniqueId());
        if (playerData != null && playerData.contains("restoreItem")) {
            ConfigurationSection items = playerData.getConfigurationSection("restoreItem");
            if (items != null) {
                for (String key : items.getKeys(false)) {
                    ItemStack storedItem = items.getItemStack(key);
                    if (storedItem != null && storedItem.equals(item)) {
                        playerData.set("restoreItem." + key, null);
                        plugin.saveDataFile(player.getUniqueId(), playerData);
                        break;
                    }
                }
            }
        }

        player.sendMessage(MessageUtils
                .colorize(plugin.getConfig().getString("messages.item-restored", "&aItem has been restored!")));

        // Refresh inventory
        int currentPage = playerPageMap.getOrDefault(player.getUniqueId(), 1);
        openInventory(player, currentPage);
    }

    public void openInventory(Player player, int page) {
        UUID playerUUID = player.getUniqueId();
        FileConfiguration dataConfig = plugin.getDataConfig(playerUUID);

        if (dataConfig == null || !dataConfig.contains("restoreItem")) {
            player.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("messages.no-broken-items")));
            return;
        }

        String title = MessageUtils.colorize(plugin.getConfig().getString("menu.restore.title", "&cBroken Items List"));
        int size = plugin.getConfig().getInt("menu.restore.size", 54);
        int itemsPerPage = size - 9; // Reserving last row for navigation

        Inventory inventory = Bukkit.createInventory(null, size, title);

        List<ItemStack> items = new ArrayList<>();
        for (String key : dataConfig.getConfigurationSection("restoreItem").getKeys(false)) {
            ItemStack item = dataConfig.getItemStack("restoreItem." + key);
            if (item != null) {
                addRestorationCostLore(item);
                items.add(item);
            }
        }

        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, items.size());

        for (int i = startIndex; i < endIndex; i++) {
            inventory.addItem(items.get(i));
        }

        if (page > 0) {
            ItemStack prevPage = createNavigationItem("previous-page");
            inventory.setItem(size - 9, prevPage);
        }

        if (endIndex < items.size()) {
            ItemStack nextPage = createNavigationItem("next-page");
            inventory.setItem(size - 1, nextPage);
        }

        player.openInventory(inventory);
        playerPageMap.put(playerUUID, page);
    }

    public void openInventory(Player player) {
        openInventory(player, 0);
    }

    private ItemStack createNavigationItem(String type) {
        FileConfiguration config = plugin.getConfig();
        Material material = Material.valueOf(
                config.getString("menu.restore.navigation-buttons." + type + ".material", "ARROW").toUpperCase());
        int customModelData = config.getInt("menu.restore.navigation-buttons." + type + ".custom-model-data", 0);
        String displayName = MessageUtils
                .colorize(config.getString("menu.restore.navigation-buttons." + type + ".display-name", ""));

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            if (customModelData != 0) {
                meta.setCustomModelData(customModelData);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String titlePrefix = MessageUtils
                .colorize(plugin.getConfig().getString("menu.restore.title", "&cBroken Items List"));
        if (event.getView().getTitle().startsWith(titlePrefix)) {
            event.setCancelled(true);

            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) {
                return;
            }

            Player player = (Player) event.getWhoClicked();
            UUID playerUUID = player.getUniqueId();
            ItemStack item = event.getCurrentItem();

            FileConfiguration config = plugin.getConfig();
            String nextPageDisplayName = MessageUtils.colorize(
                    config.getString("menu.restore.navigation-buttons.next-page.display-name", "&aNext Page"));
            String prevPageDisplayName = MessageUtils.colorize(
                    config.getString("menu.restore.navigation-buttons.previous-page.display-name", "&aPrevious Page"));

            if (item.getItemMeta() != null && item.getItemMeta().getDisplayName() != null) {
                String itemName = item.getItemMeta().getDisplayName();
                int currentPage = playerPageMap.getOrDefault(playerUUID, 0);

                if (itemName.equals(nextPageDisplayName)) {
                    openInventory(player, currentPage + 1);
                } else if (itemName.equals(prevPageDisplayName)) {
                    openInventory(player, currentPage - 1);
                } else {
                    handleItemRestoration(player, item);
                }
                return;
            }

            handleItemRestoration(player, item);
        }
    }

    private int calculateRestorationCost(ItemStack item) {
        List<Integer> costs = plugin.getConfig().getIntegerList("costs");
        int defaultCostWithoutUnbreaking = plugin.getConfig().getInt("default-cost-without-unbreaking", 30000);

        if (item.getItemMeta() != null && item.getItemMeta().hasEnchant(Enchantment.DURABILITY)) {
            int unbreakingLevel = item.getEnchantmentLevel(Enchantment.DURABILITY);
            if (unbreakingLevel > 0 && unbreakingLevel <= costs.size()) {
                return costs.get(unbreakingLevel - 1);
            }
        }
        return defaultCostWithoutUnbreaking;
    }

    private void addRestorationCostLore(ItemStack item) {
        int cost = calculateRestorationCost(item);
        String loreFormat = plugin.getConfig().getString("menu.restore.lore.format", "&eRestoration Cost: &6{cost}");
        String formattedLore = MessageUtils.colorize(loreFormat.replace("{cost}", String.valueOf(cost)));

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.add(formattedLore);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
    }

    private void handleItemRestoration(Player player, ItemStack item) {
        UUID playerUUID = player.getUniqueId();
        FileConfiguration dataConfig = plugin.getDataConfig(playerUUID);
        int cost = calculateRestorationCost(item) * item.getAmount();

        if (PlaceholderAPI.setPlaceholders(player, "%fluffy_isfull%").equalsIgnoreCase("true")) {
            player.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("messages.inventory-full")));
            return;
        }

        if (economy == null || !economy.has(player, cost)) {
            player.sendMessage(MessageUtils.colorize(
                    plugin.getConfig().getString("messages.not-enough-money").replace("{cost}", String.valueOf(cost))));
            return;
        }

        for (String key : dataConfig.getConfigurationSection("restoreItem").getKeys(false)) {
            ItemStack storedItem = dataConfig.getItemStack("restoreItem." + key);
            if (storedItem != null && isSameItem(storedItem, item)) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasLore()) {
                    List<String> lore = meta.getLore();
                    if (lore != null) {
                        lore = lore.stream()
                                .filter(line -> !line.contains("ราคาที่ต้องจ่าย:"))
                                .collect(Collectors.toList());
                        meta.setLore(lore);
                        item.setItemMeta(meta);
                    }
                }

                player.getInventory().addItem(item);
                economy.withdrawPlayer(player, cost);
                dataConfig.set("restoreItem." + key, null);
                plugin.saveDataFile(playerUUID, dataConfig);
                player.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("messages.restore-success")));

                logRecovery(player.getName(), item);

                player.closeInventory();
                return;
            }
        }
    }

    private boolean isSameItem(ItemStack item1, ItemStack item2) {
        if (item1 == null || item2 == null) {
            return false;
        }
        ItemMeta meta1 = item1.getItemMeta();
        ItemMeta meta2 = item2.getItemMeta();

        if (meta1 == null || meta2 == null) {
            return item1.getType() == item2.getType();
        }

        meta1.setLore(null);
        meta2.setLore(null);

        ItemStack clone1 = item1.clone();
        ItemStack clone2 = item2.clone();
        clone1.setItemMeta(meta1);
        clone2.setItemMeta(meta2);

        return clone1.isSimilar(clone2);
    }

    private void logRecovery(String playerName, ItemStack item) {
        File logFile = new File(plugin.getDataFolder(), "log-recovery.yml");
        FileConfiguration logConfig = YamlConfiguration.loadConfiguration(logFile);

        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String currentDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

        String displayName = item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName()
                : item.getType().name();
        List<String> loreList = item.getItemMeta().hasLore() ? item.getItemMeta().getLore() : List.of();

        Map<Enchantment, Integer> enchantments = item.getItemMeta().getEnchants();
        String enchantmentsStr = enchantments.isEmpty() ? ""
                : " with enchantments: " + enchantments.entrySet().stream()
                        .map(e -> e.getKey().getKey().getKey() + " " + e.getValue())
                        .collect(Collectors.joining(", "));

        String lore = loreList.isEmpty() ? "" : " with lore: " + String.join(", ", loreList);
        String logEntry = playerName + " restored " + item.getAmount() + "x " + displayName + lore + enchantmentsStr
                + " at " + currentTime;

        if (!logConfig.contains("logs")) {
            logConfig.set("logs", new ArrayList<>());
        }

        List<String> dailyLogs = logConfig.getStringList(currentDate);
        dailyLogs.add(logEntry);
        logConfig.set(currentDate, dailyLogs);

        try {
            logConfig.save(logFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
