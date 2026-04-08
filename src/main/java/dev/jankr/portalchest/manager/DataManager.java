package dev.jankr.portalchest.manager;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import com.google.common.collect.Maps;
import dev.jankr.portalchest.model.PortalChest;
import dev.jankr.portalchest.util.DataUtil;
import lombok.Getter;

import java.io.File;
import java.util.*;

@Getter
public class DataManager {

    private final JavaPlugin plugin;
    private final File dataFolder;
    private final File chestsFile;
    private final ConfigManager configManager;
    private final MiniMessage miniMessage;

    private final Map<String, PortalChest> chests = new HashMap<>();
    private final Map<UUID, String> lastClicked = new HashMap<>();

    public DataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin instanceof dev.jankr.portalchest.PortalChestPlugin 
            ? ((dev.jankr.portalchest.PortalChestPlugin) plugin).getConfigManager() 
            : null;
        this.miniMessage = MiniMessage.miniMessage();
        
        this.dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        this.chestsFile = new File(dataFolder, "chests.yml");
    }

    public void loadData() {
        chests.clear();
        
        // Nutze DatabaseManager um Daten zu laden (YAML oder MySQL)
        if (!(plugin instanceof dev.jankr.portalchest.PortalChestPlugin pluginInstance)) {
            plugin.getLogger().warning("❌ FEHLER: Plugin ist nicht PortalChestPlugin!");
            return;
        }
        
        dev.jankr.portalchest.manager.DatabaseManager databaseManager = pluginInstance.getDatabaseManager();
        if (databaseManager == null) {
            plugin.getLogger().warning("❌ FEHLER: DatabaseManager ist null!");
            return;
        }
        
        // Lade alle Chests über DatabaseManager
        List<PortalChest> loadedChests = databaseManager.loadAllChests();
        
        if (loadedChests == null || loadedChests.isEmpty()) {
            plugin.getLogger().info("ℹ Keine gespeicherten Chests gefunden.");
            return;
        }

        plugin.getLogger().info("Lade " + loadedChests.size() + " Chests aus Datenbank...");

        // Schritt 1: Registriere alle Chests
        int loadCount = 0;
        for (PortalChest chest : loadedChests) {
            try {
                String locationKey = getLocationKey(chest.getLocation());
                chests.put(locationKey, chest);
                loadCount++;
                plugin.getLogger().info("✓ Chest geladen: " + locationKey + " (Level " + chest.getUpgradeLevel() + ")");
            } catch (Exception e) {
                plugin.getLogger().warning("❌ Fehler beim Registrieren der Chest: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Schritt 2: Stelle Linked-Beziehungen wieder her (über DatabaseManager)
        int linkedCount = 0;
        for (PortalChest chest : loadedChests) {
            if (chest.getLinkedChest() != null) {
                linkedCount++;
                String key = getLocationKey(chest.getLocation());
                String linkedKey = getLocationKey(chest.getLinkedChest().getLocation());
                plugin.getLogger().info("✓ Link wiederhergestellt: " + key + " ↔ " + linkedKey);
            }
        }

        // Schritt 3: Validiere bidirektionale Linked-Beziehungen
        int brokenCount = 0;
        for (PortalChest chest : chests.values()) {
            if (chest.getLinkedChest() != null) {
                PortalChest linkedChest = chest.getLinkedChest();
                if (linkedChest.getLinkedChest() != chest) {
                    plugin.getLogger().warning("❌ Bidirektionale Link kaputt! Beide Links gelöscht: " + 
                        getLocationKey(chest.getLocation()) + " und " + getLocationKey(linkedChest.getLocation()));
                    chest.setLinkedChest(null);
                    linkedChest.setLinkedChest(null);
                    brokenCount++;
                }
            }
        }

        // Speichere sofort, um saubere Linked-Beziehungen zu haben
        saveData();

        plugin.getLogger().info("========================================");
        plugin.getLogger().info("✓ Chests geladen: " + loadCount + " gesamt");
        plugin.getLogger().info("✓ Links wiederhergestellt: " + linkedCount);
        plugin.getLogger().info("✓ Kaputte Links repariert: " + brokenCount);
        plugin.getLogger().info("========================================");
    }

    /**
     * Lädt Daten während /reload - verbesserte Version mit DatabaseManager
     */
    public void reloadData() {
        plugin.getLogger().info("========================================");
        plugin.getLogger().info("Starte Datei-Reload...");
        long startTime = System.currentTimeMillis();
        
        try {
            // Speichere vorher
            saveData();
            
            // Clear und reload
            lastClicked.clear();
            chests.clear();
            
            // Neu laden über DatabaseManager
            loadData();
            
            long reloadTime = System.currentTimeMillis() - startTime;
            plugin.getLogger().info("✓ Datei-Reload abgeschlossen (" + reloadTime + "ms)");
            plugin.getLogger().info("========================================");
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Fehler beim Reload!");
            e.printStackTrace();
        }
    }

    /**
     * Setzt Custom Names auf Chest-Blöcken für alle geladenen Chests
     * und spawned Displays für verlinkte Chests
     * (wird beim Server-Start aufgerufen)
     */
    public void restoreAllDisplays() {
        if (!(plugin instanceof dev.jankr.portalchest.PortalChestPlugin pluginInstance)) {
            plugin.getLogger().warning("FEHLER: Plugin ist nicht PortalChestPlugin!");
            return;
        }

        DisplayManager displayManager = pluginInstance.getDisplayManager();
        if (displayManager == null) {
            plugin.getLogger().warning("FEHLER: DisplayManager ist null!");
            return;
        }
        
        Set<String> processedLinks = new HashSet<>();
        int displayCount = 0;
        int nameCount = 0;

        plugin.getLogger().info("Beginne restoreAllDisplays() für " + chests.size() + " Chests...");

        for (PortalChest chest : chests.values()) {
            try {
                org.bukkit.block.Block block = chest.getLocation().getBlock();
                if (!block.getType().name().contains("CHEST")) {
                    plugin.getLogger().warning("Block bei " + chest.getLocation() + " ist nicht mehr eine Chest!");
                    continue;
                }

                // Nur für verlinkte Chests: Setze Custom Name und spawne Display
                if (chest.getLinkedChest() != null) {
                    org.bukkit.block.BlockState state = block.getState();
                    if (state instanceof org.bukkit.block.Container container) {
                        if (chest.getType() == PortalChest.ChestType.SENDER) {
                            String senderName = configManager.getSenderChestName(chest.getUpgradeLevel());
                            container.customName(miniMessage.deserialize(senderName));
                        } else {
                            String receiverName = configManager.getReceiverChestName();
                            container.customName(miniMessage.deserialize(receiverName));
                        }
                        state.update();
                        nameCount++;
                    }

                    // Spawne Display (nur einmal pro Link-Paar)
                    String locationKey = getLocationKey(chest.getLocation());
                    String linkedLocationKey = getLocationKey(chest.getLinkedChest().getLocation());
                    String linkId = locationKey.compareTo(linkedLocationKey) < 0
                        ? locationKey + "|" + linkedLocationKey
                        : linkedLocationKey + "|" + locationKey;

                    if (!processedLinks.contains(linkId)) {
                        processedLinks.add(linkId);

                        try {
                            String type = chest.getType() == PortalChest.ChestType.SENDER ? "SENDER" : "RECEIVER";
                            org.bukkit.entity.BlockDisplay display = displayManager.spawnDisplay(
                                chest.getLocation(), 
                                chest.getUpgradeLevel(), 
                                type
                            );

                            if (display != null) {
                                if (chest.getType() == PortalChest.ChestType.SENDER) {
                                    String senderName = configManager.getSenderChestName(chest.getUpgradeLevel());
                                    display.customName(displayManager.parseMessage(senderName));
                                } else {
                                    String receiverName = configManager.getReceiverChestName();
                                    display.customName(displayManager.parseMessage(receiverName));
                                }

                                displayManager.registerDisplay(locationKey, display.getUniqueId());
                                displayCount++;
                                plugin.getLogger().info("✓ Display für Chest bei " + chest.getLocation() + " (Level " + chest.getUpgradeLevel() + ") wiederhergestellt");
                            } else {
                                plugin.getLogger().info("✓ Particle-System für Chest bei " + chest.getLocation() + " aktiviert");
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("❌ Fehler beim Spawnen des Displays für: " + locationKey + " - " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                } else {
                    // Unverlinkte Chests: Entferne Custom Name
                    org.bukkit.block.BlockState state = block.getState();
                    if (state instanceof org.bukkit.block.Container container) {
                        container.setCustomName(null);
                        state.update();
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("❌ Fehler beim Restore für Chest: " + chest.getLocation() + " - " + e.getMessage());
                e.printStackTrace();
            }
        }

        plugin.getLogger().info("✓ restoreAllDisplays() fertig: " + nameCount + " Namen, " + displayCount + " Displays wiederhergestellt!");
    }

    public void saveData() {
        Map<String, Map<String, Object>> data = new HashMap<>();
        
        for (String key : chests.keySet()) {
            PortalChest chest = chests.get(key);
            data.put(key, DataUtil.serializeChest(chest));
        }

        DataUtil.saveYaml(chestsFile, data);
        plugin.getLogger().info(chests.size() + " Chests gespeichert.");
    }

    public void registerChest(PortalChest chest) {
        String key = getLocationKey(chest.getLocation());
        chests.put(key, chest);
        saveData();
    }

    public void unregisterChest(Location location) {
        String key = getLocationKey(location);
        chests.remove(key);
        saveData();
    }

    public PortalChest getChest(Location location) {
        String key = getLocationKey(location);
        return chests.get(key);
    }

    public boolean hasChest(Location location) {
        return chests.containsKey(getLocationKey(location));
    }

    public Collection<PortalChest> getAllChests() {
        return chests.values();
    }

    public List<PortalChest> getPlayerChests(UUID playerUUID) {
        List<PortalChest> playerChests = new ArrayList<>();
        for (PortalChest chest : chests.values()) {
            if (chest.getOwner().equals(playerUUID)) {
                playerChests.add(chest);
            }
        }
        return playerChests;
    }

    public static String getLocationKey(Location location) {
        return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() 
            + "," + location.getWorld().getName();
    }

    public void setLastClicked(UUID uuid, Location location) {
        lastClicked.put(uuid, getLocationKey(location));
    }

    public Location getLastClicked(UUID uuid) {
        String key = lastClicked.get(uuid);
        if (key == null) return null;
        return locationFromKey(key);
    }

    private Location locationFromKey(String key) {
        String[] parts = key.split(",");
        if (parts.length != 4) return null;
        
        int x = Integer.parseInt(parts[0]);
        int y = Integer.parseInt(parts[1]);
        int z = Integer.parseInt(parts[2]);
        World world = Bukkit.getWorld(parts[3]);
        
        if (world == null) return null;
        return new Location(world, x, y, z);
    }

    // Item-Transfer Logik
    public void transferItems() {
        for (PortalChest chest : new ArrayList<>(chests.values())) {
            if (chest.getType() != PortalChest.ChestType.SENDER) continue;
            if (chest.getLinkedChest() == null) continue;

            long currentTime = System.currentTimeMillis();
            if (currentTime - chest.getLastTransferTime() < (configManager.getTransferInterval() * 50)) {
                continue;
            }

            chest.setLastTransferTime(currentTime);
            
            Block senderBlock = chest.getLocation().getBlock();
            Block receiverBlock = chest.getLinkedChest().getLocation().getBlock();

            if (senderBlock.getState() instanceof Container && receiverBlock.getState() instanceof Container) {
                Container senderContainer = (Container) senderBlock.getState();
                Container receiverContainer = (Container) receiverBlock.getState();
                
                Inventory senderInv = senderContainer.getInventory();
                Inventory receiverInv = receiverContainer.getInventory();

                int maxItems = configManager.enableSendLimit 
                    ? chest.getMaxItemsForTransfer(configManager)
                    : Integer.MAX_VALUE;

                boolean transferred = transferItemsInternal(senderInv, receiverInv, maxItems);
                if (transferred) {
                    // Sound bei erfolgreichem Transfer
                    dev.jankr.portalchest.PortalChestPlugin pluginInstance = (dev.jankr.portalchest.PortalChestPlugin) plugin;
                    pluginInstance.getSoundManager().playTeleportSound(chest.getLocation());
                    pluginInstance.getSoundManager().playTeleportSound(chest.getLinkedChest().getLocation());
                    
                    // Partikel von Sender zu Receiver (Connection-Linie)
                    drawConnectionParticles(chest.getLocation(), chest.getLinkedChest().getLocation());
                }
            }
        }
    }

    private boolean transferItemsInternal(Inventory sender, Inventory receiver, int maxItems) {
        int transferred = 0;
        boolean didTransfer = false;

        for (int i = 0; i < sender.getSize() && transferred < maxItems; i++) {
            ItemStack item = sender.getItem(i);
            if (item == null || item.getAmount() == 0) continue;

            int canTransfer = Math.min(item.getAmount(), maxItems - transferred);
            ItemStack toTransfer = item.clone();
            toTransfer.setAmount(canTransfer);

            int needed = receiver.addItem(toTransfer).values().stream()
                .mapToInt(ItemStack::getAmount).sum();

            if (needed < canTransfer) {
                item.setAmount(item.getAmount() - (canTransfer - needed));
                transferred += (canTransfer - needed);
                didTransfer = true;
            }
        }
        
        return didTransfer;
    }

    private void drawConnectionParticles(Location loc1, Location loc2) {
        // Nutze den ParticleManager für die Connection-Linie
        if (!(plugin instanceof dev.jankr.portalchest.PortalChestPlugin pluginInstance)) {
            return;
        }
        
        // Finde die Chest-Objekte um drawConnectionLine zu nutzen
        String key1 = getLocationKey(loc1);
        String key2 = getLocationKey(loc2);
        
        PortalChest sender = chests.get(key1);
        PortalChest receiver = chests.get(key2);
        
        // Zähle in welche Richtung
        if (sender == null && receiver != null) {
            sender = receiver.getLinkedChest();
        }
        if (receiver == null && sender != null) {
            receiver = sender.getLinkedChest();
        }
        
        // Spawne die Connection-Linie (5x hintereinander für visuellen Effekt)
        if (sender != null && receiver != null) {
            final PortalChest finalSender = sender;
            final PortalChest finalReceiver = receiver;
            final dev.jankr.portalchest.PortalChestPlugin pluginInst = (dev.jankr.portalchest.PortalChestPlugin) plugin;
            
            for (int iteration = 0; iteration < 5; iteration++) {
                final int delay = iteration * 5; // Verzögerung pro Iteration
                plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                    pluginInst.getParticleManager().drawConnectionLine(finalSender, finalReceiver);
                }, delay);
            }
        }
    }

    public void removePlayerData(UUID uuid) {
        lastClicked.remove(uuid);
    }
}
