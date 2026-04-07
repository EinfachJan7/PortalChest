package dev.jankr.portalchest.manager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.Component;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class ConfigManager {

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage;
    private FileConfiguration config;
    
    // ==================== CACHE FÜR SYSTEM-WERTE ====================
    private String configVersion;
    private boolean debugMode;
    private String generalPrefix;
    
    // System
    private int minDistance;
    private int maxConnections;
    private boolean allowNether;
    private boolean allowEnd;
    public boolean enableSendLimit;
    private int maxItemsPerTransfer;
    private int transferInterval;
    private boolean enableUpgrades;
    
    // Display
    private String displayType;
    private double displayHeightOffset;
    private double displayXOffset;
    private double displayZOffset;
    private double displayScale;
    private int displayRotation;
    private boolean particleDisplayEnabled;
    
    // Database
    private String databaseType;
    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        config = plugin.getConfig();

        // Allgemeine Einstellungen
        configVersion = config.getString("config-version", "1.1.0");
        debugMode = config.getBoolean("general.debug", false);
        generalPrefix = config.getString("general.prefix", "");

        // System-Werte laden
        minDistance = config.getInt("system.min-distance", 5);
        maxConnections = config.getInt("system.max-connections", 5);
        allowNether = config.getBoolean("system.allow-nether", true);
        allowEnd = config.getBoolean("system.allow-end", true);
        enableSendLimit = config.getBoolean("system.enable-send-limit", true);
        maxItemsPerTransfer = config.getInt("system.max-items-per-transfer", 4);
        transferInterval = config.getInt("system.transfer-interval", 40);
        enableUpgrades = config.getBoolean("system.enable-upgrades", true);

        // Display-Werte laden
        displayType = config.getString("display.type", "BLOCK_DISPLAY");
        
        // Block Display Settings (falls BLOCK_DISPLAY)
        displayHeightOffset = config.getDouble("display.block-display.height-offset", 1.2);
        displayXOffset = config.getDouble("display.block-display.x-offset", 0.35);
        displayZOffset = config.getDouble("display.block-display.z-offset", 0.35);
        displayScale = config.getDouble("display.block-display.scale", 0.3);
        displayRotation = config.getInt("display.block-display.rotation", 0);

        // Database
        databaseType = config.getString("database.type", "YAML");

        if (debugMode) {
            plugin.getLogger().info("PortalChest Config loaded (v" + configVersion + ")");
        }
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        loadConfig();
    }

    // ==================== UPGRADE-SYSTEM ====================
    public int getUpgradeCost(int level) {
        return config.getInt("upgrades.level-" + level + ".cost", 0);
    }

    public int getMaxItemsForLevel(int level) {
        if (level == 1) {
            return maxItemsPerTransfer;
        }
        return config.getInt("upgrades.level-" + level + ".items", maxItemsPerTransfer);
    }
    
    /**
     * Gibt die maximale Anzahl von Levels (DYNAMISCH) zurück
     */
    public int getMaxLevels() {
        return config.getInt("upgrades.max-levels", 6);
    }
    
    // Upgrade-Währung
    public String getUpgradeCurrency() {
        return config.getString("upgrades.currency", "DIAMOND");
    }
    
    /**
     * Prüft ob Vault Economy verwendet wird
     */
    public boolean isVaultEconomyUsed() {
        return getUpgradeCurrency().equalsIgnoreCase("VAULT_ECONOMY");
    }
    
    /**
     * Gibt den angezeigten Namen der Währung zurück (z.B. "Diamanten" oder "Money")
     */
    public String getCurrencyDisplayName() {
        if (isVaultEconomyUsed()) {
            return dev.jankr.portalchest.util.EconomyUtil.getCurrencyName();
        }
        
        String currency = getUpgradeCurrency();
        return switch (currency) {
            case "DIAMOND" -> "Diamanten";
            case "EMERALD" -> "Smaragde";
            case "NETHER_STAR" -> "Nether-Sterne";
            case "AMETHYST_SHARD" -> "Amethyst-Splitter";
            default -> currency.replace("_", " ").toLowerCase();
        };
    }
    
    public int getDoubleChestUpgradeCost() {
        return config.getInt("upgrades.double-chest.cost", 10);
    }
    
    public String getDoubleChestUpgradeCurrency() {
        return config.getString("upgrades.double-chest.currency", getUpgradeCurrency());
    }
    
    public boolean isDoubleChestUpgradeEnabled() {
        return config.getBoolean("upgrades.double-chest.enabled", true);
    }
    
    /**
     * Gibt das minimale Level zurück zum freischalten des Double-Chest-Upgrades
     */
    public int getDoubleChestRequiredLevel() {
        return config.getInt("upgrades.double-chest.required-level", 1);
    }
    
    public org.bukkit.Material getUpgradeCurrencyMaterial() {
        if (isVaultEconomyUsed()) {
            return null; // Vault ist nicht material-basiert
        }
        
        String materialName = getUpgradeCurrency();
        try {
            return org.bukkit.Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            return org.bukkit.Material.DIAMOND;
        }
    }
    
    public org.bukkit.Material getDoubleChestCurrencyMaterial() {
        String currency = getDoubleChestUpgradeCurrency();
        if (currency.equalsIgnoreCase("VAULT_ECONOMY")) {
            return null;
        }
        
        try {
            return org.bukkit.Material.valueOf(currency);
        } catch (IllegalArgumentException e) {
            return org.bukkit.Material.DIAMOND;
        }
    }

    // ==================== FARBEN ====================
    public String getColorForLevel(int level) {
        return switch (level) {
            case 2 -> "<green>";
            case 3 -> "<aqua>";
            case 4 -> "<light_purple>";
            case 5 -> "<gold>";
            default -> "<blue>";
        };
    }

    public String getTransferSeconds() {
        double seconds = transferInterval / 20.0;
        if (seconds == 1) {
            return "1 Sekunde";
        }
        if (seconds == Math.floor(seconds)) {
            return ((int) seconds) + " Sekunden";
        }
        return String.format("%.1f Sekunden", seconds);
    }

    public boolean isNetherAllowed() {
        return allowNether;
    }

    public boolean isEndAllowed() {
        return allowEnd;
    }
    
    // ==================== NACHRICHTEN ====================
    public String getMessage(String path) {
        return config.getString("messages." + path, "");
    }
    
    public String getMessageWithPlaceholders(String path, java.util.Map<String, String> replacements) {
        String msg = getMessage(path);
        for (String key : replacements.keySet()) {
            msg = msg.replace("%" + key + "%", replacements.get(key));
        }
        return msg;
    }
    
    // ==================== ITEMS ====================
    public String getPortalLinkerName() {
        return config.getString("items.portal-linker.name", "<light_purple><bold><italic:false>Portal Linker");
    }
    
    public java.util.List<String> getPortalLinkerLore() {
        java.util.List<String> lore = config.getStringList("items.portal-linker.lore");
        return lore != null ? lore : new java.util.ArrayList<>();
    }
    
    // ==================== CHESTS ====================
    public String getChestName(int level) {
        String key = "chests.level-" + level + ".name";
        String name = config.getString(key);
        if (name == null) {
            name = config.getString("chests.level-1.name", "<blue><bold><italic:false>Portal Chest");
        }
        return name;
    }
    
    public java.util.List<String> getChestLore(int level) {
        String key = "chests.level-" + level + ".lore";
        java.util.List<String> lore = config.getStringList(key);
        if (lore == null || lore.isEmpty()) {
            lore = config.getStringList("chests.level-1.lore");
        }
        return lore != null ? lore : new java.util.ArrayList<>();
    }
    
    // ==================== LINKED CHESTS ====================
    public String getSenderChestName(int level) {
        String name = config.getString("linked-chests.sender.name", "<aqua><bold><italic:false>SENDER [LVL %level%]");
        return name.replace("%level%", String.valueOf(level));
    }
    
    public String getReceiverChestName() {
        return config.getString("linked-chests.receiver.name", "<gold><bold><italic:false>RECEIVER");
    }
    
    // ==================== GUI ====================
    public String getGuiTitle(int level) {
        String title = config.getString("gui.upgrade.title", "Portal Chest Upgrade");
        String color = getColorForLevel(level);
        return title.replace("%color%", color).replace("%level%", String.valueOf(level));
    }
    
    public String getGuiReceiverTitle() {
        return config.getString("gui.upgrade.receiver-title", "Receiver Upgrades");
    }
    
    // GUI Slot-Positionen
    public int getGuiCurrentLevelSlot() {
        return config.getInt("gui.upgrade.current-level-slot", 11);
    }
    
    public int getGuiArrowSlot() {
        return config.getInt("gui.upgrade.arrow-slot", 13);
    }
    
    public int getGuiNextLevelSlot() {
        return config.getInt("gui.upgrade.next-level-slot", 15);
    }
    
    public int getGuiDoubleChestSlot() {
        return config.getInt("gui.upgrade.double-chest-slot", 13);
    }
    
    // GUI Materials
    public String getGuiMaterial(String type) {
        return config.getString("gui.upgrade.materials." + type, "DIAMOND_BLOCK");
    }
    
    // GUI Item Namen
    public String getGuiCurrentLevelName(int level) {
        String name = config.getString("gui.upgrade.current-level-name", "✦ Aktuelles Level: %level% ✦");
        String color = getColorForLevel(level);
        int items = getMaxItemsForLevel(level);
        String time = getTransferSeconds();
        return name.replace("%level%", String.valueOf(level))
                   .replace("%color%", color)
                   .replace("%items%", String.valueOf(items))
                   .replace("%time%", time);
    }
    
    public java.util.List<String> getGuiCurrentLevelLore(int level) {
        java.util.List<String> lore = config.getStringList("gui.upgrade.current-level-lore");
        int items = getMaxItemsForLevel(level);
        String time = getTransferSeconds();
        java.util.List<String> result = new java.util.ArrayList<>(lore);
        result.replaceAll(s -> s.replace("%items%", String.valueOf(items))
                                .replace("%time%", time)
                                .replace("%level%", String.valueOf(level)));
        return result;
    }
    
    public String getGuiMaxLevelName(int level) {
        String name = config.getString("gui.upgrade.max-level-name", "✦ Maximales Level: %level% ✦");
        String color = getColorForLevel(level);
        int items = getMaxItemsForLevel(level);
        String time = getTransferSeconds();
        return name.replace("%level%", String.valueOf(level))
                   .replace("%color%", color)
                   .replace("%items%", String.valueOf(items))
                   .replace("%time%", time);
    }
    
    public java.util.List<String> getGuiMaxLevelLore(int level) {
        java.util.List<String> lore = config.getStringList("gui.upgrade.max-level-lore");
        int items = getMaxItemsForLevel(level);
        String time = getTransferSeconds();
        java.util.List<String> result = new java.util.ArrayList<>(lore);
        result.replaceAll(s -> s.replace("%items%", String.valueOf(items))
                                .replace("%time%", time)
                                .replace("%level%", String.valueOf(level)));
        return result;
    }
    
    public String getGuiNextLevelName(int level) {
        String name = config.getString("gui.upgrade.next-level-name", "➜ Nächstes Level: %level%");
        String color = getColorForLevel(level);
        int items = getMaxItemsForLevel(level);
        String time = getTransferSeconds();
        int cost = getUpgradeCost(level);
        return name.replace("%level%", String.valueOf(level))
                   .replace("%color%", color)
                   .replace("%items%", String.valueOf(items))
                   .replace("%time%", time)
                   .replace("%cost%", String.valueOf(cost));
    }
    
    public java.util.List<String> getGuiNextLevelLore(int level) {
        java.util.List<String> lore = config.getStringList("gui.upgrade.next-level-lore");
        int items = getMaxItemsForLevel(level);
        String time = getTransferSeconds();
        int cost = getUpgradeCost(level);
        java.util.List<String> result = new java.util.ArrayList<>(lore);
        result.replaceAll(s -> s.replace("%items%", String.valueOf(items))
                                .replace("%time%", time)
                                .replace("%level%", String.valueOf(level))
                                .replace("%cost%", String.valueOf(cost)));
        return result;
    }
    
    public String getGuiArrowName() {
        return config.getString("gui.upgrade.arrow-name", "<gold><bold>→ Upgrade →");
    }
    
    public java.util.List<String> getGuiArrowLore() {
        return config.getStringList("gui.upgrade.arrow-lore");
    }
    
    public String getGuiDoubleChestNameOn() {
        return config.getString("gui.upgrade.double-chest-name-on", "<green><bold>✓ Doppelkisten-Upgrade: ON");
    }
    
    public java.util.List<String> getGuiDoubleChestLoreOn() {
        return config.getStringList("gui.upgrade.double-chest-lore-on");
    }
    
    public String getGuiDoubleChestNameOff() {
        return config.getString("gui.upgrade.double-chest-name-off", "<gold><bold>Doppelkisten-Upgrade: OFF");
    }
    
    public java.util.List<String> getGuiDoubleChestLoreOff() {
        java.util.List<String> lore = config.getStringList("gui.upgrade.double-chest-lore-off");
        java.util.List<String> result = new java.util.ArrayList<>(lore);
        result.replaceAll(s -> s.replace("%cost%", String.valueOf(getDoubleChestUpgradeCost()))
                                .replace("%currency%", getCurrencyDisplayName()));
        return result;
    }
    
    // ==================== DISPLAY ====================
    public String getDisplayType() {
        return displayType;
    }

    // ==================== BLOCK DISPLAY ====================
    public org.bukkit.Material getBlockDisplayMaterialForLevel(int level) {
        return getBlockDisplayMaterialForLevel(level, true); // Standard: SENDER
    }

    public org.bukkit.Material getBlockDisplayMaterialForLevel(int level, boolean isSender) {
        String path = isSender ? "display.block-display.materials.sender.level-" + level : "display.block-display.materials.receiver.level-" + level;
        String materialName = config.getString(path, "RED_CONCRETE");
        try {
            return org.bukkit.Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            return org.bukkit.Material.RED_CONCRETE;
        }
    }

    // Für Kompatibilität (alte Methode)
    public org.bukkit.Material getDisplayMaterialForLevel(int level, boolean isSender) {
        return getBlockDisplayMaterialForLevel(level, isSender);
    }

    // ==================== PARTICLES - DISPLAY (Partikel über der Chest) ====================
    public String getParticleDisplayTypeForLevel(int level, boolean isSender) {
        String path = isSender ? "display.particles.display.materials.sender.level-" + level : "display.particles.display.materials.receiver.level-" + level;
        return config.getString(path, "glow");
    }

    public double getParticleDisplayHeightOffset() {
        return config.getDouble("display.particles.display.height-offset", 1.2);
    }

    public double getParticleDisplayXOffset() {
        return config.getDouble("display.particles.display.x-offset", 0.35);
    }

    public double getParticleDisplayZOffset() {
        return config.getDouble("display.particles.display.z-offset", 0.35);
    }

    public int getParticleDisplayCount() {
        return config.getInt("display.particles.display.particle-count", 3);
    }

    public double getParticleDisplaySpread() {
        return config.getDouble("display.particles.display.particle-spread", 0.1);
    }

    // ==================== PARTICLES - CONNECTION (Verbindungslinie zwischen Chests) ====================
    public boolean isConnectionParticlesEnabled() {
        return config.getBoolean("display.particles.connection.enabled", true);
    }

    public double getConnectionParticleDensity() {
        return config.getDouble("display.particles.connection.particle-density", 2);
    }

    public double getConnectionParticleHeightOffset() {
        return config.getDouble("display.particles.connection.height-offset", 0.5);
    }

    public double getConnectionParticleXOffset() {
        return config.getDouble("display.particles.connection.x-offset", 0.0);
    }

    public double getConnectionParticleZOffset() {
        return config.getDouble("display.particles.connection.z-offset", 0.0);
    }

    public String getConnectionParticleType() {
        return config.getString("display.particles.connection.particle-type", "dust");
    }

    public int getConnectionParticleCount() {
        return config.getInt("display.particles.connection.particle-count", 2);
    }

    public double getConnectionParticleSpread() {
        return config.getDouble("display.particles.connection.particle-spread", 0.0);
    }

    // Alte Methoden (für Kompatibilität, verwenden jetzt die neuen Pfade)
    public double getParticlesHeightOffset() {
        return getParticleDisplayHeightOffset();
    }

    public double getParticlesXOffset() {
        return getParticleDisplayXOffset();
    }

    public double getParticlesZOffset() {
        return getParticleDisplayZOffset();
    }

    public int getParticlesCount() {
        return getParticleDisplayCount();
    }

    public double getParticlesSpread() {
        return getParticleDisplaySpread();
    }

    public double getParticleDensityForParticles() {
        return getConnectionParticleDensity();
    }

    public double getDisplayScale() {
        return displayScale;
    }

    public int getDisplayRotation() {
        return displayRotation;
    }

    public double getDisplayHeightOffset() {
        return displayHeightOffset;
    }

    public double getDisplayXOffset() {
        return displayXOffset;
    }

    public double getDisplayZOffset() {
        return displayZOffset;
    }

    // ==================== DATABASE ====================
    public String getDatabaseType() {
        return databaseType;
    }

    public String getDatabaseYamlFile() {
        return config.getString("database.yaml.file", "chests.yml");
    }

    public String getMySQLHost() {
        return config.getString("database.mysql.host", "localhost");
    }

    public int getMySQLPort() {
        return config.getInt("database.mysql.port", 3306);
    }

    public String getMySQLDatabase() {
        return config.getString("database.mysql.database", "minecraft");
    }

    public String getMySQLUsername() {
        return config.getString("database.mysql.username", "root");
    }

    public String getMySQLPassword() {
        return config.getString("database.mysql.password", "");
    }

    public String getMySQLTablePrefix() {
        return config.getString("database.mysql.table-prefix", "portalchest_");
    }

    public int getMySQLPoolSize() {
        return config.getInt("database.mysql.connection-pool-size", 5);
    }

    public int getMySQLTimeout() {
        return config.getInt("database.mysql.connection-timeout", 30000);
    }

    // ==================== TELEPORT SOUND ====================
    public boolean isTeleportSoundEnabled() {
        return config.getBoolean("teleport-sound.enabled", true);
    }

    public String getTeleportSoundType() {
        return config.getString("teleport-sound.sound-type", "ambient.soul_sand_valley.mood");
    }

    public float getTeleportSoundVolume() {
        return (float) config.getDouble("teleport-sound.volume", 0.6);
    }

    public float getTeleportSoundPitch() {
        return (float) config.getDouble("teleport-sound.pitch", 1.0);
    }

    // ==================== HELPER METHODEN ====================
    public String getMessagePrefix() {
        return config.getString("messages.prefix", "<dark_gray>[<aqua>PortalChest<dark_gray>]");
    }
}
