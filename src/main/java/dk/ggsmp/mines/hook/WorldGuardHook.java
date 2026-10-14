package dk.ggsmp.mines.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import dk.ggsmp.mines.BennetsenMines;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * Handles integration with WorldGuard 7.x.
 * Used for localized player notifications and fallback evacuation spawn points.
 */
public class WorldGuardHook {

    private final BennetsenMines plugin;
    private final boolean available;

    public WorldGuardHook(BennetsenMines plugin) {
        this.plugin = plugin;
        this.available = Bukkit.getPluginManager().isPluginEnabled("WorldGuard");

        if (available) {
            plugin.getLogger().info("WorldGuard integration enabled.");
        } else {
            plugin.getLogger().info("WorldGuard not detected; localized region queries will use mine cuboid coordinates.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Checks if a specific player is inside a WorldGuard region.
     *
     * @param player     The player to check.
     * @param regionName The WorldGuard region identifier.
     * @return true if the player is within the region.
     */
    public boolean isPlayerInRegion(Player player, String regionName) {
        if (!available || player == null || regionName == null) {
            return false;
        }

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager manager = container.get(BukkitAdapter.adapt(player.getWorld()));
            if (manager == null) {
                return false;
            }

            ProtectedRegion region = manager.getRegion(regionName);
            if (region == null) {
                return false;
            }

            return region.contains(BukkitAdapter.asBlockVector(player.getLocation()));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error checking WorldGuard region for player " + player.getName(), e);
            return false;
        }
    }

    /**
     * Gets all online players currently inside a WorldGuard region.
     *
     * @param world      The Bukkit world.
     * @param regionName The region name.
     * @return List of players currently in that region.
     */
    public List<Player> getPlayersInRegion(World world, String regionName) {
        if (!available || world == null || regionName == null) {
            return Collections.emptyList();
        }

        List<Player> result = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            if (isPlayerInRegion(player, regionName)) {
                result.add(player);
            }
        }
        return result;
    }

    /**
     * Retrieves the teleport or spawn location configured on a WorldGuard region, if any.
     *
     * @param world      The Bukkit world.
     * @param regionName The WorldGuard region name.
     * @return Bukkit Location or null.
     */
    public Location getRegionTeleportLocation(World world, String regionName) {
        if (!available || world == null || regionName == null) {
            return null;
        }

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager manager = container.get(BukkitAdapter.adapt(world));
            if (manager == null) {
                return null;
            }

            ProtectedRegion region = manager.getRegion(regionName);
            if (region == null) {
                return null;
            }

            com.sk89q.worldedit.util.Location teleLoc = region.getFlag(Flags.TELE_LOC);
            if (teleLoc == null) {
                teleLoc = region.getFlag(Flags.SPAWN_LOC);
            }

            if (teleLoc != null) {
                return BukkitAdapter.adapt(world, teleLoc);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error getting teleport location for WorldGuard region " + regionName, e);
        }

        return null;
    }

    /**
     * Checks if a WorldGuard region exists in the given world.
     */
    public boolean regionExists(World world, String regionName) {
        if (!available || world == null || regionName == null) {
            return false;
        }

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager manager = container.get(BukkitAdapter.adapt(world));
            return manager != null && manager.hasRegion(regionName);
        } catch (Exception e) {
            return false;
        }
    }
}

