package dk.ggsmp.mines.hook;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import dk.ggsmp.mines.BennetsenMines;
import dk.ggsmp.mines.mine.Mine;
import dk.ggsmp.mines.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Handles integration with FancyHolograms API.
 * Automatically spawns, refreshes, and deletes mine status holograms.
 */
public class HologramHook {

    private final BennetsenMines plugin;
    private final boolean available;

    public HologramHook(BennetsenMines plugin) {
        this.plugin = plugin;
        this.available = Bukkit.getPluginManager().isPluginEnabled("FancyHolograms");

        if (available) {
            plugin.getLogger().info("FancyHolograms integration enabled.");
        } else {
            plugin.getLogger().warning("FancyHolograms was not found. Hologram displays will be disabled.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Updates or creates the two-line countdown hologram for the given mine.
     * Line 1: Mine's custom name (e.g., '&a&lG&G Mines')
     * Line 2: Live countdown (e.g., '&7Resets in: &f%time%')
     *
     * @param mine The target mine.
     */
    public void updateOrCreateHologram(Mine mine) {
        if (!available) {
            return;
        }

        Location loc = mine.getHologramLocation();
        if (loc == null || loc.getWorld() == null) {
            return;
        }

        try {
            HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
            String holoName = mine.getHologramName();

            // Prepare the 2 lines of text
            String line1 = ChatUtil.color(mine.getDisplayName());
            String line2 = ChatUtil.color("&7Resets in: &f" + mine.getFormattedTime());
            List<String> lines = List.of(line1, line2);

            Optional<Hologram> optionalHolo = manager.getHologram(holoName);

            if (optionalHolo.isPresent()) {
                Hologram hologram = optionalHolo.get();
                if (hologram.getData() instanceof TextHologramData textData) {
                    textData.setText(lines);

                    // If location changed, update hologram position
                    if (!textData.getLocation().equals(loc)) {
                        textData.setLocation(loc);
                    }

                    hologram.queueUpdate();
                }
            } else {
                // Create new text hologram
                TextHologramData data = new TextHologramData(holoName, loc);
                data.setText(lines);

                Hologram hologram = manager.create(data);
                manager.addHologram(hologram);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to update FancyHologram for mine '" + mine.getId() + "': " + e.getMessage());
        }
    }

    /**
     * Removes the FancyHolograms display associated with a mine.
     *
     * @param mine The target mine.
     */
    public void removeHologram(Mine mine) {
        if (!available) {
            return;
        }

        try {
            HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
            manager.getHologram(mine.getHologramName()).ifPresent(manager::removeHologram);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to remove FancyHologram for mine '" + mine.getId() + "': " + e.getMessage());
        }
    }
}

