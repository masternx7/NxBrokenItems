package dev.fluffyworld.nxbrokenitems.model;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;

public class BrokenItem {
    private ItemStack itemStack;
    private Location location;
    private LocalDateTime breakTime;
    private String serverName;

    public BrokenItem() {
    }

    public BrokenItem(ItemStack itemStack, Location location) {
        this.itemStack = itemStack;
        this.location = location;
        this.breakTime = LocalDateTime.now();
    }

    public String serializeItemStack() {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            dataOutput.writeObject(itemStack);
            dataOutput.close();

            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize ItemStack", e);
        }
    }

    public void deserializeItemStack(String data) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

            itemStack = (ItemStack) dataInput.readObject();
            dataInput.close();
        } catch (ClassNotFoundException | IOException e) {
            throw new RuntimeException("Failed to deserialize ItemStack", e);
        }
    }

    // Getters and Setters
    public ItemStack getItemStack() {
        return itemStack;
    }

    public void setItemStack(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public LocalDateTime getBreakTime() {
        return breakTime;
    }

    public void setBreakTime(LocalDateTime breakTime) {
        this.breakTime = breakTime;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }
}
