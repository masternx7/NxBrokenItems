package dev.fluffyworld.nxbrokenitems.commands;

import dev.fluffyworld.nxbrokenitems.NxBrokenItems;
import dev.fluffyworld.nxbrokenitems.gui.BrokenItemsGUI;
import dev.fluffyworld.nxbrokenitems.utils.MessageUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.*;
import java.util.stream.Collectors;

public final class NxBrokenItemsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList("restore", "reload");
    private static final String PERMISSION_RESTORE = "nxbrokenitems.restore";
    private static final String PERMISSION_RELOAD = "nxbrokenitems.reload";

    private final NxBrokenItems plugin;
    private final BrokenItemsGUI brokenItemsGUI;
    private final Economy economy;

    public NxBrokenItemsCommand(NxBrokenItems plugin) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.economy = setupEconomy();
        
        if (economy != null) {
            this.brokenItemsGUI = new BrokenItemsGUI(plugin, economy);
            plugin.getLogger().info("Economy system initialized successfully");
        } else {
            plugin.getLogger().warning("Vault not found! Economy functions will be disabled.");
            this.brokenItemsGUI = null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            sendMessage(player, "messages.usage");
            return true;
        }

        final String subCommand = args[0].toLowerCase();
        
        return switch (subCommand) {
            case "restore" -> handleRestoreCommand(player);
            case "reload" -> handleReloadCommand(player);
            default -> {
                sendMessage(player, "messages.usage");
                yield true;
            }
        };
    }

    /**
     * Handle the restore subcommand
     */
    private boolean handleRestoreCommand(Player player) {
        if (!player.hasPermission(PERMISSION_RESTORE)) {
            sendMessage(player, "messages.no-permission");
            return true;
        }

        if (brokenItemsGUI == null) {
            player.sendMessage("§cVault is not enabled, this command is disabled.");
            return true;
        }

        brokenItemsGUI.openInventory(player);
        return true;
    }

    /**
     * Handle the reload subcommand
     */
    private boolean handleReloadCommand(Player player) {
        if (!player.hasPermission(PERMISSION_RELOAD)) {
            sendMessage(player, "messages.no-permission");
            return true;
        }

        reloadPlugin(player);
        return true;
    }

    /**
     * Reload plugin configuration and player data
     */
    private void reloadPlugin(Player player) {
        final UUID playerUUID = player.getUniqueId();
        plugin.reloadDataFile(playerUUID);
        plugin.reloadConfig();
        sendMessage(player, "messages.reload-success");
        plugin.getLogger().info("Configuration reloaded by " + player.getName());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                .filter(subCommand -> hasPermissionForSubCommand(player, subCommand))
                .filter(subCommand -> subCommand.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    /**
     * Check if player has permission for a specific subcommand
     */
    private boolean hasPermissionForSubCommand(Player player, String subCommand) {
        return switch (subCommand) {
            case "restore" -> player.hasPermission(PERMISSION_RESTORE);
            case "reload" -> player.hasPermission(PERMISSION_RELOAD);
            default -> false;
        };
    }

    /**
     * Setup Vault economy system
     */
    private Economy setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return null;
        }

        final RegisteredServiceProvider<Economy> rsp = 
            Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        
        return rsp != null ? rsp.getProvider() : null;
    }

    /**
     * Send a message to the player from config
     */
    private void sendMessage(Player player, String path) {
        final String message = plugin.getConfig().getString(path);
        if (message != null && !message.isEmpty()) {
            player.sendMessage(MessageUtils.colorize(message));
        }
    }
}
