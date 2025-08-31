package dev.fluffyworld.nxbrokenitems.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.fluffyworld.nxbrokenitems.NxBrokenItems;
import dev.fluffyworld.nxbrokenitems.model.BrokenItem;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {
    private final NxBrokenItems plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(NxBrokenItems plugin) {
        this.plugin = plugin;
        setupDatabase();
    }

    private void setupDatabase() {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("database.mysql");
        HikariConfig hikariConfig = new HikariConfig();

        hikariConfig.setJdbcUrl("jdbc:mysql://" +
                config.getString("host") + ":" +
                config.getInt("port") + "/" +
                config.getString("database"));
        hikariConfig.setUsername(config.getString("username"));
        hikariConfig.setPassword(config.getString("password"));

        // HikariCP settings
        hikariConfig.setMaximumPoolSize(config.getInt("options.maximumPoolSize", 10));
        hikariConfig.setMinimumIdle(config.getInt("options.minimumIdle", 5));
        hikariConfig.setConnectionTimeout(config.getLong("options.connectionTimeout", 30000));

        try {
            dataSource = new HikariDataSource(hikariConfig);
            createTables();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database connection!", e);
        }
    }

    private void createTables() {
        String sql = "CREATE TABLE IF NOT EXISTS broken_items (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "player_uuid VARCHAR(36) NOT NULL," +
                "item_data TEXT NOT NULL," +
                "break_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "server_name VARCHAR(50)," +
                "world VARCHAR(50)," +
                "x DOUBLE," +
                "y DOUBLE," +
                "z DOUBLE" +
                ")";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create tables!", e);
        }
    }

    public void saveBrokenItem(UUID playerUUID, BrokenItem brokenItem) {
        String sql = "INSERT INTO broken_items (player_uuid, item_data, server_name, world, x, y, z) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUUID.toString());
            stmt.setString(2, brokenItem.serializeItemStack());
            stmt.setString(3, plugin.getConfig().getString("server-name", "default"));
            stmt.setString(4, brokenItem.getLocation().getWorld().getName());
            stmt.setDouble(5, brokenItem.getLocation().getX());
            stmt.setDouble(6, brokenItem.getLocation().getY());
            stmt.setDouble(7, brokenItem.getLocation().getZ());

            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save broken item!", e);
        }
    }

    public List<BrokenItem> getBrokenItems(UUID playerUUID) {
        List<BrokenItem> items = new ArrayList<>();
        String sql = "SELECT * FROM broken_items WHERE player_uuid = ?";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUUID.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    BrokenItem item = new BrokenItem();
                    item.deserializeItemStack(rs.getString("item_data"));
                    item.setBreakTime(rs.getTimestamp("break_time").toLocalDateTime());
                    item.setServerName(rs.getString("server_name"));
                    // Add other data as needed
                    items.add(item);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load broken items!", e);
        }

        return items;
    }

    public void deleteBrokenItem(int itemId) {
        String sql = "DELETE FROM broken_items WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, itemId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete broken item!", e);
        }
    }

    public void cleanup() {
        int days = plugin.getConfig().getInt("storage.cleanup-after-days", 30);
        if (days <= 0)
            return;

        String sql = "DELETE FROM broken_items WHERE break_time < DATE_SUB(NOW(), INTERVAL ? DAY)";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, days);
            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                plugin.getLogger().info("Cleaned up " + deleted + " old broken items records");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to cleanup old records!", e);
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
