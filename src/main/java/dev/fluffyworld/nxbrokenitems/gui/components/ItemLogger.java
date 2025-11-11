package dev.fluffyworld.nxbrokenitems.gui.components;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class ItemLogger {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_ONLY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final File dataFolder;
    private final Logger logger;

    public ItemLogger(File dataFolder, Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    public void logRecovery(String playerName, ItemStack item) {
        final File logFile = new File(dataFolder, "log-recovery.yml");
        final FileConfiguration logConfig = YamlConfiguration.loadConfiguration(logFile);

        final LocalDateTime now = LocalDateTime.now();
        final String currentTime = now.format(DATE_FORMAT);
        final String currentDate = now.format(DATE_ONLY_FORMAT);

        final String logEntry = formatLogEntry(playerName, item, "restored", currentTime);

        final List<String> dailyLogs = new ArrayList<>(logConfig.getStringList(currentDate));
        dailyLogs.add(logEntry);
        logConfig.set(currentDate, dailyLogs);

        saveLog(logFile, logConfig);
    }

    public void logDeletion(String playerName, ItemStack item) {
        final File logFile = new File(dataFolder, "log-item-delete.yml");
        final FileConfiguration logConfig = YamlConfiguration.loadConfiguration(logFile);

        final LocalDateTime now = LocalDateTime.now();
        final String currentTime = now.format(DATE_FORMAT);
        final String currentDate = now.format(DATE_ONLY_FORMAT);

        final String logEntry = formatLogEntry(playerName, item, "deleted", currentTime);

        final List<String> dailyLogs = new ArrayList<>(logConfig.getStringList(currentDate));
        dailyLogs.add(logEntry);
        logConfig.set(currentDate, dailyLogs);

        saveLog(logFile, logConfig);
    }

    private String formatLogEntry(String playerName, ItemStack item, String action, String time) {
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
        
        return String.format("%s %s %dx %s%s%s at %s",
            playerName, action, item.getAmount(), displayName, lore, enchantmentsStr, time);
    }

    private void saveLog(File logFile, FileConfiguration logConfig) {
        try {
            logConfig.save(logFile);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save log file: " + logFile.getName(), e);
        }
    }
}
