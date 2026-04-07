package dev.jankr.portalchest.listeners;

import dev.jankr.portalchest.PortalChestPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerDisconnectListener implements Listener {

    private final PortalChestPlugin plugin;

    public PlayerDisconnectListener(PortalChestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerQuitEvent event) {
        plugin.getDataManager().removePlayerData(event.getPlayer().getUniqueId());
    }
}
