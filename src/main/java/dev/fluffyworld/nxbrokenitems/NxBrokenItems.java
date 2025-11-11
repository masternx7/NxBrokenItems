package dev.fluffyworld.nxbrokenitems;

import dev.fluffyworld.nxbrokenitems.commands.NxBrokenItemsCommand;
import dev.fluffyworld.nxbrokenitems.listeners.ItemBreakListener;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;

public final class NxBrokenItems extends JavaPlugin {

    private static final String DATA_USER_FOLDER = "dataUser";
    private static final String LOG_RECOVERY_FILE = "log-recovery.yml";
    private static final String LOG_DELETE_FILE = "log-item-delete.yml";
    private static final String RESTORE_ITEM_SECTION = "restoreItem";

    @Override
    public void onEnable() {
        // Register event listeners
        getServer().getPluginManager().registerEvents(new ItemBreakListener(this), this);

        // Register command executor and tab completer
        final NxBrokenItemsCommand commandExecutor = new NxBrokenItemsCommand(this);
        Objects.requireNonNull(getCommand("nxbrokenitems"), "Command 'nxbrokenitems' not found")
            .setExecutor(commandExecutor);
        getCommand("nxbrokenitems").setTabCompleter(commandExecutor);

        // Save default config
        saveDefaultConfig();

        // Create required directories and files
        initializePluginFiles();

        getLogger().info("NxBrokenItems has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("NxBrokenItems has been disabled!");
    }

    /**
     * Initialize all required plugin files and directories
     */
    private void initializePluginFiles() {
        createDataUserDirectory();
        createLogFile(LOG_RECOVERY_FILE);
        createLogFile(LOG_DELETE_FILE);
    }

    /**
     * Create the dataUser directory if it doesn't exist
     */
    private void createDataUserDirectory() {
        final Path dataUserPath = getDataFolder().toPath().resolve(DATA_USER_FOLDER);
        try {
            if (!Files.exists(dataUserPath)) {
                Files.createDirectories(dataUserPath);
                getLogger().info("Created dataUser directory");
            }
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to create dataUser directory", e);
        }
    }

    /**
     * Create a log file if it doesn't exist
     */
    private void createLogFile(String fileName) {
        final File logFile = new File(getDataFolder(), fileName);
        if (!logFile.exists()) {
            try {
                if (logFile.createNewFile()) {
                    final FileConfiguration logConfig = YamlConfiguration.loadConfiguration(logFile);
                    logConfig.set("logs", new ArrayList<String>());
                    logConfig.save(logFile);
                    getLogger().info("Created " + fileName);
                }
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to create " + fileName, e);
            }
        }
    }

    /**
     * Get the data configuration for a specific player
     * 
     * @param playerUUID The UUID of the player
     * @return The FileConfiguration or null if file doesn't exist
     */
    public FileConfiguration getDataConfig(UUID playerUUID) {
        final File playerDataFile = getPlayerDataFile(playerUUID);
        if (!playerDataFile.exists()) {
            return null;
        }
        return YamlConfiguration.loadConfiguration(playerDataFile);
    }

    /**
     * Save data file for a specific player
     * 
     * @param playerUUID The UUID of the player
     * @param dataConfig The FileConfiguration to save
     */
    public void saveDataFile(UUID playerUUID, FileConfiguration dataConfig) {
        final File playerDataFile = getPlayerDataFile(playerUUID);
        try {
            dataConfig.save(playerDataFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, 
                "Failed to save data file for player: " + playerUUID, e);
        }
    }

    /**
     * Create a data file for a specific player
     * 
     * @param playerUUID The UUID of the player
     * @return The created FileConfiguration
     */
    public FileConfiguration createDataFile(UUID playerUUID) {
        final File playerDataFile = getPlayerDataFile(playerUUID);
        
        if (!playerDataFile.exists()) {
            try {
                // Ensure parent directory exists
                final File parentDir = playerDataFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
                
                // Create the file
                if (playerDataFile.createNewFile()) {
                    getLogger().info("Created data file for player: " + playerUUID);
                }
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, 
                    "Failed to create data file for player: " + playerUUID, e);
            }
        }
        
        return YamlConfiguration.loadConfiguration(playerDataFile);
    }

    /**
     * Reload the data file for a specific player
     * 
     * @param playerUUID The UUID of the player
     */
    public void reloadDataFile(UUID playerUUID) {
        final File playerDataFile = getPlayerDataFile(playerUUID);
        
        if (playerDataFile.exists()) {
            final FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
            try {
                dataConfig.load(playerDataFile);
                getLogger().info("Reloaded data file for player: " + playerUUID);
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, 
                    "Failed to reload data file for player: " + playerUUID, e);
            }
        } else {
            createDataFile(playerUUID);
        }
    }

    /**
     * Get the player data file
     * 
     * @param playerUUID The UUID of the player
     * @return The player's data file
     */
    private File getPlayerDataFile(UUID playerUUID) {
        return new File(getDataFolder(), 
            DATA_USER_FOLDER + File.separator + playerUUID + ".yml");
    }
}
