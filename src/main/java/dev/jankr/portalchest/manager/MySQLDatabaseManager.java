package dev.jankr.portalchest.manager;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.jankr.portalchest.PortalChestPlugin;
import dev.jankr.portalchest.model.PortalChest;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.*;
import java.util.*;

/**
 * MySQL-Speicherung für PortalChests
 */
public class MySQLDatabaseManager {
    
    private final PortalChestPlugin plugin;
    private final ConfigManager configManager;
    private HikariDataSource dataSource;

    public MySQLDatabaseManager(PortalChestPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        
        try {
            initializeConnection();
            createTables();
        } catch (Exception e) {
            plugin.getLogger().severe("Fehler beim Initialisieren von MySQL!");
            e.printStackTrace();
        }
    }

    /**
     * Initialisiere MySQL Connection Pool
     */
    private void initializeConnection() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + configManager.getMySQLHost() + ":" + 
                         configManager.getMySQLPort() + "/" + configManager.getMySQLDatabase());
        config.setUsername(configManager.getMySQLUsername());
        config.setPassword(configManager.getMySQLPassword());
        config.setMaximumPoolSize(configManager.getMySQLPoolSize());
        config.setConnectionTimeout(configManager.getMySQLTimeout());
        config.setAutoCommit(true);
        config.setLeakDetectionThreshold(60000L);
        
        this.dataSource = new HikariDataSource(config);
        
        plugin.getLogger().info("✓ MySQL Connection Pool initialisiert!");
    }

    /**
     * Erstelle Datenbanktabellen
     */
    private void createTables() throws SQLException {
        String tablePrefix = configManager.getMySQLTablePrefix();
        
        String chestsTable = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "chests (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "world VARCHAR(255) NOT NULL," +
                "x INT NOT NULL," +
                "y INT NOT NULL," +
                "z INT NOT NULL," +
                "owner_uuid VARCHAR(36) NOT NULL," +
                "chest_type VARCHAR(20) NOT NULL," +
                "upgrade_level INT DEFAULT 1," +
                "has_double_chest_upgrade BOOLEAN DEFAULT FALSE," +
                "linked_chest_id INT," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "UNIQUE KEY unique_location (world, x, y, z)," +
                "KEY idx_owner (owner_uuid)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
        
        String trustedTable = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "trusted_players (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "chest_id INT NOT NULL," +
                "player_uuid VARCHAR(36) NOT NULL," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "FOREIGN KEY (chest_id) REFERENCES " + tablePrefix + "chests(id) ON DELETE CASCADE," +
                "UNIQUE KEY unique_trust (chest_id, player_uuid)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(chestsTable);
            stmt.execute(trustedTable);
            plugin.getLogger().info("✓ MySQL Tabellen erstellt/überprüft!");
        }
    }

    /**
     * Speichere eine Chest in MySQL
     */
    public void saveChest(PortalChest chest) {
        String tablePrefix = configManager.getMySQLTablePrefix();
        String query = "INSERT INTO " + tablePrefix + "chests " +
                "(world, x, y, z, owner_uuid, chest_type, upgrade_level, has_double_chest_upgrade, linked_chest_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "upgrade_level=VALUES(upgrade_level), " +
                "has_double_chest_upgrade=VALUES(has_double_chest_upgrade), " +
                "linked_chest_id=VALUES(linked_chest_id), " +
                "updated_at=CURRENT_TIMESTAMP";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            
            pstmt.setString(1, chest.getLocation().getWorld().getName());
            pstmt.setInt(2, chest.getLocation().getBlockX());
            pstmt.setInt(3, chest.getLocation().getBlockY());
            pstmt.setInt(4, chest.getLocation().getBlockZ());
            pstmt.setString(5, chest.getOwner().toString());
            pstmt.setString(6, chest.getType().toString());
            pstmt.setInt(7, chest.getUpgradeLevel());
            pstmt.setBoolean(8, chest.isHasDoubleChestUpgrade());
            
            if (chest.getLinkedChest() != null) {
                pstmt.setInt(9, getChestIdFromLocation(chest.getLinkedChest().getLocation()));
            } else {
                pstmt.setNull(9, Types.INTEGER);
            }
            
            pstmt.executeUpdate();
            
            // Speichere Trusted Players
            saveTrustedPlayers(chest);
            
        } catch (SQLException e) {
            plugin.getLogger().warning("Fehler beim Speichern der Chest in MySQL!");
            e.printStackTrace();
        }
    }

    /**
     * Lade eine Chest von MySQL
     */
    public PortalChest loadChest(Location location) {
        String tablePrefix = configManager.getMySQLTablePrefix();
        String query = "SELECT * FROM " + tablePrefix + "chests " +
                "WHERE world=? AND x=? AND y=? AND z=?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            
            pstmt.setString(1, location.getWorld().getName());
            pstmt.setInt(2, location.getBlockX());
            pstmt.setInt(3, location.getBlockY());
            pstmt.setInt(4, location.getBlockZ());
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    UUID owner = UUID.fromString(rs.getString("owner_uuid"));
                    PortalChest.ChestType type = PortalChest.ChestType.valueOf(rs.getString("chest_type"));
                    
                    PortalChest chest = new PortalChest(location, owner, type);
                    chest.setUpgradeLevel(rs.getInt("upgrade_level"));
                    chest.setHasDoubleChestUpgrade(rs.getBoolean("has_double_chest_upgrade"));
                    
                    // Lade Trusted Players
                    loadTrustedPlayers(chest, rs.getInt("id"));
                    
                    return chest;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Fehler beim Laden der Chest von MySQL!");
            e.printStackTrace();
        }
        
        return null;
    }

    /**
     * Lösche eine Chest von MySQL
     */
    public void deleteChest(PortalChest chest) {
        String tablePrefix = configManager.getMySQLTablePrefix();
        String query = "DELETE FROM " + tablePrefix + "chests " +
                "WHERE world=? AND x=? AND y=? AND z=?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            
            pstmt.setString(1, chest.getLocation().getWorld().getName());
            pstmt.setInt(2, chest.getLocation().getBlockX());
            pstmt.setInt(3, chest.getLocation().getBlockY());
            pstmt.setInt(4, chest.getLocation().getBlockZ());
            
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            plugin.getLogger().warning("Fehler beim Löschen der Chest von MySQL!");
            e.printStackTrace();
        }
    }

    /**
     * Lade alle Chests von MySQL
     */
    public List<PortalChest> loadAllChests() {
        List<PortalChest> chests = new ArrayList<>();
        String tablePrefix = configManager.getMySQLTablePrefix();
        String query = "SELECT * FROM " + tablePrefix + "chests";
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            while (rs.next()) {
                try {
                    World world = plugin.getServer().getWorld(rs.getString("world"));
                    if (world == null) continue;
                    
                    Location loc = new Location(world, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                    PortalChest chest = loadChest(loc);
                    if (chest != null) {
                        chests.add(chest);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Fehler beim Laden einer Chest!");
                }
            }
            
        } catch (SQLException e) {
            plugin.getLogger().warning("Fehler beim Laden aller Chests von MySQL!");
            e.printStackTrace();
        }
        
        return chests;
    }

    /**
     * Speichere Trusted Players
     */
    private void saveTrustedPlayers(PortalChest chest) {
        String tablePrefix = configManager.getMySQLTablePrefix();
        
        try (Connection conn = dataSource.getConnection()) {
            // Lösche alte Trusted Players
            String deleteQuery = "DELETE FROM " + tablePrefix + "trusted_players " +
                    "WHERE chest_id=?";
            
            int chestId = getChestIdFromLocation(chest.getLocation());
            
            try (PreparedStatement pstmt = conn.prepareStatement(deleteQuery)) {
                pstmt.setInt(1, chestId);
                pstmt.executeUpdate();
            }
            
            // Füge neue hinzu
            String insertQuery = "INSERT INTO " + tablePrefix + "trusted_players (chest_id, player_uuid) VALUES (?, ?)";
            
            try (PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {
                for (UUID uuid : chest.getTrustedPlayers()) {
                    pstmt.setInt(1, chestId);
                    pstmt.setString(2, uuid.toString());
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }
            
        } catch (SQLException e) {
            plugin.getLogger().warning("Fehler beim Speichern von Trusted Players!");
            e.printStackTrace();
        }
    }

    /**
     * Lade Trusted Players
     */
    private void loadTrustedPlayers(PortalChest chest, int chestId) {
        String tablePrefix = configManager.getMySQLTablePrefix();
        String query = "SELECT player_uuid FROM " + tablePrefix + "trusted_players WHERE chest_id=?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            
            pstmt.setInt(1, chestId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    chest.addTrusted(UUID.fromString(rs.getString("player_uuid")));
                }
            }
            
        } catch (SQLException e) {
            plugin.getLogger().warning("Fehler beim Laden von Trusted Players!");
        }
    }

    /**
     * Hole die Datenbank-ID einer Chest basierend auf Location
     */
    private int getChestIdFromLocation(Location loc) {
        String tablePrefix = configManager.getMySQLTablePrefix();
        String query = "SELECT id FROM " + tablePrefix + "chests " +
                "WHERE world=? AND x=? AND y=? AND z=?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            
            pstmt.setString(1, loc.getWorld().getName());
            pstmt.setInt(2, loc.getBlockX());
            pstmt.setInt(3, loc.getBlockY());
            pstmt.setInt(4, loc.getBlockZ());
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
            
        } catch (SQLException e) {
            plugin.getLogger().warning("Fehler beim Abrufen der Chest-ID!");
        }
        
        return -1;
    }

    /**
     * Schließe Connection Pool
     */
    public void shutdown() {
        if (dataSource != null) {
            dataSource.close();
            plugin.getLogger().info("✓ MySQL Connection Pool geschlossen!");
        }
    }
}
