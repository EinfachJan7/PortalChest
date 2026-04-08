package dev.jankr.portalchest.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Material;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

public class DisplayManager {

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage;
    private final Map<String, UUID> displayEntities = new HashMap<>();
    private final ConfigManager configManager;

    public DisplayManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.configManager = configManager;
    }

    /**
     * Wandelt MiniMessage-String zu Adventure Component um
     * Format: <color:#FF0000>Text</color>, <bold>Text</bold>, etc.
     */
    public Component parseMessage(String message) {
        if (message == null || message.isEmpty()) {
            return Component.text("");
        }
        
        try {
            return miniMessage.deserialize(message);
        } catch (Exception e) {
            plugin.getLogger().warning("Fehler beim Parsen von MiniMessage: " + message);
            plugin.getLogger().warning("Error: " + e.getMessage());
            return Component.text(message);
        }
    }

    /**
     * Spawnt ein Display basierend auf Config-Einstellung (BLOCK_DISPLAY oder PARTICLES)
     */
    public BlockDisplay spawnDisplay(Location chest, int upgradeLevel, String type) {
        String displayType = configManager.getDisplayType();
        // Nur spawne BLOCK_DISPLAY wenn Display-Typ auf BLOCK_DISPLAY gesetzt ist
        if ("BLOCK_DISPLAY".equalsIgnoreCase(displayType)) {
            return spawnBlockDisplay(chest, upgradeLevel, type);
        }
        // Ansonsten: Particle-System wird verwendet (kein BlockDisplay)
        plugin.getLogger().info("[PortalChest] Particle-System für Chest aktiviert (nicht BLOCK_DISPLAY)");
        return null;
    }

    /**
     * Spawnt ein Block Display über einer Kiste
     */
    public BlockDisplay spawnBlockDisplay(Location chest, int upgradeLevel, String type) {
        double xOffset = configManager.getDisplayXOffset();
        double yOffset = configManager.getDisplayHeightOffset();
        double zOffset = configManager.getDisplayZOffset();
        double scale = configManager.getDisplayScale();
        
        Location displayLoc = chest.clone().add(xOffset, yOffset, zOffset);

        BlockDisplay display = (BlockDisplay) chest.getWorld().spawnEntity(displayLoc, EntityType.BLOCK_DISPLAY);

        // Material basierend auf Level und Typ (SENDER oder RECEIVER)
        boolean isSender = type.equalsIgnoreCase("SENDER");
        org.bukkit.Material material = configManager.getBlockDisplayMaterialForLevel(upgradeLevel, isSender);
        display.setBlock(material.createBlockData());

        // Größe setzen mit Scale
        float scaleValue = (float) scale;
        display.setTransformation(new Transformation(
            new Vector3f(0, 0, 0),           // Translation
            new AxisAngle4f(0, 0, 0, 1),    // Rotation
            new Vector3f(scaleValue, scaleValue, scaleValue), // Scale
            new AxisAngle4f(0, 0, 0, 1)     // Right rotation
        ));

        // Entferne Glow-Effekt  
        display.setGlowing(false);

        // Tags für Identifikation
        display.getPersistentDataContainer().set(
            new org.bukkit.NamespacedKey(plugin, "portal_chest_display"),
            org.bukkit.persistence.PersistentDataType.STRING,
            type + "_" + upgradeLevel
        );

        return display;
    }

    /**
     * Spawnt Partikel über einer Chest als Display - IMMER AN ORT UND STELLE
     */
    public void spawnParticleDisplay(Location chest, int upgradeLevel, String type) {
        double xOffset = configManager.getParticleDisplayXOffset();
        double yOffset = configManager.getParticleDisplayHeightOffset();
        double zOffset = configManager.getParticleDisplayZOffset();
        
        Location displayLoc = chest.clone().add(xOffset, yOffset, zOffset);

        try {
            boolean isSender = type.equalsIgnoreCase("SENDER");
            String particleType = configManager.getParticleDisplayTypeForLevel(upgradeLevel, isSender);
            org.bukkit.Particle particle = org.bukkit.Particle.valueOf(particleType.toUpperCase());
            
            // WICHTIG: Spawn immer mit Count=1 und Spread=0 für EXAKTE Positionen!
            displayLoc.getWorld().spawnParticle(particle, displayLoc, 1, 
                0.0, 0.0, 0.0);
        } catch (IllegalArgumentException e) {
            // Fallback auf GLOW wenn Partikeltyp nicht existiert
            displayLoc.getWorld().spawnParticle(org.bukkit.Particle.GLOW, displayLoc, 1, 
                0.0, 0.0, 0.0);
        }
    }

    /**
     * Entfernt ein Block Display
     */
    public void removeBlockDisplay(String locationKey) {
        UUID displayId = displayEntities.get(locationKey);
        if (displayId != null) {
            org.bukkit.entity.Entity entity = plugin.getServer().getEntity(displayId);
            if (entity != null) {
                try {
                    // Entferne die Entity vom Server
                    if (!entity.isDead()) {
                        entity.remove();
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("[PortalChest] Fehler beim Entfernen von Display: " + e.getMessage());
                }
            }
            // Entferne aus Cache unabhängig davon ob Entity existiert
            displayEntities.remove(locationKey);
            plugin.getLogger().info("[PortalChest] Display entfernt für Location: " + locationKey);
        }
    }

    /**
     * Registriert ein Block Display zur Verwaltung
     */
    public void registerDisplay(String locationKey, UUID displayId) {
        displayEntities.put(locationKey, displayId);
    }

    public void clearAllDisplays() {
        for (UUID displayId : new HashSet<>(displayEntities.values())) {
            org.bukkit.entity.Entity entity = plugin.getServer().getEntity(displayId);
            if (entity != null) {
                entity.remove();
            }
        }
        displayEntities.clear();
    }

    /**
     * Updated alle bestehenden Displays mit neuen Einstellungen
     * (wird beim Reload aufgerufen)
     */
    public int updateAllDisplays(JavaPlugin javaPlugin) {
        int updated = 0;
        
        // Alle Displays entfernen
        for (UUID displayId : new HashSet<>(displayEntities.values())) {
            org.bukkit.entity.Entity entity = plugin.getServer().getEntity(displayId);
            if (entity != null) {
                entity.remove();
            }
        }
        displayEntities.clear();
        
        // Alle Chests neu laden
        if (javaPlugin instanceof dev.jankr.portalchest.PortalChestPlugin pluginInstance) {
            for (dev.jankr.portalchest.model.PortalChest chest : pluginInstance.getDataManager().getAllChests()) {
                try {
                    String locationKey = pluginInstance.getDataManager().getLocationKey(chest.getLocation());
                    
                    // Display basierend auf Chest-Typ spawnen
                    String type = chest.getType() == dev.jankr.portalchest.model.PortalChest.ChestType.SENDER ? "sender" : "receiver";
                    BlockDisplay display = spawnDisplay(chest.getLocation(), chest.getUpgradeLevel(), type);
                    
                    if (display != null) {
                        registerDisplay(locationKey, display.getUniqueId());
                        updated++;
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Fehler beim Update von Display: " + e.getMessage());
                }
            }
        }
        
        return updated;
    }
}
