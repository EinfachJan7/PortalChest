package dev.jankr.portalchest.gui;

import dev.jankr.portalchest.PortalChestPlugin;
import dev.jankr.portalchest.manager.ConfigManager;
import dev.jankr.portalchest.model.PortalChest;
import dev.jankr.portalchest.util.EconomyUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;

import java.util.*;

public class UpgradeGUI {

    private final PortalChestPlugin plugin;
    private final ConfigManager config;

    public UpgradeGUI(PortalChestPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    public void openUpgradeGUI(Player player, PortalChest chest) {
        // Prüfe: Ist die Chest verlinkt?
        if (chest.getLinkedChest() == null) {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<red>✘ Diese Chest ist nicht verlinkt!"
            ));
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<dark_gray>Verlinke sie zuerst mit einer anderen Chest um zu upgraden."
            ));
            return;
        }

        int currentLevel = chest.getUpgradeLevel();
        
        String title;
        if (chest.getType() == PortalChest.ChestType.SENDER) {
            title = config.getGuiTitle(currentLevel);
        } else {
            title = config.getGuiReceiverTitle();
        }
        
        Inventory gui = plugin.getServer().createInventory(null, 27, 
            plugin.getDisplayManager().parseMessage(title));

        // Fülle mit Glass Panes
        ItemStack glass = createGlassPane();
        for (int i = 0; i < 27; i++) {
            // Für SENDER: Slots 11, 13, 15 aussparen (Level-Upgrade nur)
            // Für RECEIVER: Slot 13 aussparen (nur Double-Chest-Upgrade)
            if (chest.getType() == PortalChest.ChestType.SENDER) {
                if (i != config.getGuiCurrentLevelSlot() && 
                    i != config.getGuiArrowSlot() && 
                    i != config.getGuiNextLevelSlot()) {
                    gui.setItem(i, glass);
                }
            } else {
                if (i != config.getGuiDoubleChestSlot()) {
                    gui.setItem(i, glass);
                }
            }
        }

        // ===== NUR SENDER: Level-Upgrade anzeigen (dynamisch) =====
        if (chest.getType() == PortalChest.ChestType.SENDER) {
            int maxLevels = config.getMaxLevels();
            
            // Slot für aktuelles Level
            ItemStack currentLevel_item = createCurrentLevelItem(currentLevel);
            gui.setItem(config.getGuiCurrentLevelSlot(), currentLevel_item);

            // Slot für Arrow (nur wenn nicht max level)
            if (currentLevel < maxLevels) {
                ItemStack arrow = createArrowItem();
                gui.setItem(config.getGuiArrowSlot(), arrow);
            }

            // Slot für nächstes Level oder Maximales Level
            if (currentLevel < maxLevels) {
                ItemStack nextLevel = createNextLevelItem(player, currentLevel + 1);
                gui.setItem(config.getGuiNextLevelSlot(), nextLevel);
            } else {
                ItemStack maxLevel = createMaxLevelItem(currentLevel);
                gui.setItem(config.getGuiNextLevelSlot(), maxLevel);
            }
        }

        // ===== NUR RECEIVER: Double-Chest-Upgrade anzeigen (wenn aktiviert) =====
        if (chest.getType() == PortalChest.ChestType.RECEIVER && chest.getLinkedChest() != null) {
            // Prüfe: Ist Double-Chest-Upgrade aktiviert und SENDER Level erfüllt?
            int senderLevel = chest.getLinkedChest().getUpgradeLevel();
            if (config.isDoubleChestUpgradeEnabled() && 
                senderLevel >= config.getDoubleChestRequiredLevel()) {
                ItemStack doubleChestUpgrade = createDoubleChestUpgradeItem(player, chest);
                gui.setItem(config.getGuiDoubleChestSlot(), doubleChestUpgrade);
            }
        }

        // Registriere das GUI bei InventoryListener
        String guiType = (chest.getType() == PortalChest.ChestType.SENDER) ? "sender" : "receiver";
        plugin.getInventoryListener().registerGUI(player.getUniqueId(), guiType);
        
        player.openInventory(gui);
    }

    private ItemStack createCurrentLevelItem(int level) {
        String materialName = config.getGuiMaterial("current-level");
        Material material = Material.getMaterial(materialName, false);
        if (material == null) material = Material.DIAMOND_BLOCK;
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // Name und Lore aus config laden
        String name = config.getGuiCurrentLevelName(level);
        meta.displayName(plugin.getDisplayManager().parseMessage(name));

        List<Component> lore = new ArrayList<>();
        for (String loreLine : config.getGuiCurrentLevelLore(level)) {
            lore.add(plugin.getDisplayManager().parseMessage(loreLine));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNextLevelItem(Player player, int level) {
        String materialName = config.getGuiMaterial("next-level");
        Material material = Material.getMaterial(materialName, false);
        if (material == null) material = Material.EMERALD_BLOCK;
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String name = config.getGuiNextLevelName(level);
        int cost = config.getUpgradeCost(level);

        meta.displayName(plugin.getDisplayManager().parseMessage(name));

        List<Component> lore = new ArrayList<>();
        
        // Zähle Währung (Vault oder Items)
        int playerCurrency = countCurrency(player, config.getUpgradeCurrencyMaterial());
        String currencyDisplayName = config.getCurrencyDisplayName();
        
        for (String loreLine : config.getGuiNextLevelLore(level)) {
            String line = loreLine
                .replace("%have%", String.valueOf(playerCurrency))
                .replace("%cost%", String.valueOf(cost))
                .replace("%currency%", currencyDisplayName);
            
            if (playerCurrency < cost) {
                // Filter out success message if not enough currency
                if (!line.contains("✔")) {
                    lore.add(plugin.getDisplayManager().parseMessage(line));
                }
            } else {
                // Filter out error message if enough currency
                if (!line.contains("✘") && !line.contains("Du hast")) {
                    lore.add(plugin.getDisplayManager().parseMessage(line));
                }
            }
        }
        
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createArrowItem() {
        String materialName = config.getGuiMaterial("arrow");
        Material material = Material.getMaterial(materialName, false);
        if (material == null) material = Material.ARROW;
        
        ItemStack arrow = new ItemStack(material);
        ItemMeta meta = arrow.getItemMeta();

        String name = config.getGuiArrowName();
        meta.displayName(plugin.getDisplayManager().parseMessage(name));

        List<Component> lore = new ArrayList<>();
        for (String loreLine : config.getGuiArrowLore()) {
            lore.add(plugin.getDisplayManager().parseMessage(loreLine));
        }
        meta.lore(lore);
        arrow.setItemMeta(meta);
        return arrow;
    }

    private ItemStack createDoubleChestUpgradeItem(Player player, PortalChest chest) {
        String materialName = config.getGuiMaterial("double-chest");
        Material material = Material.getMaterial(materialName, false);
        if (material == null) material = Material.ENDER_CHEST;
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        boolean hasUpgrade = chest.isHasDoubleChestUpgrade();
        
        String title;
        List<String> lorePart;
        if (hasUpgrade) {
            title = config.getGuiDoubleChestNameOn();
            lorePart = config.getGuiDoubleChestLoreOn();
        } else {
            title = config.getGuiDoubleChestNameOff();
            lorePart = config.getGuiDoubleChestLoreOff();
        }

        meta.displayName(plugin.getDisplayManager().parseMessage(title));

        List<Component> lore = new ArrayList<>();
        
        // Hole Double-Chest-Währung (Vault oder Items)
        int playerCurrency = countCurrency(player, config.getDoubleChestCurrencyMaterial());
        int cost = config.getDoubleChestUpgradeCost();
        String currencyDisplayName = config.getCurrencyDisplayName();
        
        for (String loreLine : lorePart) {
            String line = loreLine
                .replace("%have%", String.valueOf(playerCurrency))
                .replace("%cost%", String.valueOf(cost))
                .replace("%currency%", currencyDisplayName);
            
            if (!hasUpgrade && playerCurrency < cost) {
                // Filter out success message if not enough currency
                if (!line.contains("✔")) {
                    lore.add(plugin.getDisplayManager().parseMessage(line));
                }
            } else if (!hasUpgrade && playerCurrency >= cost) {
                // Filter out error message if enough currency
                if (!line.contains("✘") && !line.contains("Du hast")) {
                    lore.add(plugin.getDisplayManager().parseMessage(line));
                }
            } else {
                lore.add(plugin.getDisplayManager().parseMessage(line));
            }
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
    
    private int countCurrency(Player player, Material currencyMaterial) {
        // Vault Economy Support
        if (config.isVaultEconomyUsed()) {
            return (int) EconomyUtil.getMoney(player);
        }
        
        // Items-basierte Währung
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == currencyMaterial) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private ItemStack createMaxLevelItem(int level) {
        String materialName = config.getGuiMaterial("max-level");
        Material material = Material.getMaterial(materialName, false);
        if (material == null) material = Material.GOLD_BLOCK;
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String name = config.getGuiMaxLevelName(level);
        meta.displayName(plugin.getDisplayManager().parseMessage(name));

        List<Component> lore = new ArrayList<>();
        for (String loreLine : config.getGuiMaxLevelLore(level)) {
            lore.add(plugin.getDisplayManager().parseMessage(loreLine));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
    private ItemStack createGlassPane() {
        String materialName = config.getGuiMaterial("glass-pane");
        Material material = Material.getMaterial(materialName, false);
        if (material == null) material = Material.GRAY_STAINED_GLASS_PANE;
        
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        return pane;
    }
}
