package dev.jankr.portalchest.manager;

import dev.jankr.portalchest.PortalChestPlugin;
import dev.jankr.portalchest.model.PortalChest;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

/**
 * Verwaltet die Partikeleffekte für PortalChest-Verbindungen
 */
public class ParticleManager {
    
    private final PortalChestPlugin plugin;
    private final ConfigManager configManager;

    public ParticleManager(PortalChestPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    /**
     * Zeigt eine Partikelverbindung zwischen zwei verknüpften Chests
     */
    public void drawConnectionLine(PortalChest senderChest, PortalChest receiverChest) {
        // Nur wenn Connection-Particles aktiviert und Particle-Display aktiv
        if (!("PARTICLES".equalsIgnoreCase(configManager.getDisplayType())) || 
            !configManager.isConnectionParticlesEnabled() || receiverChest == null) {
            return;
        }

        Location start = senderChest.getLocation().clone();
        Location end = receiverChest.getLocation().clone();

        // Verschiebe die Startposition in die Mitte der Chest mit Offset
        start.add(0.5, configManager.getConnectionParticleHeightOffset(), 0.5);
        start.add(configManager.getConnectionParticleXOffset(), 0, configManager.getConnectionParticleZOffset());

        // Verschiebe die Endposition in die Mitte der Chest mit Offset
        end.add(0.5, configManager.getConnectionParticleHeightOffset(), 0.5);
        end.add(configManager.getConnectionParticleXOffset(), 0, configManager.getConnectionParticleZOffset());

        // Berechne die Distanz zwischen den Chests
        double distance = start.distance(end);
        int steps = (int) (distance * configManager.getConnectionParticleDensity());

        // Zeichne eine Linie aus Partikeln
        for (int i = 0; i <= steps; i++) {
            double progress = steps > 0 ? (double) i / steps : 0;
            
            double x = start.getX() + (end.getX() - start.getX()) * progress;
            double y = start.getY() + (end.getY() - start.getY()) * progress;
            double z = start.getZ() + (end.getZ() - start.getZ()) * progress;
            
            Location particleLocation = new Location(start.getWorld(), x, y, z);
            
            try {
                // Hole Partikeltyp für Connection-Line
                String particleType = configManager.getConnectionParticleType();
                Particle particle = Particle.valueOf(particleType.toUpperCase());
                start.getWorld().spawnParticle(particle, particleLocation, configManager.getConnectionParticleCount(), 
                    configManager.getConnectionParticleSpread(), configManager.getConnectionParticleSpread(), configManager.getConnectionParticleSpread());
            } catch (IllegalArgumentException e) {
                // Fallback auf glow wenn Partikeltyp nicht existiert
                start.getWorld().spawnParticle(Particle.GLOW, particleLocation, configManager.getConnectionParticleCount(), 
                    configManager.getConnectionParticleSpread(), configManager.getConnectionParticleSpread(), configManager.getConnectionParticleSpread());
            }
        }
    }


    /**
     * Zeigt mehrere Partikeleffekte für Teleport-Animation
     */
    public void spawnTeleportEffect(Location location) {
        // Nur wenn Particle-Display aktiv ist
        if (!("PARTICLES".equalsIgnoreCase(configManager.getDisplayType()))) {
            return;
        }

        // Zeige mehrere Partikeltypen für schönen Effekt
        int count = 15;
        double radius = 0.5;

        location.getWorld().spawnParticle(Particle.GLOW, location, count, radius, radius, radius);
        location.getWorld().spawnParticle(Particle.PORTAL, location, 10, radius * 0.7, radius * 0.7, radius * 0.7);
    }
}
