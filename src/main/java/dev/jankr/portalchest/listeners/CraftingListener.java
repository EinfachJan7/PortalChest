package dev.jankr.portalchest.listeners;

import dev.jankr.portalchest.PortalChestPlugin;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

public class CraftingListener implements Listener {

    private final PortalChestPlugin plugin;

    public CraftingListener(PortalChestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        ItemStack result = inventory.getResult();

        if (result == null || result.getType() != Material.BLAZE_ROD) {
            return;
        }

        // Prüfe ob dies die Portal Linker Recipe ist
        // Das Rezept hat: Ender Pearl + 2 Blaze Rods
        int blazeRods = 0;
        int enderPearls = 0;

        for (ItemStack item : inventory.getMatrix()) {
            if (item != null) {
                if (item.getType() == Material.BLAZE_ROD) {
                    blazeRods++;
                } else if (item.getType() == Material.ENDER_PEARL) {
                    enderPearls++;
                }
            }
        }

        // Wenn es die Portal Linker Recipe ist (2 Blaze Rods + 1 Ender Pearl)
        if (blazeRods == 2 && enderPearls == 1) {
            // Erstelle Portal Linker mit NBT-Tag
            ItemStack linkerItem = new ItemStack(Material.BLAZE_ROD, 1);
            ItemMeta meta = linkerItem.getItemMeta();

            if (meta != null) {
                // Name aus Config laden
                meta.displayName(plugin.getDisplayManager().parseMessage(
                        plugin.getConfigManager().getPortalLinkerName()
                ));
                
                // Lore aus Config laden
                java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
                for (String loreLine : plugin.getConfigManager().getPortalLinkerLore()) {
                    lore.add(plugin.getDisplayManager().parseMessage(loreLine));
                }
                meta.lore(lore);
                
                // NBT-Tag setzen
                meta.getPersistentDataContainer().set(
                        new NamespacedKey(plugin, "portal_linker"),
                        PersistentDataType.INTEGER,
                        1
                );
                
                linkerItem.setItemMeta(meta);
            }

            inventory.setResult(linkerItem);
        }
    }
}
