package dev.fluffyworld.nxbrokenitems;

import dev.fluffyworld.nxbrokenitems.commands.NxBrokenItemsCommand;
import dev.fluffyworld.nxbrokenitems.config.ConfigManager;
import dev.fluffyworld.nxbrokenitems.database.DatabaseManager;
import dev.fluffyworld.nxbrokenitems.gui.BrokenItemsGUI;
import dev.fluffyworld.nxbrokenitems.gui.DeleteItemsGUI;
import dev.fluffyworld.nxbrokenitems.listeners.ItemBreakListener;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

public final class NxBrokenItems extends JavaPlugin {

    private Economy economy;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private DeleteItemsGUI deleteItemsGUI;

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    @Override
    public void onEnable() {
        // Initialize configuration
        configManager = new ConfigManager(this);

        // Setup database
        databaseManager = new DatabaseManager(this);

        // Setup economy
        if (!setupEconomy()) {
            getLogger().warning("Vault not found! Economy features will be disabled.");
        }

        // Register listeners
        getServer().getPluginManager().registerEvents(new ItemBreakListener(this), this);

        // Register commands
        NxBrokenItemsCommand commandExecutor = new NxBrokenItemsCommand(this);
        getCommand("nxbrokenitems").setExecutor(commandExecutor);
        getCommand("nxbrokenitems").setTabCompleter(commandExecutor);

        // Create necessary files
        saveDefaultConfig();
        createLogRecoveryFile();
        createLogDeleteFile();

        // Schedule cleanup task
        if (getConfig().getInt("storage.cleanup-after-days", 30) > 0) {
            getServer().getScheduler().runTaskTimerAsynchronously(this,
                    () -> databaseManager.cleanup(),
                    20L * 60 * 60, // 1 hour delay
                    20L * 60 * 60 * 24); // Run daily
        }
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public FileConfiguration getDataConfig(UUID playerUUID) {
        File playerDataFile = new File(getDataFolder(), "dataUser" + File.separator + playerUUID.toString() + ".yml");
        if (!playerDataFile.exists()) {
            return null;
        }
        return YamlConfiguration.loadConfiguration(playerDataFile);
    }

    public void saveDataFile(UUID playerUUID, FileConfiguration dataConfig) {
        File playerDataFile = new File(getDataFolder(), "dataUser" + File.separator + playerUUID.toString() + ".yml");
        try {
            dataConfig.save(playerDataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public FileConfiguration createDataFile(UUID playerUUID) {
        File playerDataFile = new File(getDataFolder(), "dataUser" + File.separator + playerUUID.toString() + ".yml");
        if (!playerDataFile.exists()) {
            try {
                playerDataFile.getParentFile().mkdirs();
                playerDataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return YamlConfiguration.loadConfiguration(playerDataFile);
    }

    public void reloadDataFile(UUID playerUUID) {
        File playerDataFile = new File(getDataFolder(), "dataUser" + File.separator + playerUUID.toString() + ".yml");
        if (playerDataFile.exists()) {
            FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
            try {
                dataConfig.load(playerDataFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            createDataFile(playerUUID);
        }
    }

    private void createLogRecoveryFile() {
        File logFile = new File(getDataFolder(), "log-recovery.yml");
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
                FileConfiguration logConfig = YamlConfiguration.loadConfiguration(logFile);
                logConfig.set("logs", new ArrayList<String>());
                logConfig.save(logFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void createLogDeleteFile() {
        File logFile = new File(getDataFolder(), "log-item-delete.yml");
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
                FileConfiguration logConfig = YamlConfiguration.loadConfiguration(logFile);
                logConfig.set("logs", new ArrayList<String>());
                logConfig.save(logFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
