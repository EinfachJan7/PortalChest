package dev.jankr.portalchest.model;

import org.bukkit.Location;
import lombok.Data;
import lombok.NonNull;
import dev.jankr.portalchest.manager.ConfigManager;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
public class PortalChest {

    @NonNull
    private Location location;

    @NonNull
    private UUID owner;

    @NonNull
    private ChestType type;  // SENDER oder RECEIVER

    private PortalChest linkedChest;
    private int upgradeLevel = 1;
    private long lastTransferTime = 0;
    private final Set<UUID> trustedPlayers = new HashSet<>();
    private UUID displayEntityId;
    private boolean hasDoubleChestUpgrade = false;  // Für RECEIVER-Chests

    public enum ChestType {
        SENDER, RECEIVER
    }

    public String getLocationString() {
        return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() + ","
                + location.getWorld().getName();
    }

    public static PortalChest fromLocationString(String str, Location loc, UUID owner) {
        PortalChest chest = new PortalChest(loc, owner, ChestType.SENDER);
        return chest;
    }

    public boolean isTrusted(UUID uuid) {
        return owner.equals(uuid) || trustedPlayers.contains(uuid);
    }

    public void addTrusted(UUID uuid) {
        if (!uuid.equals(owner)) {
            trustedPlayers.add(uuid);
        }
    }

    public void removeTrusted(UUID uuid) {
        trustedPlayers.remove(uuid);
    }

    public int getMaxItemsForTransfer(ConfigManager config) {
        return config.getMaxItemsForLevel(upgradeLevel);
    }
}
