package dev.jankr.portalchest;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import dev.jankr.portalchest.manager.ConfigManager;
import dev.jankr.portalchest.manager.DataManager;
import dev.jankr.portalchest.manager.DisplayManager;
import dev.jankr.portalchest.manager.DatabaseManager;
import dev.jankr.portalchest.manager.ParticleManager;
import dev.jankr.portalchest.manager.SoundManager;
import dev.jankr.portalchest.gui.UpgradeGUI;
import dev.jankr.portalchest.commands.PortalChestCommand;
import dev.jankr.portalchest.listeners.*;
import dev.jankr.portalchest.model.PortalChest;
import dev.jankr.portalchest.util.EconomyUtil;

import java.util.*;

public class PortalChestPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private DataManager dataManager;
    private DatabaseManager databaseManager;
    private DisplayManager displayManager;
    private ParticleManager particleManager;
    private SoundManager soundManager;
    private UpgradeGUI upgradeGUI;
    private ChestPlaceListener chestPlaceListener;
    private InventoryListener inventoryListener;
    private Map<UUID, String> guiLocations = new HashMap<>();

    @Override
    public void onEnable() {
        // Manager initialisieren
        this.configManager = new ConfigManager(this);
        this.configManager.loadConfig();
        
        this.dataManager = new DataManager(this);
        this.databaseManager = new DatabaseManager(this);
        this.displayManager = new DisplayManager(this, this.configManager);
        this.particleManager = new ParticleManager(this);
        this.soundManager = new SoundManager(this);
        this.upgradeGUI = new UpgradeGUI(this);

        // Economy-Support initialisieren (falls Vault Economy verwendet wird)
        EconomyUtil.setupEconomy(this);

        // Daten laden
        dataManager.loadData();
        
        // Displays für gespeicherte Chests wiederherstellen
        dataManager.restoreAllDisplays();

        // Befehle registrieren
        PortalChestCommand cmd = new PortalChestCommand(this);
        getCommand("portalchest").setExecutor(cmd);
        getCommand("portalchest").setTabCompleter(cmd);

        // Rezepte registrieren
        registerRecipes();

        // Listener registrieren
        registerListeners();

        // Task starten für Item-Transfer
        startTransferTask();

        getLogger().info("PortalChest Plugin v" + getDescription().getVersion() + " aktiviert!");
    }

    @Override
    public void onDisable() {
        if (dataManager != null) {
            dataManager.saveData();
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        getLogger().info("PortalChest Plugin deaktiviert!");
    }

    private void registerListeners() {
        this.chestPlaceListener = new ChestPlaceListener(this);
        this.inventoryListener = new InventoryListener(this);
        getServer().getPluginManager().registerEvents(chestPlaceListener, this);
        getServer().getPluginManager().registerEvents(new ChestBreakListener(this), this);
        getServer().getPluginManager().registerEvents(new ChestInteractListener(this), this);
        getServer().getPluginManager().registerEvents(inventoryListener, this);
        getServer().getPluginManager().registerEvents(new PlayerDisconnectListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
    }

    public ChestPlaceListener getChestPlaceListener() {
        return chestPlaceListener;
    }

    public InventoryListener getInventoryListener() {
        return inventoryListener;
    }

    private void startTransferTask() {
        // Transfer-Task: Jedes Tick
        getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            dataManager.transferItems();
        }, 0L, 1L);

        // Partikel-Display Task: Alle 20 Ticks (1 Sekunde) - nur wenn type: PARTICLES
        getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if ("PARTICLES".equalsIgnoreCase(configManager.getDisplayType())) {
                // Spawne Partikel über allen Chests
                for (PortalChest chest : dataManager.getAllChests()) {
                    String type = chest.getType() == PortalChest.ChestType.SENDER ? "SENDER" : "RECEIVER";
                    displayManager.spawnParticleDisplay(chest.getLocation(), chest.getUpgradeLevel(), type);
                }
            }
        }, 20L, 20L);  // Alle 20 Ticks (1 Sekunde)

        // Connection-Partikel-Verbindungen Task: Alle 100 Ticks (5 Sekunden)
        getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            // Zeichne Partikelverbindungen für alle verknüpften Chests
            for (PortalChest chest : dataManager.getAllChests()) {
                if (chest.getType() == PortalChest.ChestType.SENDER && chest.getLinkedChest() != null) {
                    particleManager.drawConnectionLine(chest, chest.getLinkedChest());
                }
            }
        }, 100L, 100L);  // Alle 100 Ticks (5 Sekunden)
    }

    private void registerRecipes() {
        // Portal Linker wird nicht mehr benötigt - Linking erfolgt durch Platzieren von Chests
        getLogger().info("Rezepte deaktiviert - Linking erfolgt direkt beim Platzieren!");
    }

    // Getter
    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public DisplayManager getDisplayManager() {
        return displayManager;
    }

    public ParticleManager getParticleManager() {
        return particleManager;
    }

    public SoundManager getSoundManager() {
        return soundManager;
    }

    public UpgradeGUI getUpgradeGUI() {
        return upgradeGUI;
    }

    public Map<UUID, String> getGuiLocations() {
        return guiLocations;
    }

    /**
     * Laden alle Manager neu (für /reload Befehl)
     */
    public void reloadPluginConfig() {
        getLogger().info("Lade komplette Config neu...");
        
        try {
            // Speichere Daten vorher
            if (dataManager != null) {
                dataManager.saveData();
            }
            
            // Schließe alte Datenbankverbindung
            if (databaseManager != null) {
                databaseManager.shutdown();
            }
            
            // Reload ConfigManager
            configManager.reloadConfig();
            
            // Reinitialize DatabaseManager mit neuer Config
            this.databaseManager = new DatabaseManager(this);
            
            // Update DisplayManager falls Display-Settings sich geändert haben
            displayManager = new DisplayManager(this, configManager);
            
            // Update ParticleManager falls Particle-Settings sich geändert haben
            particleManager = new ParticleManager(this);
            
            // Update SoundManager falls Sound-Settings sich geändert haben
            soundManager = new SoundManager(this);
            
            // Reload Daten
            dataManager.loadData();
            
            // Restauriere Displays
            dataManager.restoreAllDisplays();
            
            getLogger().info("✓ Config erfolgreich neu geladen!");
            
        } catch (Exception e) {
            getLogger().severe("Fehler beim Reload der Config!");
            e.printStackTrace();
        }
    }
}
