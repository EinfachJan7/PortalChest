package dev.jankr.portalchest.commands;

import dev.jankr.portalchest.PortalChestPlugin;
import dev.jankr.portalchest.model.PortalChest;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PortalChestCommand implements CommandExecutor, TabCompleter {

    private final PortalChestPlugin plugin;

    public PortalChestCommand(PortalChestPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        // Reload ist Admin-Command und erlaubt für Console
        if (args[0].equalsIgnoreCase("reload")) {
            if (!(sender.hasPermission("portalchest.admin") || sender == Bukkit.getConsoleSender())) {
                sender.sendMessage(plugin.getDisplayManager().parseMessage("<red>Keine Berechtigung!"));
                return true;
            }
            return cmdReload(sender);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl ist nur für Spieler!");
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "trust" -> cmdTrust(player, args);
            case "untrust" -> cmdUntrust(player, args);
            case "unlink" -> cmdUnlink(player, args);
            case "list" -> cmdList(player, args);
            case "info" -> cmdInfo(player, args);
            case "give" -> cmdGive(player, args);
            case "reset" -> cmdReset(player, args);
            case "exit" -> cmdExit(player);
            case "help" -> {
                showHelp(player);
                yield true;
            }
            default -> {
                showHelp(player);
                yield true;
            }
        };
    }

    private boolean cmdTrust(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<color:#FF6B6B>Nutze: /portalchest trust <spieler></color>"
            ));
            return true;
        }

        org.bukkit.Location lastClick = plugin.getDataManager().getLastClicked(player.getUniqueId());
        if (lastClick == null) {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    plugin.getConfigManager().getMessage("trust.not-looking-at-chest")
            ));
            return true;
        }

        PortalChest chest = plugin.getDataManager().getChest(lastClick);
        if (chest == null) {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    plugin.getConfigManager().getMessage("error.chest-not-found")
            ));
            return true;
        }

        if (!chest.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    plugin.getConfigManager().getMessage("trust.not-your-chest")
            ));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    plugin.getConfigManager().getMessage("general.player-not-found")
            ));
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    plugin.getConfigManager().getMessage("general.cannot-trust-yourself")
            ));
            return true;
        }

        chest.addTrusted(target.getUniqueId());
        if (chest.getLinkedChest() != null) {
            chest.getLinkedChest().addTrusted(target.getUniqueId());
        }
        plugin.getDataManager().saveData();

        String msg = plugin.getConfigManager().getMessage("trust.trust-added").replace("%player%", target.getName());
        player.sendMessage(plugin.getDisplayManager().parseMessage(msg));
        String targetMsg = plugin.getConfigManager().getMessage("trust.trust-added-target").replace("%player%", player.getName());
        target.sendMessage(plugin.getDisplayManager().parseMessage(targetMsg));
        
        plugin.getSoundManager().playTeleportSoundForPlayer(player);
        plugin.getSoundManager().playTeleportSoundForPlayer(target);

        return true;
    }

    private boolean cmdUntrust(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<color:#FF6B6B>Nutze: /portalchest untrust <spieler></color>"
            ));
            return true;
        }

        org.bukkit.Location lastClick = plugin.getDataManager().getLastClicked(player.getUniqueId());
        if (lastClick == null) {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<color:#FF6B6B>Rechtsklicke zuerst auf deine Portal Chest!</color>"
            ));
            return true;
        }

        PortalChest chest = plugin.getDataManager().getChest(lastClick);
        if (chest == null) {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<color:#FF6B6B>Die Chest existiert nicht mehr!</color>"
            ));
            return true;
        }

        if (!chest.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<color:#FF6B6B>Das ist nicht deine Chest!</color>"
            ));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<color:#FF6B6B>Spieler nicht gefunden!</color>"
            ));
            return true;
        }

        chest.removeTrusted(target.getUniqueId());
        if (chest.getLinkedChest() != null) {
            chest.getLinkedChest().removeTrusted(target.getUniqueId());
        }
        plugin.getDataManager().saveData();

        player.sendMessage(plugin.getDisplayManager().parseMessage(
                "<color:#00FF00>" + target.getName() + " hat keinen Zugriff mehr!</color>"
        ));
        target.sendMessage(plugin.getDisplayManager().parseMessage(
                "<color:#FF6B6B>" + player.getName() + " hat dir den Zugriff auf eine Portal Chest entzogen!</color>"
        ));

        return true;
    }

    private boolean cmdUnlink(Player player, String[] args) {
        player.sendMessage(plugin.getDisplayManager().parseMessage(
                "<color:#FFA500>Auswahl zurückgesetzt!</color>"
        ));
        return true;
    }

    private boolean cmdList(Player player, String[] args) {
        player.sendMessage(plugin.getDisplayManager().parseMessage(
                "<color:#FFD700>Alle Portal Chests:</color>"
        ));

        int count = 0;
        for (PortalChest chest : plugin.getDataManager().getAllChests()) {
            count++;
            Player owner = Bukkit.getPlayer(chest.getOwner());
            String ownerName = owner != null ? owner.getName() : "Unbekannt";
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<color:#B0B0B0>- <color:#FFD700>" + chest.getLocation().getBlockX() + ", " +
                    chest.getLocation().getBlockY() + ", " + chest.getLocation().getBlockZ() +
                    " <color:#B0B0B0>(" + ownerName + ", " + chest.getType() +
                    ", Lvl " + chest.getUpgradeLevel() + ")</color>"
            ));
        }

        if (count == 0) {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<color:#FFA500>Keine Portal Chests gefunden!</color>"
            ));
        }

        return true;
    }

    private boolean cmdInfo(Player player, String[] args) {
        Player target = player;
        if (args.length > 1) {
            if (!player.hasPermission("portalchest.admin")) {
                player.sendMessage(plugin.getDisplayManager().parseMessage(
                        "<color:#FF6B6B>Keine Berechtigung!</color>"
                ));
                return true;
            }
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(plugin.getDisplayManager().parseMessage(
                        "<color:#FF6B6B>Spieler nicht gefunden!</color>"
                ));
                return true;
            }
        }

        long connections = plugin.getDataManager().getPlayerChests(target.getUniqueId()).stream()
                .filter(c -> c.getType() == PortalChest.ChestType.SENDER).count();
        long maxConnections = plugin.getConfigManager().getMaxConnections();

        player.sendMessage(plugin.getDisplayManager().parseMessage(
                "<color:#FFD700>Info für " + target.getName() + ":</color>"
        ));
        player.sendMessage(plugin.getDisplayManager().parseMessage(
                "<color:#B0B0B0>Verbindungen: <color:#00FF00>" + connections +
                "<color:#B0B0B0>/" + maxConnections + "</color>"
        ));
        player.sendMessage(plugin.getDisplayManager().parseMessage(
                "<color:#B0B0B0>Verfügbar: <color:#FFD700>" + (maxConnections - connections) + "</color>"
        ));

        return true;
    }

    private boolean cmdGive(Player player, String[] args) {
        if (!player.hasPermission("portalchest.admin")) {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<color:#FF6B6B>Keine Berechtigung!</color>"
            ));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<color:#FF6B6B>Nutze: /portalchest give <spieler> [level]</color>"
            ));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<color:#FF6B6B>Spieler nicht gefunden!</color>"
            ));
            return true;
        }

        int maxLevels = plugin.getConfigManager().getMaxLevels();
        int level = 1;
        if (args.length > 2) {
            try {
                level = Integer.parseInt(args[2]);
                if (level < 1 || level > maxLevels) {
                    player.sendMessage(plugin.getDisplayManager().parseMessage(
                            "<color:#FF6B6B>Level muss 1-" + maxLevels + " sein!</color>"
                    ));
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(plugin.getDisplayManager().parseMessage(
                        "<color:#FF6B6B>Ungültiges Level!</color>"
                ));
                return true;
            }
        }

        // Nutze die gleiche Logik wie dropChestWithLevel
        ItemStack chest = createChestItem(level);
        target.getInventory().addItem(chest);

        player.sendMessage(plugin.getDisplayManager().parseMessage(
                "<color:#00FF00>✓ Portal Chest Level " + level + " an " + target.getName() + " gegeben!</color>"
        ));
        target.sendMessage(plugin.getDisplayManager().parseMessage(
                "<color:#00FF00>✓ Du hast eine Portal Chest Level " + level + " erhalten!</color>"
        ));

        return true;
    }

    private ItemStack createChestItem(int level) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) meta = Bukkit.getItemFactory().getItemMeta(Material.CHEST);

        int maxLevels = plugin.getConfigManager().getMaxLevels();
        if (level < 1 || level > maxLevels) level = 1;

        int maxItems = plugin.getConfigManager().getMaxItemsForLevel(level);
        String transferTime = plugin.getConfigManager().getTransferSeconds();

        // Name aus Config mit Placeholders
        try {
            String chestName = plugin.getConfigManager().getChestName(level);
            if (chestName != null && !chestName.isEmpty()) {
                net.kyori.adventure.text.Component nameComponent = plugin.getDisplayManager().parseMessage(chestName);
                meta.displayName(nameComponent);
                plugin.getLogger().info("✓ Name gesetzt für Level " + level + ": " + chestName);
            } else {
                plugin.getLogger().warning("WARNUNG: Config-Name ist null oder leer für Level " + level);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("FEHLER beim Name-Setzen: " + e.getMessage());
        }

        // Lore aus Config mit Placeholders
        try {
            java.util.List<String> lorePaths = plugin.getConfigManager().getChestLore(level);
            if (lorePaths != null && !lorePaths.isEmpty()) {
                java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();

                for (String loreLine : lorePaths) {
                    if (loreLine == null || loreLine.isEmpty()) continue;
                    String processedLine = loreLine
                        .replace("%items%", String.valueOf(maxItems))
                        .replace("%time%", transferTime);
                    lore.add(plugin.getDisplayManager().parseMessage(processedLine));
                }

                if (!lore.isEmpty()) {
                    meta.lore(lore);
                    plugin.getLogger().info("✓ Lore gesetzt für Level " + level + ": " + lore.size() + " Zeilen");
                } else {
                    plugin.getLogger().warning("Lore ist leer nach Verarbeitung für Level " + level);
                }
            } else {
                plugin.getLogger().warning("WARNUNG: Config-Lore ist null oder leer für Level " + level);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("FEHLER beim Lore-Setzen: " + e.getMessage());
            e.printStackTrace();
        }

        // NBT Tag
        try {
            meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "portal_chest_level"),
                PersistentDataType.INTEGER,
                level
            );
        } catch (Exception e) {
            plugin.getLogger().warning("FEHLER beim NBT-Tag-Setzen: " + e.getMessage());
        }

        item.setItemMeta(meta);
        return item;
    }

    private boolean cmdReset(Player player, String[] args) {
        if (!player.hasPermission("portalchest.admin")) {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<color:#FF6B6B>Keine Berechtigung!</color>"
            ));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<color:#FF6B6B>Nutze: /portalchest reset <spieler></color>"
            ));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<color:#FF6B6B>Spieler nicht gefunden!</color>"
            ));
            return true;
        }

        List<PortalChest> playerChests = plugin.getDataManager().getPlayerChests(target.getUniqueId());
        for (PortalChest chest : playerChests) {
            plugin.getDisplayManager().removeBlockDisplay(
                    plugin.getDataManager().getLocationKey(chest.getLocation())
            );
            plugin.getDataManager().unregisterChest(chest.getLocation());
        }

        player.sendMessage(plugin.getDisplayManager().parseMessage(
                "<color:#00FF00>Portal Chests von " + target.getName() + " zurückgesetzt!</color>"
        ));

        return true;
    }

    private boolean cmdReload(CommandSender sender) {
        sender.sendMessage("§8[§bPortalChest§8]§r §6Lade komplette Config neu...");
        
        // Alles neu laden (Config, Manager, Datenbank)
        plugin.reloadPluginConfig();
        
        sender.sendMessage("§8[§bPortalChest§8]§r §a✓ Config und alle Manager neu geladen!");
        
        return true;
    }

    private boolean cmdExit(Player player) {
        // Beende Linking-Modus wenn aktiv
        if (plugin.getChestPlaceListener().isInLinkingMode(player)) {
            plugin.getChestPlaceListener().exitLinkingMode(player);
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    plugin.getConfigManager().getMessage("linking-mode.exit-exec")
            ));
            return true;
        } else {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    plugin.getConfigManager().getMessage("linking-mode.not-in-mode")
            ));
            return true;
        }
    }

    private void showHelp(CommandSender sender) {
        if (sender instanceof Player player) {
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<color:#FFD700><bold>━━━ Portal Chest Befehle ━━━</bold></color>"
            ));
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<color:#FFD700>/portalchest trust <spieler> <color:#B0B0B0>- Spieler berechtigen</color>"
            ));
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<color:#FFD700>/portalchest untrust <spieler> <color:#B0B0B0>- Berechtigung entfernen</color>"
            ));
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<color:#FFD700>/portalchest unlink <color:#B0B0B0>- Auswahl aufheben</color>"
            ));
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<color:#FFD700>/portalchest list <color:#B0B0B0>- Alle Chests anzeigen</color>"
            ));
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<color:#FFD700>/portalchest info [spieler] <color:#B0B0B0>- Info anzeigen</color>"
            ));
            player.sendMessage(plugin.getDisplayManager().parseMessage(
                    "<color:#FFD700>/portalchest exit <color:#B0B0B0>- Linking-Modus beenden</color>"
            ));

            if (player.hasPermission("portalchest.admin")) {
                player.sendMessage(plugin.getDisplayManager().parseMessage(
                        "<color:#FF6B6B><bold>Admin-Befehle:</bold></color>"
                ));
                player.sendMessage(plugin.getDisplayManager().parseMessage(
                        "<color:#FFD700>/portalchest reset <spieler> <color:#B0B0B0>- Chests zurücksetzen</color>"
                ));
                player.sendMessage(plugin.getDisplayManager().parseMessage(
                        "<color:#FFD700>/portalchest give <spieler> [level] <color:#B0B0B0>- Chest geben</color>"
                ));
                player.sendMessage(plugin.getDisplayManager().parseMessage(
                        "<color:#FFD700>/portalchest reload <color:#B0B0B0>- Config neu laden</color>"
                ));
            }
        } else {
            sender.sendMessage("§6Portal Chest Befehle:");
            sender.sendMessage("§b/portalchest reload§r - Config neu laden");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subcommands = Arrays.asList(
                    "trust", "untrust", "unlink", "list", "info", "help", "exit"
            );
            
            if (sender.hasPermission("portalchest.admin")) {
                subcommands = new ArrayList<>(subcommands);
                subcommands.add("give");
                subcommands.add("reset");
                subcommands.add("reload");
            }
            
            String input = args[0].toLowerCase();
            for (String sub : subcommands) {
                if (sub.startsWith(input)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust") ||
                args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("info") ||
                args[0].equalsIgnoreCase("give")) {
                // Spieler-Vorschläge
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            // Level-Vorschläge (1-maxLevel)
            int maxLevels = plugin.getConfigManager().getMaxLevels();
            for (int i = 1; i <= maxLevels; i++) {
                if (String.valueOf(i).startsWith(args[2])) {
                    completions.add(String.valueOf(i));
                }
            }
        }

        return completions;
    }
}
