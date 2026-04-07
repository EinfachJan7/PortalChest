package dev.jankr.portalchest.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Method;

public class EconomyUtil {
    
    private static Object economy = null;
    private static boolean vaultAvailable = false;
    private static Method getBalance;
    private static Method withdrawPlayer;
    private static Method depositPlayer;
    private static Method currencyNamePlural;
    
    /**
     * Initialisiert den Economy-Provider von Vault (falls verfügbar)
     */
    public static void setupEconomy(JavaPlugin plugin) {
        try {
            // Prüfe ob Vault installiert ist
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
                plugin.getLogger().info("[PortalChest] Vault nicht gefunden - Economy-Support deaktiviert");
                vaultAvailable = false;
                return;
            }
            
            plugin.getLogger().info("[PortalChest] Vault Plugin gefunden - versuche Economy zu laden...");
            
            // Lade Economy-Klasse
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Class<?> serviceProviderClass = Class.forName("org.bukkit.plugin.RegisteredServiceProvider");
            
            // Hole die Registrierung vom Services Manager
            Method getRegistrationMethod = Bukkit.getServicesManager().getClass()
                .getMethod("getRegistration", Class.class);
            Object registration = getRegistrationMethod.invoke(Bukkit.getServicesManager(), economyClass);
            
            if (registration == null) {
                plugin.getLogger().warning("[PortalChest] Economy-Provider von Vault nicht gefunden! (Kein Economy Plugin registriert)");
                vaultAvailable = false;
                return;
            }
            
            // Hole die Provider-Instanz
            Method getProviderMethod = serviceProviderClass.getMethod("getProvider");
            economy = getProviderMethod.invoke(registration);
            
            if (economy == null) {
                plugin.getLogger().warning("[PortalChest] Economy-Instance konnte nicht geladen werden!");
                vaultAvailable = false;
                return;
            }
            
            // Cache Methods - Vault Economy nutzt OfflinePlayer, nicht Player!
            getBalance = economyClass.getMethod("getBalance", OfflinePlayer.class);
            withdrawPlayer = economyClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            depositPlayer = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            currencyNamePlural = economyClass.getMethod("currencyNamePlural");
            
            vaultAvailable = true;
            plugin.getLogger().info("[PortalChest] Vault Economy erfolgreich geladen! (Provider: " + economy.getClass().getName() + ")");
        } catch (Exception e) {
            vaultAvailable = false;
            plugin.getLogger().warning("[PortalChest] Vault Economy konnte nicht geladen werden: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Prüft, ob Vault Economy verfügbar ist
     */
    public static boolean isEconomyAvailable() {
        return vaultAvailable && economy != null;
    }
    
    /**
     * Gibt die Money des Spielers zurück
     */
    public static double getMoney(Player player) {
        if (!isEconomyAvailable()) {
            return 0;
        }
        try {
            Object result = getBalance.invoke(economy, (OfflinePlayer) player);
            double balance = result instanceof Number ? ((Number) result).doubleValue() : 0;
            return balance;
        } catch (Exception e) {
            Bukkit.getLogger().warning("[PortalChest] Vault getMoney Error: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Prüft, ob der Spieler genug Geld hat
     */
    public static boolean hasMoney(Player player, double amount) {
        return getMoney(player) >= amount;
    }
    /**
     * Entfernt Geld vom Spieler
     */
    public static boolean withdrawMoney(Player player, double amount) {
        if (!isEconomyAvailable()) {
            return false;
        }
        
        try {
            if (!hasMoney(player, amount)) {
                return false;
            }
            
            // Invoke withdrawPlayer - gibt EconomyResponse zurück
            Object response = withdrawPlayer.invoke(economy, (OfflinePlayer) player, amount);
            
            // Wenn Response existiert, versuche transactionSucceeded() zu prüfen
            if (response != null) {
                try {
                    // Versuche verschiedene mögliche Methoden-Namen
                    Method successMethod = null;
                    try {
                        successMethod = response.getClass().getMethod("transactionSucceeded");
                    } catch (NoSuchMethodException e) {
                        // Versuche alternative Namen
                        try {
                            successMethod = response.getClass().getMethod("isTransactionSuccessful");
                        } catch (NoSuchMethodException e2) {
                            // Fallback: wenn invoke() keine Exception wirft, gehen wir davon aus es war erfolgreich
                            return true;
                        }
                    }
                    
                    Object result = successMethod.invoke(response);
                    if (result instanceof Boolean) {
                        return (Boolean) result;
                    }
                    return true;
                } catch (Exception e) {
                    // Wenn transactionSucceeded() fehlschlägt, assume success wenn invoke() erfolgreich war
                    return true;
                }
            }
            // Wenn kein Response, aber keine Exception - erfolgreich
            return true;
        } catch (Exception e) {
            Bukkit.getLogger().warning("[PortalChest] Vault withdrawMoney Error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Gibt Geld an den Spieler
     */
    public static void depositMoney(Player player, double amount) {
        if (!isEconomyAvailable()) {
            return;
        }
        try {
            Object response = depositPlayer.invoke(economy, (OfflinePlayer) player, amount);
            
            // Nur auf LogLevel prüfen, nicht kritisch aufräumen
            if (response != null) {
                Method transactionSucceeded = response.getClass().getMethod("transactionSucceeded");
                transactionSucceeded.invoke(response);
            }
        } catch (Exception e) {
            // Ignorieren
        }
    }
    
    /**
     * Gibt den Namen der Economy-Währung zurück (z.B. "Euros", "Dollar")
     */
    public static String getCurrencyName() {
        if (!isEconomyAvailable()) {
            return "Money";
        }
        try {
            Object result = currencyNamePlural.invoke(economy);
            return result != null ? result.toString() : "Money";
        } catch (Exception e) {
            return "Money";
        }
    }
}
