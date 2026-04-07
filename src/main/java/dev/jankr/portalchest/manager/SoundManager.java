package dev.jankr.portalchest.manager;

import dev.jankr.portalchest.PortalChestPlugin;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Verwaltet die Sound-Effekte für PortalChest (Teleport-Sounds)
 */
public class SoundManager {
    
    private final PortalChestPlugin plugin;
    private final ConfigManager configManager;

    public SoundManager(PortalChestPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    /**
     * Spiele den Teleport-Sound an einer Location
     */
    public void playTeleportSound(Location location) {
        if (!configManager.isTeleportSoundEnabled()) {
            return;
        }

        try {
            String soundName = configManager.getTeleportSoundType();
            // Konvertiere String zu Sound enum
            Sound sound = parseSound(soundName);
            
            location.getWorld().playSound(
                location,
                sound,
                configManager.getTeleportSoundVolume(),
                configManager.getTeleportSoundPitch()
            );
        } catch (IllegalArgumentException e) {
            // Fallback auf Standard-Sound wenn dieser nicht existiert
            location.getWorld().playSound(
                location,
                Sound.ENTITY_ENDERMAN_TELEPORT,
                configManager.getTeleportSoundVolume(),
                configManager.getTeleportSoundPitch()
            );
        }
    }

    /**
     * Spiele den Teleport-Sound für einen Spieler
     */
    public void playTeleportSoundForPlayer(Player player) {
        playTeleportSound(player.getLocation());
    }

    /**
     * Konvertiere einen Sound-String in einen Sound enum
     */
    private Sound parseSound(String soundName) {
        // Versuche direkten Namen (entity.enderman.teleport)
        try {
            // Konvertiere Punkte in Unterstriche für Sound enum Namen
            String enumName = soundName.toUpperCase().replace(".", "_");
            return Sound.valueOf(enumName);
        } catch (IllegalArgumentException e1) {
            // Versuche den Namen direkt
            try {
                return Sound.valueOf(soundName.toUpperCase());
            } catch (IllegalArgumentException e2) {
                // Fallback auf Standard-Sound
                return Sound.ENTITY_ENDERMAN_TELEPORT;
            }
        }
    }

    /**
     * Spiele einen Custom Sound mit allen Einstellungen
     */
    public void playCustomSound(Location location, String soundName, float volume, float pitch) {
        try {
            Sound sound = parseSound(soundName);
            location.getWorld().playSound(location, sound, volume, pitch);
        } catch (Exception e) {
            plugin.getLogger().warning("Konnte Sound nicht abspielen: " + soundName);
        }
    }
}
