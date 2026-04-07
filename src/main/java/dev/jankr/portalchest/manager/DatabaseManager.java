package dev.jankr.portalchest.manager;

import dev.jankr.portalchest.PortalChestPlugin;
import dev.jankr.portalchest.model.PortalChest;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Verwaltet die Speicherung von PortalChests in YAML oder MySQL
 */
public class DatabaseManager {
    
    private final PortalChestPlugin plugin;
    private final ConfigManager configManager;
    private final File dataFolder;
    private final File chestsFile;
    private YamlConfiguration chestData;
    private MySQLDatabaseManager mysqlManager;
    private final String databaseType;

    public DatabaseManager(PortalChestPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.databaseType = configManager.getDatabaseType();
        this.dataFolder = plugin.getDataFolder();
        
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        this.chestsFile = new File(dataFolder, configManager.getDatabaseYamlFile());
        
        if ("MYSQL".equalsIgnoreCase(databaseType)) {
            plugin.getLogger().info("Verwende MySQL Datenbank...");
            try {
                this.mysqlManager = new MySQLDatabaseManager(plugin);
            } catch (Exception e) {
                plugin.getLogger().severe("MySQL konnte nicht initialisiert werden! Wechsle zu YAML!");
                e.printStackTrace();
                loadChestData();
            }
        } else {
            plugin.getLogger().info("Verwende YAML Datenbank...");
            loadChestData();
        }
    }

    /**
     * Lädt alle Chests aus der Speicherung
     */
    public void loadChestData() {
        if ("MYSQL".equalsIgnoreCase(databaseType)) {
            return; // MySQL lädt automatisch
        }
        
        if (!chestsFile.exists()) {
            try {
                chestsFile.createNewFile();
                chestData = new YamlConfiguration();
            } catch (IOException e) {
                plugin.getLogger().warning("Konnte chests.yml nicht erstellen!");
                e.printStackTrace();
                return;
            }
        }
        chestData = YamlConfiguration.loadConfiguration(chestsFile);
    }

    /**
     * Speichert eine Chest in die Datenbank
     */
    public void saveChest(PortalChest chest) {
        if ("MYSQL".equalsIgnoreCase(databaseType) && mysqlManager != null) {
            mysqlManager.saveChest(chest);
            return;
        }
        
        String locationKey = getLocationKey(chest.getLocation());
        
        chestData.set(locationKey + ".owner", chest.getOwner().toString());
        chestData.set(locationKey + ".type", chest.getType().toString());
        chestData.set(locationKey + ".level", chest.getUpgradeLevel());
        chestData.set(locationKey + ".has-double-chest-upgrade", chest.isHasDoubleChestUpgrade());
        
        if (chest.getLinkedChest() != null) {
            String linkedKey = getLocationKey(chest.getLinkedChest().getLocation());
            chestData.set(locationKey + ".linked-chest", linkedKey);
        }
        
        // Trusted Players speichern
        List<String> trustedList = new ArrayList<>();
        for (UUID uuid : chest.getTrustedPlayers()) {
            trustedList.add(uuid.toString());
        }
        chestData.set(locationKey + ".trusted-players", trustedList);
        
        saveToFile();
        
        if (configManager.isDebugMode()) {
            plugin.getLogger().info("Chest gespeichert: " + locationKey);
        }
    }

    /**
     * Lädt eine Chest aus der Datenbank
     */
    public PortalChest loadChest(Location location) {
        if ("MYSQL".equalsIgnoreCase(databaseType) && mysqlManager != null) {
            return mysqlManager.loadChest(location);
        }
        
        String locationKey = getLocationKey(location);
        
        if (!chestData.contains(locationKey)) {
            return null;
        }
        
        try {
            UUID owner = UUID.fromString(chestData.getString(locationKey + ".owner"));
            PortalChest.ChestType type = PortalChest.ChestType.valueOf(chestData.getString(locationKey + ".type", "SENDER"));
            
            PortalChest chest = new PortalChest(location, owner, type);
            chest.setUpgradeLevel(chestData.getInt(locationKey + ".level", 1));
            chest.setHasDoubleChestUpgrade(chestData.getBoolean(locationKey + ".has-double-chest-upgrade", false));
            
            // Trusted Players laden
            List<String> trustedList = chestData.getStringList(locationKey + ".trusted-players");
            for (String uuidStr : trustedList) {
                try {
                    chest.addTrusted(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Ungültige UUID in trusted-players: " + uuidStr);
                }
            }
            
            return chest;
        } catch (Exception e) {
            plugin.getLogger().warning("Fehler beim Laden der Chest: " + locationKey);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Löscht eine Chest aus der Datenbank
     */
    public void deleteChest(PortalChest chest) {
        if ("MYSQL".equalsIgnoreCase(databaseType) && mysqlManager != null) {
            mysqlManager.deleteChest(chest);
            return;
        }
        
        String locationKey = getLocationKey(chest.getLocation());
        chestData.set(locationKey, null);
        saveToFile();
        
        if (configManager.isDebugMode()) {
            plugin.getLogger().info("Chest gelöscht: " + locationKey);
        }
    }

    /**
     * Speichert alle Daten in die YAML-Datei
     */
    public void saveToFile() {
        try {
            chestData.save(chestsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte chests.yml nicht speichern!");
            e.printStackTrace();
        }
    }

    /**
     * Gibt einen eindeutigen Key für eine Location zurück
     */
    public String getLocationKey(Location loc) {
        return loc.getWorld().getName() + "_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
    }

    /**
     * Laden alle Chests aus der Datenbank
     */
    public List<PortalChest> loadAllChests() {
        if ("MYSQL".equalsIgnoreCase(databaseType) && mysqlManager != null) {
            return mysqlManager.loadAllChests();
        }
        
        List<PortalChest> chests = new ArrayList<>();
        
        if (!chestData.contains("")) {
            return chests;
        }
        
        for (String key : chestData.getKeys(false)) {
            if (chestData.isConfigurationSection(key)) {
                try {
                    // Parse location key
                    String[] parts = key.split("_");
                    if (parts.length < 4) continue;
                    
                    String worldName = parts[0];
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    int z = Integer.parseInt(parts[3]);
                    
                    World world = plugin.getServer().getWorld(worldName);
                    if (world == null) continue;
                    
                    Location loc = new Location(world, x, y, z);
                    PortalChest chest = loadChest(loc);
                    
                    if (chest != null) {
                        chests.add(chest);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Fehler beim Laden der Chest mit key: " + key);
                }
            }
        }
        
        return chests;
    }

    /**
     * Schließe die Datenbankverbindung
     */
    public void shutdown() {
        if ("MYSQL".equalsIgnoreCase(databaseType) && mysqlManager != null) {
            mysqlManager.shutdown();
        } else if (chestData != null) {
            saveToFile();
        }
    }
}
