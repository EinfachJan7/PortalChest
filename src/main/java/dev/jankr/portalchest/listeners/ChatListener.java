package dev.jankr.portalchest.listeners;

import dev.jankr.portalchest.PortalChestPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatListener implements Listener {

    private final PortalChestPlugin plugin;

    public ChatListener(PortalChestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // Prüfe: Ist Spieler im Linking-Modus?
        if (plugin.getChestPlaceListener().isInLinkingMode(player)) {
            String message = event.getMessage().trim();

            // Nur EXIT-Befehl erlaubt im Linking-Modus
            if (!message.equalsIgnoreCase("EXIT")) {
                event.setCancelled(true);
                player.sendMessage(plugin.getDisplayManager().parseMessage(
                        plugin.getConfigManager().getMessage("linking-mode.chat-blocked")
                ));
                player.sendMessage(plugin.getDisplayManager().parseMessage(
                        plugin.getConfigManager().getMessage("linking-mode.exit-hint")
                ));
                return;
            }

            // EXIT erkannt
            if (message.equalsIgnoreCase("EXIT")) {
                event.setCancelled(true);
                plugin.getChestPlaceListener().exitLinkingMode(player);
                player.sendMessage(plugin.getDisplayManager().parseMessage(
                        plugin.getConfigManager().getMessage("linking-mode.exit-exec")
                ));
            }
        }
    }
}
