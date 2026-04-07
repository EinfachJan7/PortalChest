package dev.jankr.portalchest.listeners;

import dev.jankr.portalchest.PortalChestPlugin;
import dev.jankr.portalchest.model.PortalChest;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class InventoryListener implements Listener {

    private final PortalChestPlugin plugin;
    // Map speichert: Spieler UUID → "sender" oder "receiver" um zu wissen, welches GUI offen ist
    private final Map<UUID, String> openGUIs = new HashMap<>();

    public InventoryListener(PortalChestPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registriert, dass ein Spieler ein Upgrade-GUI offen hat
     */
    public void registerGUI(UUID playerUUID, String type) {
        // type: "sender" oder "receiver"
        openGUIs.put(playerUUID, type);
    }

    /**
     * Entfernt den GUI-Registrierung für einen Spieler
     */
    public void unregisterGUI(UUID playerUUID) {
        openGUIs.remove(playerUUID);
    }

    /**
     * Prüft, ob ein Spieler ein GUI offen hat
     */
    public boolean hasGUIOpen(UUID playerUUID) {
        return openGUIs.containsKey(playerUUID);
    }

    /**
     * Holt den GUI-Typ für einen Spieler
     */
    public String getGUIType(UUID playerUUID) {
        return openGUIs.get(playerUUID);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID playerUUID = player.getUniqueId();

        // Überprüfe ob dieser Spieler ein GUI offen hat
        if (!hasGUIOpen(playerUUID)) {
            return;
        }

        String guiType = getGUIType(playerUUID);
        
        // Lese die Slot-Nummern aus der Config
        int nextLevelSlot = plugin.getConfigManager().getGuiNextLevelSlot();
        int doubleChestSlot = plugin.getConfigManager().getGuiDoubleChestSlot();
        int clickedSlot = event.getSlot();

        // SENDER GUI: Klick auf Upgrade-Button (next-level)
        if ("sender".equals(guiType) && clickedSlot == nextLevelSlot) {
            event.setCancelled(true);
            handleUpgradeClick(player);
            return;
        }

        // RECEIVER GUI: Klick auf Double-Chest-Upgrade-Button
        if ("receiver".equals(guiType) && clickedSlot == doubleChestSlot) {
            event.setCancelled(true);
            handleDoubleChestUpgradeClick(player);
            return;
        }

        // Blockiere alle anderen Clicks im GUI
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Player player = (Player) event.getWhoClicked();
        
        // Blockiere Drag-Operationen wenn GUI offen ist
        if (hasGUIOpen(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        // Entferne GUI-Registrierung wenn der Spieler das Inventar schließt
        unregisterGUI(player.getUniqueId());
        // Entferne auch GUI-Location
        plugin.getGuiLocations().remove(player.getUniqueId());
    }

    private void handleUpgradeClick(Player player) {
        // Lade GUI-Location aus Player-Map
        String locKey = plugin.getGuiLocations().get(player.getUniqueId());
        if (locKey == null) {
            player.sendMessage(plugin.getDisplayManager().parseMessage("<color:#FF6B6B>Fehler: Location nicht gefunden!"));
            player.closeInventory();
            return;
        }

        // Parse Location Key
        Location loc = locationFromKey(locKey);
        if (loc == null) {
            player.sendMessage(plugin.getDisplayManager().parseMessage("<color:#FF6B6B>Fehler: Location ungültig!"));
            player.closeInventory();
            return;
        }

        PortalChest chest = plugin.getDataManager().getChest(loc);
        if (chest == null) {
            player.sendMessage(plugin.getDisplayManager().parseMessage("<color:#FF6B6B>Fehler: Chest nicht gefunden!"));
            player.closeInventory();
            return;
        }

        // Prüfe Berechtigung
        if (!chest.isTrusted(player.getUniqueId())) {
            player.sendMessage(plugin.getDisplayManager().parseMessage("<color:#FF6B6B>Du hast keine Berechtigung für diese Chest!"));
            player.closeInventory();
            return;
        }

        // Level-Upgrade nur für SENDER
        if (chest.getType() != PortalChest.ChestType.SENDER) {
            player.sendMessage(plugin.getDisplayManager().parseMessage("<color:#FF6B6B>Nur Sender-Chests können upgradet werden!"));
            player.closeInventory();
            return;
        }

        // Prüfe aktuelles Level
        int currentLevel = chest.getUpgradeLevel();
        if (currentLevel >= plugin.getConfigManager().getMaxLevels()) {
            player.sendMessage(plugin.getDisplayManager().parseMessage("<color:#FFD700>Maximale Stufe erreicht!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }

        // Nächstes Level
        int nextLevel = currentLevel + 1;
        
        // Prüfe ob dieses Level existiert
        if (nextLevel > plugin.getConfigManager().getMaxLevels()) {
            player.sendMessage(plugin.getDisplayManager().parseMessage("<color:#FFD700>Maximale Stufe erreicht!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }
        
        int cost = plugin.getConfigManager().getUpgradeCost(nextLevel);
        String currencyDisplayName = plugin.getConfigManager().getCurrencyDisplayName();
        
        // Prüfe Währung basierend auf Config
        boolean hasEnough = false;
        if (plugin.getConfigManager().isVaultEconomyUsed()) {
            hasEnough = dev.jankr.portalchest.util.EconomyUtil.hasMoney(player, cost);
        } else {
            org.bukkit.Material currency = plugin.getConfigManager().getUpgradeCurrencyMaterial();
            hasEnough = (countCurrency(player, currency) >= cost);
        }
        
        if (!hasEnough) {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                "<color:#FF6B6B>Nicht genug " + currencyDisplayName + "! (<color:#FFD700>" + cost + " <color:#FF6B6B>benötigt)"
            ));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }

        // Entferne Währung
        boolean currencyRemoved = false;
        if (plugin.getConfigManager().isVaultEconomyUsed()) {
            plugin.getLogger().info("[PortalChest] Nutze Vault Economy für Upgrade - Kosten: " + cost);
            currencyRemoved = dev.jankr.portalchest.util.EconomyUtil.withdrawMoney(player, cost);
            if (!currencyRemoved) {
                player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<color:#FF6B6B>Fehler beim Abbuchen der Währung! Bitte kontaktiere einen Admin."
                ));
                return;
            }
        } else {
            org.bukkit.Material currency = plugin.getConfigManager().getUpgradeCurrencyMaterial();
            removeCurrency(player, cost, currency);
            currencyRemoved = true;
        }

        // Upgrade durchführen (nur wenn Währung erfolgreich entfernt wurde)
        if (!currencyRemoved) {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                "<color:#FF6B6B>Upgrade konnte nicht durchgeführt werden!"
            ));
            return;
        }

        chest.setUpgradeLevel(nextLevel);
        plugin.getDataManager().saveData();

        // Update Display für neues Level
        plugin.getDisplayManager().removeBlockDisplay(locKey);
        org.bukkit.entity.BlockDisplay display = plugin.getDisplayManager().spawnDisplay(loc, nextLevel, "sender");
        if (display != null) {
            plugin.getDisplayManager().registerDisplay(locKey, display.getUniqueId());
        }

        // Schließe GUI und zeige Nachricht
        player.closeInventory();
        
        String color = getColorForLevel(nextLevel);
        int maxItems = plugin.getConfigManager().getMaxItemsForLevel(nextLevel);
        String transferTime = plugin.getConfigManager().getTransferSeconds();

        player.sendMessage(plugin.getDisplayManager().parseMessage("<color:#00FF00><bold>✓ Upgrade erfolgreich!</bold></color>"));
        player.sendMessage(plugin.getDisplayManager().parseMessage(
            color + "<bold>Level " + nextLevel + "</bold> <color:#B0B0B0>(" + maxItems + " Items/" + transferTime + "s)"
        ));

        // Sounds und Partikel
        plugin.getSoundManager().playTeleportSound(loc);
        plugin.getParticleManager().spawnTeleportEffect(loc);

        // Entferne GUI-Location
        plugin.getGuiLocations().remove(player.getUniqueId());
    }

    private int countDiamonds(Player player) {
        if (plugin.getConfigManager().isVaultEconomyUsed()) {
            return (int) dev.jankr.portalchest.util.EconomyUtil.getMoney(player);
        }
        return countCurrency(player, plugin.getConfigManager().getUpgradeCurrencyMaterial());
    }
    
    private int countCurrency(Player player, Material currency) {
        if (currency == null) {
            return 0; // Vault Economy
        }
        
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == currency) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void removeDiamonds(Player player, int amount) {
        if (plugin.getConfigManager().isVaultEconomyUsed()) {
            boolean success = dev.jankr.portalchest.util.EconomyUtil.withdrawMoney(player, amount);
            if (!success) {
                plugin.getLogger().warning("[PortalChest] Fehler beim Abbuchen von " + amount + " für Spieler " + player.getName());
            }
        } else {
            removeCurrency(player, amount, plugin.getConfigManager().getUpgradeCurrencyMaterial());
        }
    }
    
    private void removeCurrency(Player player, int amount, Material currency) {
        int toRemove = amount;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == currency && toRemove > 0) {
                if (item.getAmount() <= toRemove) {
                    toRemove -= item.getAmount();
                    item.setAmount(0);
                } else {
                    item.setAmount(item.getAmount() - toRemove);
                    toRemove = 0;
                }
            }
        }
    }

    private Location locationFromKey(String key) {
        String[] parts = key.split(",");
        if (parts.length != 4) return null;
        
        try {
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            String worldName = parts[3];
            
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
            if (world == null) return null;
            
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void handleDoubleChestUpgradeClick(Player player) {
        // Prüfe ob Double-Chest-Upgrade überhaupt enabled ist
        if (!plugin.getConfigManager().isDoubleChestUpgradeEnabled()) {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                "<red>✘ Das Double-Chest-Upgrade ist nicht verfügbar auf diesem Server!"
            ));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            player.closeInventory();
            return;
        }
        
        String locKey = plugin.getGuiLocations().get(player.getUniqueId());
        if (locKey == null) {
            player.sendMessage(plugin.getDisplayManager().parseMessage("<color:#FF6B6B>Fehler: Location nicht gefunden!"));
            player.closeInventory();
            return;
        }

        Location loc = locationFromKey(locKey);
        if (loc == null) {
            player.sendMessage(plugin.getDisplayManager().parseMessage("<color:#FF6B6B>Fehler: Location ungültig!"));
            player.closeInventory();
            return;
        }

        PortalChest chest = plugin.getDataManager().getChest(loc);
        if (chest == null) {
            player.sendMessage(plugin.getDisplayManager().parseMessage("<color:#FF6B6B>Fehler: Chest nicht gefunden!"));
            player.closeInventory();
            return;
        }

        // Prüfe Berechtigung
        if (!chest.isTrusted(player.getUniqueId())) {
            player.sendMessage(plugin.getDisplayManager().parseMessage("<color:#FF6B6B>Du hast keine Berechtigung für diese Chest!"));
            player.closeInventory();
            return;
        }
        
        // Prüfe ob Mindeststufe erreicht (basierend auf SENDER Level)
        int requiredLevel = plugin.getConfigManager().getDoubleChestRequiredLevel();
        if (chest.getLinkedChest() == null || chest.getLinkedChest().getUpgradeLevel() < requiredLevel) {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                "<red>✘ Der verknüpfte SENDER benötigt Stufe " + requiredLevel + " um dieses Upgrade freizuschalten!"
            ));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }

        // Prüfe ob bereits freigeschaltet
        if (chest.isHasDoubleChestUpgrade()) {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                "<green>✓ Doppelkisten-Upgrade ist bereits aktiviert!"
            ));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;  // GUI schließen
        }

        // Hole Kosten und Währung aus Config
        int cost = plugin.getConfigManager().getDoubleChestUpgradeCost();
        String currencyDisplayName = plugin.getConfigManager().getCurrencyDisplayName();
        
        // Prüfe Währung basierend auf Config
        boolean hasEnough = false;
        if (plugin.getConfigManager().getDoubleChestUpgradeCurrency().equalsIgnoreCase("VAULT_ECONOMY")) {
            hasEnough = dev.jankr.portalchest.util.EconomyUtil.hasMoney(player, cost);
        } else {
            org.bukkit.Material currency = plugin.getConfigManager().getDoubleChestCurrencyMaterial();
            hasEnough = (countCurrency(player, currency) >= cost);
        }
        
        if (!hasEnough) {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                "<color:#FF6B6B>Nicht genug " + currencyDisplayName + "! (<color:#FFD700>" + cost + " <color:#FF6B6B>benötigt)"
            ));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }

        // Entferne Währung
        boolean currencyRemoved = false;
        if (plugin.getConfigManager().getDoubleChestUpgradeCurrency().equalsIgnoreCase("VAULT_ECONOMY")) {
            plugin.getLogger().info("[PortalChest] Nutze Vault Economy für Double-Chest Upgrade - Kosten: " + cost);
            currencyRemoved = dev.jankr.portalchest.util.EconomyUtil.withdrawMoney(player, cost);
            if (!currencyRemoved) {
                player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<color:#FF6B6B>Fehler beim Abbuchen der Währung! Bitte kontaktiere einen Admin."
                ));
                return;
            }
        } else {
            org.bukkit.Material currency = plugin.getConfigManager().getDoubleChestCurrencyMaterial();
            removeCurrency(player, cost, currency);
            currencyRemoved = true;
        }

        // Upgrade durchführen (nur wenn Währung erfolgreich entfernt wurde)
        if (!currencyRemoved) {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                "<color:#FF6B6B>Upgrade konnte nicht durchgeführt werden!"
            ));
            return;
        }

        chest.setHasDoubleChestUpgrade(true);
        player.sendMessage(plugin.getDisplayManager().parseMessage(
            "<color:#00FF00>Doppelkisten-Upgrade freigeschaltet!"
        ));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.7f, 1.2f);

        plugin.getDataManager().saveData();
        
        // WICHTIG: GUI schließen und Registrierung löschen - NICHT openUpgradeGUI aufrufen!
        player.closeInventory();
        unregisterGUI(player.getUniqueId());
        plugin.getGuiLocations().remove(player.getUniqueId());
    }

    private String getColorForLevel(int level) {
        return switch (level) {
            case 2 -> "<color:#00FF00>";
            case 3 -> "<color:#00FFFF>";
            case 4 -> "<color:#FF00FF>";
            case 5 -> "<color:#FFD700>";
            default -> "<color:#0080FF>";
        };
    }
}
