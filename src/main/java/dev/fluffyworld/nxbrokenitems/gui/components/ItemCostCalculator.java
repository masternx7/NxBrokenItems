package dev.fluffyworld.nxbrokenitems.gui.components;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class ItemCostCalculator {

    private final FileConfiguration config;

    public ItemCostCalculator(FileConfiguration config) {
        this.config = config;
    }

    public int calculateRestorationCost(ItemStack item) {
        final List<Integer> costs = config.getIntegerList("costs");
        final int defaultCost = config.getInt("default-cost-without-unbreaking", 500);
        final int advancedEnchantmentCost = config.getInt("advanced-enchantment-cost", 30000);

        int baseCost = defaultCost;

        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final var customData = meta.getPersistentDataContainer();
            boolean hasAdvancedEnchantment = customData.getKeys().stream()
                .anyMatch(key -> key.toString().contains("advancedenchantments:ae_enchantment"));
            
            if (hasAdvancedEnchantment) {
                baseCost = advancedEnchantmentCost;
            }

            Enchantment unbreaking = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
            if (unbreaking != null && meta.hasEnchant(unbreaking)) {
                final int unbreakingLevel = meta.getEnchantLevel(unbreaking);
                if (unbreakingLevel > 0 && unbreakingLevel <= costs.size()) {
                    baseCost = costs.get(unbreakingLevel - 1);
                }
            }

            final var multipliers = config.getConfigurationSection("cost-multipliers");
            
            if (multipliers != null && !customData.isEmpty()) {
                for (String multiplierKey : multipliers.getKeys(false)) {
                    if (customData.getKeys().stream()
                        .anyMatch(key -> key.toString().contains(multiplierKey))) {
                        double multiplier = multipliers.getDouble(multiplierKey, 1.0);
                        baseCost = (int) (baseCost * multiplier);
                        break;
                    }
                }
            }
        }
        
        return baseCost;
    }
}
