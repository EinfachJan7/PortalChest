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
        // Manager initialisieren (REIHENFOLGE IST WICHTIG!)
        this.configManager = new ConfigManager(this);
        this.configManager.loadConfig();
        
        // DatabaseManager MUSS VOR DataManager kommen
        this.databaseManager = new DatabaseManager(this);
        
        // DataManager DANACH initialisieren
        this.dataManager = new DataManager(this);
        
        // Andere Manager
        this.displayManager = new DisplayManager(this, this.configManager);
        this.particleManager = new ParticleManager(this);
        this.soundManager = new SoundManager(this);
        this.upgradeGUI = new UpgradeGUI(this);

        // Economy-Support initialisieren
        EconomyUtil.setupEconomy(this);

        // Daten laden (wird automatisch YAML oder MySQL laden basierend auf Config)
        getLogger().info("========================================");
        getLogger().info("Lade Chests aus " + configManager.getDatabaseType() + "...");
        long startTime = System.currentTimeMillis();
        
        dataManager.loadData();
        
        long loadTime = System.currentTimeMillis() - startTime;
        getLogger().info("✓ " + dataManager.getAllChests().size() + " Chests geladen (" + loadTime + "ms)");
        getLogger().info("========================================");
        
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
        getLogger().info("========================================");
        getLogger().info("Starte komplette Config/Datenbank-Reload...");
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. Speichere Daten vorher
            if (dataManager != null) {
                dataManager.saveData();
            }
            
            // 2. Cleaere ALLE Displays
            if (displayManager != null) {
                displayManager.clearAllDisplays();
                getLogger().info("✓ Alle Displays gelöscht");
            }
            
            // 3. Schließe alte Datenbankverbindung
            if (databaseManager != null) {
                databaseManager.shutdown();
            }
            
            // 4. Reload ConfigManager
            configManager.reloadConfig();
            getLogger().info("✓ Config neu geladen");
            
            // 5. Reinitialize DatabaseManager mit neuer Config
            this.databaseManager = new DatabaseManager(this);
            getLogger().info("✓ DatabaseManager neu initialisiert");
            
            // 6. Update Manager
            displayManager = new DisplayManager(this, configManager);
            particleManager = new ParticleManager(this);
            soundManager = new SoundManager(this);
            getLogger().info("✓ Alle Manager neu initialisiert");
            
            // 7. Reload Daten aus Datenbank
            if ("MYSQL".equalsIgnoreCase(configManager.getDatabaseType())) {
                // MySQL: Starte neu die Datenbankverbindung
                databaseManager.loadChestData();
                getLogger().info("✓ MySQL-Daten neu geladen");
            } else {
                // YAML: Lade Datei neu
                dataManager.reloadData();
                getLogger().info("✓ YAML-Daten neu geladen");
            }
            
            // 8. Restauriere ALLE Displays
            dataManager.restoreAllDisplays();
            getLogger().info("✓ Alle Displays wiederhergestellt");
            
            long reloadTime = System.currentTimeMillis() - startTime;
            getLogger().info("========================================");
            getLogger().info("✓ CONFIG/DATENBANK-RELOAD ABGESCHLOSSEN!");
            getLogger().info("Reload-Zeit: " + reloadTime + "ms");
            getLogger().info("Chests geladen: " + dataManager.getAllChests().size());
            getLogger().info("========================================");
            
        } catch (Exception e) {
            getLogger().severe("❌ FEHLER beim Reload der Config!");
            e.printStackTrace();
        }
    }
}
