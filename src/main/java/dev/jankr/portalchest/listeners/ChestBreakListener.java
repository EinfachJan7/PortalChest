package dev.jankr.portalchest.listeners;

import dev.jankr.portalchest.PortalChestPlugin;
import dev.jankr.portalchest.model.PortalChest;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

public class ChestBreakListener implements Listener {

    private final PortalChestPlugin plugin;

    public ChestBreakListener(PortalChestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChestBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType().name().contains("CHEST")) {
            Player player = event.getPlayer();
            Location location = block.getLocation();

            PortalChest chest = plugin.getDataManager().getChest(location);
            if (chest == null) {
                // Cancel double chest drop if adjacent is a Portal Chest
                Block adjacent = getAdjacentChest(block);
                if (adjacent != null && plugin.getDataManager().getChest(adjacent.getLocation()) != null) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.getDisplayManager().parseMessage(
                            "<color:#FFA500>Die verlinkte Chest ist auch eine Portal Chest. Baue diese zuerst ab!</color>"
                    ));
                }
                return;
            }

            // Prüfe Berechtigung
            if (!chest.isTrusted(player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage(plugin.getDisplayManager().parseMessage(
                        "<color:#FF6B6B>✘ Diese Chest gehört dir nicht!</color>"
                ));
                return;
            }

            // Prüfe Shift-Taste
            if (!player.isSneaking()) {
                event.setCancelled(true);
                player.sendMessage(plugin.getDisplayManager().parseMessage(
                        "<color:#FFA500>Halte SHIFT um diese Chest abzubauen!</color>"
                ));
                return;
            }

            // Entferne verlinkte Chest (wird zu normaler Kiste)
            if (chest.getLinkedChest() != null) {
                PortalChest linkedChest = chest.getLinkedChest();
                
                // Breche Linked-Beziehung auf (so wird auch die andere Seite gelöst)
                linkedChest.setLinkedChest(null);
                
                // Entferne Display von der anderen Chest
                String linkedKey = plugin.getDataManager().getLocationKey(linkedChest.getLocation());
                plugin.getDisplayManager().removeBlockDisplay(linkedKey);
                
                // Entferne Custom Name vom Block der anderen Chest
                org.bukkit.block.Block linkedBlock = linkedChest.getLocation().getBlock();
                if (linkedBlock.getState() instanceof org.bukkit.block.Container linkedContainer) {
                    linkedContainer.setCustomName(null);
                    linkedContainer.update();
                }
                
                // Unregister aus DataManager (wird normale Kiste)
                plugin.getDataManager().unregisterChest(linkedChest.getLocation());
            }

            // Entferne aktuelle Chest
            dropChestContents(location);
            
            // Drop NUR die abgebaute Chest mit Level (kein automatischer Drop von Minecraft)
            event.setDropItems(false);
            ItemStack chestItem = dropChestWithLevel(chest);
            location.getWorld().dropItemNaturally(location, chestItem);

            // Entferne Display dieser Chest
            String locationKey = plugin.getDataManager().getLocationKey(location);
            plugin.getDisplayManager().removeBlockDisplay(locationKey);

            // Spiele Effekte ab
            plugin.getSoundManager().playTeleportSound(location);
            plugin.getParticleManager().spawnTeleportEffect(location);

            // Unregister
            plugin.getDataManager().unregisterChest(location);

            // Starte Linking-Modus - Spieler muss neue Chest platzieren
            plugin.getChestPlaceListener().startLinkingMode(player, location, chest.getUpgradeLevel());
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    plugin.getConfigManager().getMessage("linking-mode.chest-removed")
            ));
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    plugin.getConfigManager().getMessage("linking-mode.restart-linking")
            ));
        }
    }

    private Block getAdjacentChest(Block block) {
        // Prüfe alle 4 Seiten (nicht oben/unten)
        int[][] offsets = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] offset : offsets) {
            Block adjacent = block.getLocation().add(offset[0], offset[1], offset[2]).getBlock();
            if (adjacent.getType().name().contains("CHEST")) {
                return adjacent;
            }
        }
        return null;
    }

    private void dropChestContents(Location location) {
        Block block = location.getBlock();
        if (block.getState() instanceof Chest chest) {
            for (ItemStack item : chest.getInventory().getContents()) {
                if (item != null && item.getAmount() > 0) {
                    location.getWorld().dropItemNaturally(location, item.clone());
                }
            }
            chest.getInventory().clear();
        }
    }

    private ItemStack dropChestWithLevel(PortalChest chest) {
        ItemStack item = new ItemStack(org.bukkit.Material.CHEST);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) meta = org.bukkit.Bukkit.getItemFactory().getItemMeta(org.bukkit.Material.CHEST);
        
        int level = chest.getUpgradeLevel();
        int maxLevels = plugin.getConfigManager().getMaxLevels();
        if (level < 1 || level > maxLevels) level = 1;
        
        int maxItems = plugin.getConfigManager().getMaxItemsForLevel(level);
        String transferTime = plugin.getConfigManager().getTransferSeconds();
        
        // Name aus Config mit Placeholders
        try {
            String chestName = plugin.getConfigManager().getChestName(level);
            if (chestName != null && !chestName.isEmpty()) {
                net.kyori.adventure.text.Component nameComponent = plugin.getDisplayManager().parseMessage(chestName);
                meta.displayName(nameComponent);
                plugin.getLogger().info("✓ Name gesetzt für Level " + level + ": " + chestName);
            } else {
                plugin.getLogger().warning("WARNUNG: Config-Name ist null oder leer für Level " + level);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("FEHLER beim Name-Setzen: " + e.getMessage());
        }
        
        // Lore aus Config mit Placeholders
        try {
            java.util.List<String> lorePaths = plugin.getConfigManager().getChestLore(level);
            if (lorePaths != null && !lorePaths.isEmpty()) {
                java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
                
                for (String loreLine : lorePaths) {
                    if (loreLine == null || loreLine.isEmpty()) continue;
                    String processedLine = loreLine
                        .replace("%items%", String.valueOf(maxItems))
                        .replace("%time%", transferTime);
                    
                    // Parse MiniMessage zu Component
                    net.kyori.adventure.text.Component component = plugin.getDisplayManager().parseMessage(processedLine);
                    lore.add(component);
                }
                
                if (!lore.isEmpty()) {
                    meta.lore(lore);
                    plugin.getLogger().info("✓ Lore gesetzt für Level " + level + ": " + lore.size() + " Zeilen");
                } else {
                    plugin.getLogger().warning("Lore ist leer nach Verarbeitung für Level " + level);
                }
            } else {
                plugin.getLogger().warning("WARNUNG: Config-Lore ist null oder leer für Level " + level);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("FEHLER beim Lore-Setzen: " + e.getMessage());
            e.printStackTrace();
        }
        
        // NBT Tag
        try {
            meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, "portal_chest_level"),
                org.bukkit.persistence.PersistentDataType.INTEGER,
                level
            );
        } catch (Exception e) {
            plugin.getLogger().warning("FEHLER beim NBT-Tag-Setzen: " + e.getMessage());
        }
        
        item.setItemMeta(meta);
        return item;
    }


    
}
