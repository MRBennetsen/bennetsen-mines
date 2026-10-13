package dk.ggsmp.mines.hook;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.function.pattern.RandomPattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BlockType;
import dk.ggsmp.mines.BennetsenMines;
import dk.ggsmp.mines.mine.Mine;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Handles interactions with WorldEdit and FastAsyncWorldEdit (FAWE).
 * Provides player selection retrieval and asynchronous block population.
 */
public class WorldEditHook {

    private final BennetsenMines plugin;
    private final boolean available;

    public WorldEditHook(BennetsenMines plugin) {
        this.plugin = plugin;
        this.available = Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit")
                || Bukkit.getPluginManager().isPluginEnabled("WorldEdit");

        if (available) {
            plugin.getLogger().info("FastAsyncWorldEdit / WorldEdit integration enabled.");
        } else {
            plugin.getLogger().warning("Neither FastAsyncWorldEdit nor WorldEdit was found. Region resets will be unavailable.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Retrieves the current WorldEdit region selection for a player.
     *
     * @param player The player to query.
     * @return Selected Region, or null if selection is missing or incomplete.
     */
    public Region getPlayerSelection(Player player) {
        if (!available) {
            return null;
        }

        try {
            com.sk89q.worldedit.entity.Player actor = BukkitAdapter.adapt(player);
            LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
            if (session == null || session.getSelectionWorld() == null) {
                return null;
            }
            return session.getSelection(session.getSelectionWorld());
        } catch (IncompleteRegionException e) {
            return null;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error getting selection for " + player.getName(), e);
            return null;
        }
    }

    /**
     * Executes the mine block replacement asynchronously using FAWE.
     *
     * @param mine The mine to reset.
     * @return CompletableFuture completing when the edit session is flushed.
     */
    public CompletableFuture<Void> resetMineAsync(Mine mine) {
        return CompletableFuture.runAsync(() -> {
            if (!available) {
                plugin.getLogger().warning("Cannot reset mine '" + mine.getId() + "': FAWE/WorldEdit is not available.");
                return;
            }

            org.bukkit.World bukkitWorld = mine.getWorld();
            if (bukkitWorld == null) {
                plugin.getLogger().warning("Cannot reset mine '" + mine.getId() + "': World '" + mine.getWorldName() + "' is not loaded!");
                return;
            }

            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(bukkitWorld);
            BlockVector3 min = BlockVector3.at(mine.getMinX(), mine.getMinY(), mine.getMinZ());
            BlockVector3 max = BlockVector3.at(mine.getMaxX(), mine.getMaxY(), mine.getMaxZ());
            CuboidRegion region = new CuboidRegion(weWorld, min, max);

            // Construct composition pattern
            RandomPattern randomPattern = new RandomPattern();
            Map<Material, Double> composition = mine.getComposition();

            if (composition.isEmpty()) {
                // Default fallback pattern: 100% Stone
                BlockType stoneType = BukkitAdapter.asBlockType(Material.STONE);
                if (stoneType != null) {
                    randomPattern.add(stoneType.getDefaultState(), 1.0);
                }
            } else {
                for (Map.Entry<Material, Double> entry : composition.entrySet()) {
                    if (entry.getValue() <= 0.0) {
                        continue;
                    }
                    try {
                        BlockType blockType = BukkitAdapter.asBlockType(entry.getKey());
                        if (blockType != null) {
                            // Weight normalized based on percentage
                            randomPattern.add(blockType.getDefaultState(), entry.getValue() / 100.0);
                        }
                    } catch (Exception ex) {
                        plugin.getLogger().warning("Failed to map material " + entry.getKey() + " to FAWE BlockType: " + ex.getMessage());
                    }
                }
            }

            // Create FAWE EditSession and set blocks asynchronously
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
                editSession.setBlocks((Region) region, (com.sk89q.worldedit.function.pattern.Pattern) randomPattern);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Exception occurred while resetting mine '" + mine.getId() + "' via FAWE", e);
                throw new RuntimeException("FAWE reset error", e);
            }
        });
    }
}
