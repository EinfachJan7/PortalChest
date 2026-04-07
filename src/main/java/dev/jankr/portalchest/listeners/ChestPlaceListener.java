package dev.jankr.portalchest.listeners;

import dev.jankr.portalchest.PortalChestPlugin;
import dev.jankr.portalchest.model.PortalChest;
import dev.jankr.portalchest.util.DataUtil;
import dev.jankr.portalchest.manager.DataManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class ChestPlaceListener implements Listener {

    private final PortalChestPlugin plugin;
    // Speichert: UUID -> {senderChestLocation, senderChestLevel}
    private final Map<UUID, LinkingSession> linkingSessions = new HashMap<>();

    /**
     * Startet den Linking-Modus für einen Spieler
     * (z.B. beim Abbau einer Portal Chest, um diese zu ersetzen)
     */
    public void startLinkingMode(Player player, Location senderLocation, int level) {
        UUID uuid = player.getUniqueId();
        linkingSessions.put(uuid, new LinkingSession(senderLocation, level));
    }

    /**
     * Prüft, ob ein Spieler sich im Linking-Modus befindet
     */
    public boolean isInLinkingMode(Player player) {
        return linkingSessions.containsKey(player.getUniqueId());
    }

    /**
     * Beendet den Linking-Modus für einen Spieler
     */
    public void exitLinkingMode(Player player) {
        linkingSessions.remove(player.getUniqueId());
    }

    public ChestPlaceListener(PortalChestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onChestPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Prüfe: Ist Spieler im Linking-Mode?
        if (linkingSessions.containsKey(uuid)) {
            // Im Linking-Mode: NUR normale Kisten erlaubt (keine Portal Chests)
            if (item.getType() != Material.CHEST) {
                event.setCancelled(true);
                player.sendMessage(plugin.getDisplayManager().parseMessage(
                        "<red>✘ Du kannst im Linking-Modus nur Kisten platzieren!"
                ));
                return;
            }

            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(
                    new NamespacedKey(plugin, "portal_chest_level"),
                    PersistentDataType.INTEGER)) {
                // Versuch, eine Portal Chest zu platzieren während Linking-Mode
                event.setCancelled(true);
                player.sendMessage(plugin.getDisplayManager().parseMessage(
                        "<red>✘ Du kannst keine Portal Chest während des Linking-Modus platzieren!"
                ));
                player.sendMessage(plugin.getDisplayManager().parseMessage(
                        "<dark_gray>Platziere eine normale Kiste um zu verlinken, oder shift+rechtsklick um abzubrechen."
                ));
                return;
            }

            // Normale Kiste im Linking-Mode - versuche zu linken
            handleNormalChestPlaced(event, player, event.getBlockPlaced());
            return;
        }

        // Nicht im Linking-Mode
        if (item.getType() != Material.CHEST) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        Block block = event.getBlockPlaced();

        // Prüfe auf Portal Chest NBT-Tag
        boolean isPortalChest = meta.getPersistentDataContainer().has(
                new NamespacedKey(plugin, "portal_chest_level"),
                PersistentDataType.INTEGER);

        if (isPortalChest) {
            handlePortalChestPlaced(event, player, block, meta);
        } else {
            // Normale Kiste - prüfe ob neben Portal Chest
            handleNormalChestPlaced_NoLinking(event, player, block);
        }
    }

    /**
     * Normale Kiste ohne Linking-Modus - prüfe Double-Chest-Regeln
     */
    private void handleNormalChestPlaced_NoLinking(BlockPlaceEvent event, Player player, Block block) {
        Block adjacentBlock = getAdjacentChestBlock(block);
        if (adjacentBlock == null) return;  // Keine benachbarte Kiste

        // Prüfe: Ist benachbarte Kiste eine Portal-Chest?
        PortalChest adjacentChest = plugin.getDataManager().getChest(adjacentBlock.getLocation());
        if (adjacentChest == null) return;  // Ist normale Kiste

        // Benachbarte Kiste ist Portal Chest - prüfe Doppelkisten-Upgrade
        if (adjacentChest.getType() != PortalChest.ChestType.RECEIVER || !adjacentChest.isHasDoubleChestUpgrade()) {
            event.setCancelled(true);
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    plugin.getConfigManager().getMessage("linking-mode.double-chest-error")
            ));
            if (adjacentChest.getType() != PortalChest.ChestType.RECEIVER) {
                player.sendMessage(plugin.getDisplayManager().parseMessage(
                        plugin.getConfigManager().getMessage("linking-mode.double-chest-not-receiver")
                ));
            } else {
                player.sendMessage(plugin.getDisplayManager().parseMessage(
                        plugin.getConfigManager().getMessage("linking-mode.double-chest-no-upgrade")
                ));
            }
        }
    }

    /**
     * Portal Chest wurde platziert -> Starte Linking-Mode
     */
    private void handlePortalChestPlaced(BlockPlaceEvent event, Player player, Block block, ItemMeta meta) {
        // Prüfe: Welt erlaubt?
        String worldName = block.getWorld().getName().toLowerCase();
        if (worldName.contains("nether") && !plugin.getConfigManager().isNetherAllowed()) {
            event.setCancelled(true);
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<red>✘ Portal Chests sind im Nether nicht erlaubt!"
            ));
            return;
        }
        if (worldName.contains("end") && !plugin.getConfigManager().isEndAllowed()) {
            event.setCancelled(true);
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<red>✘ Portal Chests sind im End nicht erlaubt!"
            ));
            return;
        }

        // Prüfe: Doppelkiste
        Block adjacentBlock = getAdjacentChestBlock(block);
        if (adjacentBlock != null) {
            event.setCancelled(true);
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<red>✘ Portal Chests können keine Doppelkisten sein!"
            ));
            return;
        }

        // Prüfe: Max Connections
        long senderCount = plugin.getDataManager().getPlayerChests(player.getUniqueId()).stream()
                .filter(c -> c.getType() == PortalChest.ChestType.SENDER).count();
        if (senderCount >= plugin.getConfigManager().getMaxConnections()) {
            event.setCancelled(true);
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<red>✘ Maximum von " + plugin.getConfigManager().getMaxConnections() + " Sender-Chests erreicht!"
            ));
            return;
        }

        int level = meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "portal_chest_level"),
                PersistentDataType.INTEGER);

        // Erstelle Portal Chest (noch nicht verlinkt)
        PortalChest chest = new PortalChest(
                block.getLocation(),
                player.getUniqueId(),
                PortalChest.ChestType.SENDER
        );
        chest.setUpgradeLevel(level);
        chest.setLinkedChest(null);
        plugin.getDataManager().registerChest(chest);

        // **STARTE LINKING-MODE**
        UUID uuid = player.getUniqueId();
        linkingSessions.put(uuid, new LinkingSession(block.getLocation(), level));

        String activeMsg = plugin.getConfigManager().getMessage("linking-mode.active")
                .replace("%level%", String.valueOf(level));
        player.sendMessage(plugin.getDisplayManager().parseMessage(activeMsg));
        player.sendMessage(plugin.getDisplayManager().parseMessage(
                plugin.getConfigManager().getMessage("linking-mode.waiting-receiver")
        ));
    }

    /**
     * Normale Kiste wurde platziert - versuche zu linken wenn im Linking-Mode
     */
    private void handleNormalChestPlaced(BlockPlaceEvent event, Player player, Block block) {
        UUID uuid = player.getUniqueId();
        LinkingSession session = linkingSessions.get(uuid);

        // Kein Linking-Mode aktiv
        if (session == null) {
            return;
        }

        Location senderLoc = session.senderLocation;
        int senderLevel = session.senderLevel;

        // Prüfe: Gleiche Dimension
        if (!senderLoc.getWorld().equals(block.getWorld())) {
            event.setCancelled(true);
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<red>✘ Die Receiver-Chest muss in der gleichen Dimension sein!"
            ));
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<dark_gray>Versuche es in der gleichen Dimension erneut."
            ));
            return;
        }

        // Prüfe: Min. Distanz
        if (senderLoc.distance(block.getLocation()) < plugin.getConfigManager().getMinDistance()) {
            event.setCancelled(true);
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<red>✘ Zu nah! Mindestabstand: " + plugin.getConfigManager().getMinDistance() + " Blöcke"
            ));
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<dark_gray>Platziere die Kiste weiter weg und versuche es erneut."
            ));
            return;
        }

        // Prüfe: Glück_äßig Doppelkiste?
        Block adjacentBlock = getAdjacentChestBlock(block);
        if (adjacentBlock != null) {
            // Prüfe: Ist die benachbarte Kiste eine RECEIVER mit Double-Chest-Upgrade?
            PortalChest adjacentChest = plugin.getDataManager().getChest(adjacentBlock.getLocation());
            boolean canPlaceDoubleCest = adjacentChest != null && 
                                        adjacentChest.getType() == PortalChest.ChestType.RECEIVER &&
                                        adjacentChest.isHasDoubleChestUpgrade();

            if (!canPlaceDoubleCest) {
                event.setCancelled(true);
                player.sendMessage(plugin.getDisplayManager().parseMessage(
                        plugin.getConfigManager().getMessage("linking-mode.double-chest-error")
                ));
                if (adjacentChest == null) {
                    player.sendMessage(plugin.getDisplayManager().parseMessage(
                            "<dark_gray>Diese Kiste ist nicht verlinkt."
                    ));
                } else if (adjacentChest.getType() != PortalChest.ChestType.RECEIVER) {
                    player.sendMessage(plugin.getDisplayManager().parseMessage(
                            plugin.getConfigManager().getMessage("linking-mode.double-chest-not-receiver")
                    ));
                } else {
                    player.sendMessage(plugin.getDisplayManager().parseMessage(
                            plugin.getConfigManager().getMessage("linking-mode.double-chest-no-upgrade")
                    ));
                }
                return;
            }
        }

        // ===== LINKING ERFOLGREICH =====
        Location receiverLoc = block.getLocation();

        // Erstelle Receiver Chest
        PortalChest senderChest = plugin.getDataManager().getChest(senderLoc);
        PortalChest receiverChest = new PortalChest(
                receiverLoc,
                player.getUniqueId(),
                PortalChest.ChestType.RECEIVER
        );

        // Verlinke beide
        senderChest.setLinkedChest(receiverChest);
        receiverChest.setLinkedChest(senderChest);

        plugin.getDataManager().registerChest(receiverChest);
        plugin.getDataManager().saveData();

        // Setze Custom Names
        String senderName = plugin.getConfigManager().getSenderChestName(senderLevel);
        String receiverName = plugin.getConfigManager().getReceiverChestName();

        org.bukkit.block.Block senderBlock = senderLoc.getBlock();
        if (senderBlock.getState() instanceof org.bukkit.block.Container senderContainer) {
            senderContainer.customName(plugin.getDisplayManager().parseMessage(senderName));
            senderContainer.update();
        }

        org.bukkit.block.Block receiverBlock = receiverLoc.getBlock();
        if (receiverBlock.getState() instanceof org.bukkit.block.Container receiverContainer) {
            receiverContainer.customName(plugin.getDisplayManager().parseMessage(receiverName));
            receiverContainer.update();
        }

        // Spawne Displays
        org.bukkit.entity.BlockDisplay senderDisplay = plugin.getDisplayManager().spawnDisplay(senderLoc, senderLevel, "sender");
        org.bukkit.entity.BlockDisplay receiverDisplay = plugin.getDisplayManager().spawnDisplay(receiverLoc, senderLevel, "receiver");
        
        // Registriere Displays zur Verwaltung (nur wenn sie existieren)
        String senderKey = plugin.getDataManager().getLocationKey(senderLoc);
        String receiverKey = plugin.getDataManager().getLocationKey(receiverLoc);
        if (senderDisplay != null) {
            plugin.getDisplayManager().registerDisplay(senderKey, senderDisplay.getUniqueId());
        }
        if (receiverDisplay != null) {
            plugin.getDisplayManager().registerDisplay(receiverKey, receiverDisplay.getUniqueId());
        }

        // Beende Linking-Mode
        linkingSessions.remove(uuid);

        // Spiele Sound für Erfolg ab
        plugin.getSoundManager().playTeleportSound(senderLoc);
        plugin.getSoundManager().playTeleportSound(receiverLoc);

        // Spiele Partikel-Effekte ab
        plugin.getParticleManager().spawnTeleportEffect(senderLoc);
        plugin.getParticleManager().spawnTeleportEffect(receiverLoc);
        
        // Zeichne Partikelverbindung
        plugin.getParticleManager().drawConnectionLine(senderChest, receiverChest);

        player.sendMessage(plugin.getDisplayManager().parseMessage(
                "<green><bold><italic:false>✓ Verbindung hergestellt!"
        ));
    }

    private org.bukkit.block.Block getAdjacentChestBlock(org.bukkit.block.Block block) {
        int[][] offsets = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] offset : offsets) {
            org.bukkit.block.Block adjacent = block.getRelative(offset[0], offset[1], offset[2]);
            if (adjacent.getType().name().contains("CHEST")) {
                return adjacent;
            }
        }
        return null;
    }

    /**
     * Hilfsklasse für Linking-Session
     */
    private static class LinkingSession {
        Location senderLocation;
        int senderLevel;

        LinkingSession(Location senderLocation, int senderLevel) {
            this.senderLocation = senderLocation;
            this.senderLevel = senderLevel;
        }
    }
}
