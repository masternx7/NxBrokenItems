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

    private final File dataFolder;
    private final Logger logger;
    private final FileConfiguration config;

    public ItemLogger(File dataFolder, Logger logger, FileConfiguration config) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.config = config;
    }

    public void logRecovery(String playerName, ItemStack item) {
        final String logFileName = config.getString("logging.recovery-log-file", "log-recovery.yml");
        final File logFile = new File(dataFolder, logFileName);
        final FileConfiguration logConfig = YamlConfiguration.loadConfiguration(logFile);

        final String dateTimeFormat = config.getString("logging.date-time-format", "yyyy-MM-dd HH:mm:ss");
        final String dateOnlyFormat = config.getString("logging.date-only-format", "yyyy-MM-dd");
        
        final LocalDateTime now = LocalDateTime.now();
        final String currentTime = now.format(DateTimeFormatter.ofPattern(dateTimeFormat));
        final String currentDate = now.format(DateTimeFormatter.ofPattern(dateOnlyFormat));

        final String logEntry = formatLogEntry(playerName, item, "restored", currentTime);

        final List<String> dailyLogs = new ArrayList<>(logConfig.getStringList(currentDate));
        dailyLogs.add(logEntry);
        logConfig.set(currentDate, dailyLogs);

        saveLog(logFile, logConfig);
    }

    public void logDeletion(String playerName, ItemStack item) {
        final String logFileName = config.getString("logging.deletion-log-file", "log-item-delete.yml");
        final File logFile = new File(dataFolder, logFileName);
        final FileConfiguration logConfig = YamlConfiguration.loadConfiguration(logFile);

        final String dateTimeFormat = config.getString("logging.date-time-format", "yyyy-MM-dd HH:mm:ss");
        final String dateOnlyFormat = config.getString("logging.date-only-format", "yyyy-MM-dd");
        
        final LocalDateTime now = LocalDateTime.now();
        final String currentTime = now.format(DateTimeFormatter.ofPattern(dateTimeFormat));
        final String currentDate = now.format(DateTimeFormatter.ofPattern(dateOnlyFormat));

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
