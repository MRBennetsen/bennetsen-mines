package dk.ggsmp.mines.gui;

import dk.ggsmp.mines.BennetsenMines;
import dk.ggsmp.mines.mine.Mine;
import dk.ggsmp.mines.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Status-only GUI showing active mines, their reset countdowns, and compositions.
 * Strictly informational: no teleportation allowed.
 */
public class MineStatusGUI implements InventoryHolder, Listener {

    private final BennetsenMines plugin;
    private Inventory inventory;

    public MineStatusGUI(BennetsenMines plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the status GUI for a player.
     */
    public static void open(Player player, BennetsenMines plugin) {
        Collection<Mine> mines = plugin.getMineManager().getMines();
        int size = 27;
        if (mines.size() > 18) {
            size = 54;
        } else if (mines.size() > 9) {
            size = 36;
        }

        MineStatusGUI holder = new MineStatusGUI(plugin);
        Inventory inv = Bukkit.createInventory(holder, size, ChatUtil.color("&8Mine Status Overview"));
        holder.inventory = inv;

        // Fill background with tinted glass pane
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < size; i++) {
            inv.setItem(i, filler);
        }

        int slot = 0;
        for (Mine mine : mines) {
            if (slot >= size) break;

            Material icon = determineIcon(mine);
            String title = "&e&l" + mine.getId() + " &7(" + mine.getDisplayName() + "&7)";

            List<String> lore = new ArrayList<>();
            lore.add("&7Status: " + (mine.isResetting() ? "&eResetting..." : "&aActive"));
            lore.add("&7Next Reset: &e" + mine.getFormattedTime());
            lore.add("&7Reset Interval: &f" + mine.getResetInterval() + "s");
            lore.add("&7World: &f" + mine.getWorldName());
            lore.add("&7Size: &f" + mine.getVolume() + " blocks");
            lore.add("&8&m------------------------");
            lore.add("&6Ore Composition:");

            if (mine.getComposition().isEmpty()) {
                lore.add("  &8- &c100% STONE (default)");
            } else {
                for (Map.Entry<Material, Double> entry : mine.getComposition().entrySet()) {
                    lore.add("  &8- &f" + entry.getKey().name() + "&7: &e" + String.format("%.1f", entry.getValue()) + "%");
                }
            }

            lore.add("&8&m------------------------");
            lore.add("&8(Status info only - no teleport)");

            inv.setItem(slot++, createItem(icon, title, lore));
        }

        player.openInventory(inv);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof MineStatusGUI) {
            // Cancel all item movements - strictly informational
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MineStatusGUI) {
            event.setCancelled(true);
        }
    }

    private static Material determineIcon(Mine mine) {
        if (mine.getComposition().isEmpty()) {
            return Material.STONE;
        }

        // Pick the most valuable / highest percentage ore
        Material highest = Material.STONE;
        double maxPct = -1.0;

        for (Map.Entry<Material, Double> entry : mine.getComposition().entrySet()) {
            Material mat = entry.getKey();
            if (mat.name().contains("DIAMOND")) return mat;
            if (mat.name().contains("EMERALD")) return mat;
            if (mat.name().contains("GOLD")) return mat;
            if (mat.name().contains("IRON")) return mat;
            if (mat.name().contains("COAL")) return mat;

            if (entry.getValue() > maxPct) {
                maxPct = entry.getValue();
                highest = mat;
            }
        }
        return highest;
    }

    private static ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatUtil.color(name));
            if (lore != null) {
                List<String> colored = new ArrayList<>(lore.size());
                for (String l : lore) {
                    colored.add(ChatUtil.color(l));
                }
                meta.setLore(colored);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}

