package dev.jankr.portalchest.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;
import dev.jankr.portalchest.model.PortalChest;

import java.io.*;
import java.util.*;

public class DataUtil {

    private static final Yaml yaml = new Yaml();

    public static Map<String, Object> loadYaml(File file) {
        if (!file.exists()) {
            return new HashMap<>();
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            return yaml.load(fis);
        } catch (IOException e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    public static void saveYaml(File file, Map<String, ?> data) {
        try (FileWriter writer = new FileWriter(file)) {
            yaml.dump(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Map<String, Object> serializeChest(PortalChest chest) {
        Map<String, Object> data = new LinkedHashMap<>();
        
        data.put("owner", chest.getOwner().toString());
        data.put("type", chest.getType().toString());
        data.put("location", serializeLocation(chest.getLocation()));
        data.put("upgrade-level", chest.getUpgradeLevel());
        data.put("double-chest-upgrade", chest.isHasDoubleChestUpgrade());
        
        // Trusted Players
        List<String> trusted = new ArrayList<>();
        for (UUID uuid : chest.getTrustedPlayers()) {
            trusted.add(uuid.toString());
        }
        data.put("trusted", trusted);

        // Linked Chest
        if (chest.getLinkedChest() != null) {
            data.put("linked-location", serializeLocation(chest.getLinkedChest().getLocation()));
        }

        return data;
    }

    public static PortalChest deserializeChest(Map<String, Object> data, JavaPlugin plugin) {
        try {
            UUID owner = UUID.fromString((String) data.get("owner"));
            PortalChest.ChestType type = PortalChest.ChestType.valueOf((String) data.get("type"));
            Location location = deserializeLocation((Map<String, Object>) data.get("location"));

            if (location == null) {
                return null;
            }

            PortalChest chest = new PortalChest(location, owner, type);

            // Upgrade Level
            if (data.containsKey("upgrade-level")) {
                chest.setUpgradeLevel(((Number) data.get("upgrade-level")).intValue());
            }

            // Double Chest Upgrade
            if (data.containsKey("double-chest-upgrade")) {
                chest.setHasDoubleChestUpgrade((Boolean) data.get("double-chest-upgrade"));
            }

            // Trusted Players
            if (data.containsKey("trusted")) {
                List<String> trusted = (List<String>) data.get("trusted");
                for (String uuidStr : trusted) {
                    try {
                        chest.getTrustedPlayers().add(UUID.fromString(uuidStr));
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            return chest;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Map<String, Object> serializeLocation(Location loc) {
        Map<String, Object> locData = new LinkedHashMap<>();
        locData.put("x", loc.getBlockX());
        locData.put("y", loc.getBlockY());
        locData.put("z", loc.getBlockZ());
        locData.put("world", loc.getWorld() != null ? loc.getWorld().getName() : "world");
        return locData;
    }

    public static Location deserializeLocation(Map<String, Object> data) {
        try {
            int x = ((Number) data.get("x")).intValue();
            int y = ((Number) data.get("y")).intValue();
            int z = ((Number) data.get("z")).intValue();
            String worldName = (String) data.get("world");

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                world = Bukkit.getWorlds().get(0);  // Fallback zur ersten Welt
            }

            return new Location(world, x, y, z);
        } catch (Exception e) {
            return null;
        }
    }

    public static String formatAmpersandColors(String text) {
        return text.replace("&", "§");
    }
}
