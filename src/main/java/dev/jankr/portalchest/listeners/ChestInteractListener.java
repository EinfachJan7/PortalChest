package dev.jankr.portalchest.listeners;

import dev.jankr.portalchest.PortalChestPlugin;
import dev.jankr.portalchest.model.PortalChest;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

import java.util.*;

public class ChestInteractListener implements Listener {

    private final PortalChestPlugin plugin;
    private final MiniMessage miniMessage;

    public ChestInteractListener(PortalChestPlugin plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!event.getClickedBlock().getType().name().contains("CHEST")) return;

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Location location = block.getLocation();

        // Portal Linker wird nicht mehr benötigt
        // Linking erfolgt durch Platzieren von Chests
        handleNormalAccess(event, player, location);
    }

    private void handleNormalAccess(PlayerInteractEvent event, Player player, Location location) {
        PortalChest chest = plugin.getDataManager().getChest(location);
        if (chest == null) return;

        // Prüfe Zugriff
        if (!chest.isTrusted(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    plugin.getConfigManager().getMessage("access.no-permission")
            ));
            return;
        }

        // Speichere als zuletzt geklickt
        plugin.getDataManager().setLastClicked(player.getUniqueId(), location);

        // Shift + Rechtsklick = GUI öffnen (Sender oder Receiver)
        if (player.isSneaking()) {
            if (plugin.getConfigManager().isEnableUpgrades()) {
                event.setCancelled(true);
                // Speichere GUI-Location für InventoryListener
                String locKey = location.getBlockX() + "," + location.getBlockY() + "," + 
                               location.getBlockZ() + "," + location.getWorld().getName();
                plugin.getGuiLocations().put(player.getUniqueId(), locKey);
                
                // Spiele Sound ab
                plugin.getSoundManager().playTeleportSoundForPlayer(player);
                
                // Öffne Upgrade-GUI
                plugin.getUpgradeGUI().openUpgradeGUI(player, chest);
            }
        }
    }

    private org.bukkit.block.Block getAdjacentChestBlock(org.bukkit.block.Block block) {
        // Prüfe alle 4 horizontalen Seiten (nicht oben/unten)
        int[][] offsets = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] offset : offsets) {
            org.bukkit.block.Block adjacent = block.getRelative(offset[0], offset[1], offset[2]);
            if (adjacent.getType().name().contains("CHEST")) {
                return adjacent;
            }
        }
        return null;
    }
}
