package dev.jankr.portalchest.manager;

import dev.jankr.portalchest.PortalChestPlugin;
import dev.jankr.portalchest.model.PortalChest;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.Color;

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
     * WICHTIG: Wird nur bei aktiven Item-Transfers aufgerufen!
     */
    public void drawConnectionLine(PortalChest senderChest, PortalChest receiverChest) {
        // Nur wenn Connection-Particles aktiviert
        if (!configManager.isConnectionParticlesEnabled() || receiverChest == null) {
            return;
        }

        Location start = senderChest.getLocation().clone();
        Location end = receiverChest.getLocation().clone();

        // Verschiebe zu Chest-Mittelpunkt
        start.add(0.5, 0.5, 0.5);
        end.add(0.5, 0.5, 0.5);

        // Berechne die Distanz zwischen den Chests
        double distance = start.distance(end);
        
        // Höhere Dichte = mehr Partikel pro Block
        int steps = Math.max(5, (int) (distance * configManager.getConnectionParticleDensity()));

        // Zeichne eine Linie aus Partikeln - PRÄZISE POSITIONEN
        for (int i = 0; i <= steps; i++) {
            double progress = (double) i / steps;
            
            double x = start.getX() + (end.getX() - start.getX()) * progress;
            double y = start.getY() + (end.getY() - start.getY()) * progress;
            double z = start.getZ() + (end.getZ() - start.getZ()) * progress;
            
            Location particleLocation = new Location(start.getWorld(), x, y, z);
            
            // Spawne mit Count=1 und Spread=0 für stabile Linie
            spawnParticleWithType(particleLocation, configManager.getConnectionParticleType(), 
                1, 0.0);
        }
    }

    /**
     * Spawnt Partikel mit intelligenter Typ-Erkennung (unterstützt parametrisierte Partikel)
     */
    private void spawnParticleWithType(Location location, String particleTypeName, int count, double spread) {
        try {
            String typeName = particleTypeName.toUpperCase().replace("-", "_");
            
            // Spezielle Partikel-Typen mit Parametern
            if (typeName.contains("COPPER_FIRE_FLAME")) {
                location.getWorld().spawnParticle(Particle.FLAME, location, count, spread, spread, spread);
                return;
            }
            
            if (typeName.contains("DUST") || typeName.contains("COLOR")) {
                // Dust Partikel mit Farbe
                location.getWorld().spawnParticle(Particle.DUST, location, count, spread, spread, spread, new Particle.DustOptions(Color.fromBGR(0xFF6B35), 1.0f));
                return;
            }
            
            // Versuche Standard-Partikeltyp zu laden
            try {
                Particle particle = Particle.valueOf(typeName);
                location.getWorld().spawnParticle(particle, location, count, spread, spread, spread);
            } catch (IllegalArgumentException e1) {
                // Fallback: Versuche mit Underscore-Konvertierung
                if (typeName.contains("_")) {
                    Particle particle = Particle.valueOf(typeName);
                    location.getWorld().spawnParticle(particle, location, count, spread, spread, spread);
                } else {
                    // Default fallback
                    location.getWorld().spawnParticle(Particle.GLOW, location, count, spread, spread, spread);
                }
            }
        } catch (Exception e) {
            // Final fallback
            location.getWorld().spawnParticle(Particle.GLOW, location, count, spread, spread, spread);
        }
    }

    /**
     * Zeigt mehrere Partikeleffekte für Teleport-Animation
     */
    public void spawnTeleportEffect(Location location) {
        // Zeige mehrere Partikeltypen für schönen Effekt
        int count = 15;
        double radius = 0.5;

        spawnParticleWithType(location, "GLOW", count, radius);
        location.getWorld().spawnParticle(Particle.PORTAL, location, 10, radius * 0.7, radius * 0.7, radius * 0.7);
    }
}
