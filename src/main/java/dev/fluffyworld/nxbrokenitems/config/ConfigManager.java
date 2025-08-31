package dev.fluffyworld.nxbrokenitems.config;

import dev.fluffyworld.nxbrokenitems.NxBrokenItems;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public class ConfigManager {
    private final NxBrokenItems plugin;
    private FileConfiguration guiConfig;
    private FileConfiguration messagesConfig;
    private File guiFile;
    private File messagesFile;

    public ConfigManager(NxBrokenItems plugin) {
        this.plugin = plugin;
        setupConfigs();
    }

    public void setupConfigs() {
        // Create config.yml if it doesn't exist
        plugin.saveDefaultConfig();

        // Setup gui.yml
        guiFile = new File(plugin.getDataFolder(), "gui.yml");
        if (!guiFile.exists()) {
            plugin.saveResource("gui.yml", false);
        }
        guiConfig = YamlConfiguration.loadConfiguration(guiFile);

        // Setup messages.yml
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public void reloadConfigs() {
        plugin.reloadConfig();
        guiConfig = YamlConfiguration.loadConfiguration(guiFile);
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public void saveConfigs() {
        try {
            guiConfig.save(guiFile);
            messagesConfig.save(messagesFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config files!", e);
        }
    }

    public FileConfiguration getGuiConfig() {
        return guiConfig;
    }

    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }

    public String getMessage(String path) {
        String message = messagesConfig.getString(path);
        if (message == null)
            return "Message not found: " + path;

        String prefix = messagesConfig.getString("prefix", "&8[&bNxBrokenItems&8] &r");
        return plugin.color(prefix + message);
    }

    public String getMessageRaw(String path) {
        String message = messagesConfig.getString(path);
        if (message == null)
            return "Message not found: " + path;
        return plugin.color(message);
    }
}
