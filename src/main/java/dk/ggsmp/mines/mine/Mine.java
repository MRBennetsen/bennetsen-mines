package dk.ggsmp.mines.mine;

import dk.ggsmp.mines.BennetsenMines;
import dk.ggsmp.mines.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents a discrete mine region with defined boundaries, block composition,
 * reset countdown schedule, and optional FancyHolograms display.
 */
public class Mine {

    private final String id;
    private String displayName;
    private String worldName;

    // Cuboid boundary coordinates
    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;

    // Composition: Material -> Percentage (0.0 to 100.0)
    private final Map<Material, Double> composition = new LinkedHashMap<>();

    // Timer settings
    private int resetInterval; // in seconds
    private int remainingSeconds;

    // Optional Hologram position
    private Location hologramLocation;

    // Atomic guard to prevent concurrent duplicate resets
    private final AtomicBoolean resetting = new AtomicBoolean(false);

    /**
     * Constructs a new Mine instance.
     *
     * @param id            Unique identifier for the mine (e.g. "quarry").
     * @param displayName   Visual display name supporting color codes (e.g. "&a&lG&G Mines").
     * @param worldName     Name of the Bukkit world.
     * @param x1            First corner X.
     * @param y1            First corner Y.
     * @param z1            First corner Z.
     * @param x2            Second corner X.
     * @param y2            Second corner Y.
     * @param z2            Second corner Z.
     * @param resetInterval Reset period in seconds (e.g. 600 for 10 minutes).
     */
    public Mine(String id, String displayName, String worldName,
                int x1, int y1, int z1, int x2, int y2, int z2,
                int resetInterval) {
        this.id = id.toLowerCase();
        this.displayName = displayName != null ? displayName : "&a&l" + id;
        this.worldName = worldName;
        setBounds(x1, y1, z1, x2, y2, z2);
        this.resetInterval = resetInterval > 0 ? resetInterval : 600;
        this.remainingSeconds = this.resetInterval;
    }

    /**
     * Sets and normalizes the cuboid boundaries.
     */
    public void setBounds(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    /**
     * Decrements the remaining countdown by one second.
     * When it hits zero, triggers an asynchronous reset.
     * Updates the FancyHolograms display accordingly.
     *
     * @param plugin The main plugin instance.
     */
    public void tickCountdown(BennetsenMines plugin) {
        if (remainingSeconds > 0) {
            remainingSeconds--;
        }

        if (remainingSeconds <= 0) {
            reset(plugin);
            remainingSeconds = resetInterval;
        }

        // Keep hologram live countdown updated
        updateHologram(plugin);
    }

    /**
     * Executes the mine block reset asynchronously via FastAsyncWorldEdit.
     *
     * @param plugin The main plugin instance.
     */
    public void reset(BennetsenMines plugin) {
        if (!resetting.compareAndSet(false, true)) {
            // Already performing reset
            return;
        }

        plugin.getWorldEditHook().resetMineAsync(this).whenComplete((success, throwable) -> {
            resetting.set(false);
            if (throwable != null) {
                plugin.getLogger().severe("Failed to reset mine '" + id + "': " + throwable.getMessage());
            } else {
                plugin.getLogger().info("Mine '" + id + "' has successfully been reset via FAWE.");
            }
            // Update hologram after reset
            updateHologram(plugin);
        });
    }

    /**
     * Updates or creates the FancyHolograms display for this mine if a location is set.
     *
     * @param plugin The main plugin instance.
     */
    public void updateHologram(BennetsenMines plugin) {
        if (hologramLocation == null) {
            return;
        }
        plugin.getHologramHook().updateOrCreateHologram(this);
    }

    /**
     * Gets the unique identifier of the mine.
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the formatted display name.
     */
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    /**
     * Returns the total volume in blocks.
     */
    public int getVolume() {
        return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    /**
     * Checks if a location is inside the cuboid region of this mine.
     */
    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        if (!loc.getWorld().getName().equalsIgnoreCase(worldName)) {
            return false;
        }
        return loc.getBlockX() >= minX && loc.getBlockX() <= maxX
                && loc.getBlockY() >= minY && loc.getBlockY() <= maxY
                && loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ;
    }

    /**
     * Gets an unmodifiable view of the block composition.
     */
    public Map<Material, Double> getComposition() {
        return Collections.unmodifiableMap(composition);
    }

    /**
     * Replaces the composition map.
     */
    public void setComposition(Map<Material, Double> newComposition) {
        composition.clear();
        if (newComposition != null) {
            composition.putAll(newComposition);
        }
    }

    /**
     * Adds or updates a block type's percentage.
     */
    public void addBlock(Material material, double percentage) {
        if (percentage <= 0.0) {
            composition.remove(material);
        } else {
            composition.put(material, percentage);
        }
    }

    /**
     * Removes a block type from composition.
     */
    public void removeBlock(Material material) {
        composition.remove(material);
    }

    /**
     * Calculates the sum of all block percentages in this mine.
     */
    public double getTotalPercentage() {
        double total = 0.0;
        for (double val : composition.values()) {
            total += val;
        }
        return total;
    }

    public int getResetInterval() {
        return resetInterval;
    }

    public void setResetInterval(int resetInterval) {
        this.resetInterval = Math.max(1, resetInterval);
        if (this.remainingSeconds > this.resetInterval) {
            this.remainingSeconds = this.resetInterval;
        }
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(int remainingSeconds) {
        this.remainingSeconds = Math.max(0, remainingSeconds);
    }

    /**
     * Gets the formatted time countdown string (e.g. "09:45" or "01:30:00").
     */
    public String getFormattedTime() {
        return ChatUtil.formatTime(remainingSeconds);
    }

    public Location getHologramLocation() {
        return hologramLocation;
    }

    public void setHologramLocation(Location hologramLocation) {
        this.hologramLocation = hologramLocation;
    }

    public boolean hasHologram() {
        return hologramLocation != null;
    }

    /**
     * Gets the standardized FancyHolograms identifier for this mine.
     */
    public String getHologramName() {
        return "bennetsen_mine_" + id;
    }

    public boolean isResetting() {
        return resetting.get();
    }

    /**
     * Deserializes a Mine object from a YAML ConfigurationSection.
     *
     * @param id      The mine key.
     * @param section The ConfigurationSection in mines.yml.
     * @return Constructed Mine instance.
     */
    public static Mine deserialize(String id, ConfigurationSection section) {
        String displayName = section.getString("display-name", "&a&l" + id);
        String world = section.getString("world", "world");

        int minX = section.getInt("region.min.x", 0);
        int minY = section.getInt("region.min.y", 0);
        int minZ = section.getInt("region.min.z", 0);
        int maxX = section.getInt("region.max.x", 0);
        int maxY = section.getInt("region.max.y", 0);
        int maxZ = section.getInt("region.max.z", 0);

        int interval = section.getInt("reset-interval", 600);

        Mine mine = new Mine(id, displayName, world, minX, minY, minZ, maxX, maxY, maxZ, interval);

        // Load block composition
        ConfigurationSection compSection = section.getConfigurationSection("composition");
        if (compSection != null) {
            for (String matKey : compSection.getKeys(false)) {
                Material mat = Material.matchMaterial(matKey);
                if (mat != null) {
                    mine.addBlock(mat, compSection.getDouble(matKey, 0.0));
                }
            }
        }

        // Load hologram position if configured
        if (section.isConfigurationSection("hologram")) {
            ConfigurationSection holoSection = section.getConfigurationSection("hologram");
            String holoWorldName = holoSection.getString("world", world);
            World holoWorld = Bukkit.getWorld(holoWorldName);
            double x = holoSection.getDouble("x");
            double y = holoSection.getDouble("y");
            double z = holoSection.getDouble("z");
            float yaw = (float) holoSection.getDouble("yaw", 0.0);
            float pitch = (float) holoSection.getDouble("pitch", 0.0);

            Location loc = new Location(holoWorld, x, y, z, yaw, pitch);
            mine.setHologramLocation(loc);
        }

        return mine;
    }

    /**
     * Serializes this Mine into the given ConfigurationSection.
     *
     * @param section The ConfigurationSection in mines.yml.
     */
    public void serialize(ConfigurationSection section) {
        section.set("display-name", displayName);
        section.set("world", worldName);

        section.set("region.min.x", minX);
        section.set("region.min.y", minY);
        section.set("region.min.z", minZ);
        section.set("region.max.x", maxX);
        section.set("region.max.y", maxY);
        section.set("region.max.z", maxZ);

        section.set("reset-interval", resetInterval);

        // Save composition
        section.set("composition", null); // clear previous
        for (Map.Entry<Material, Double> entry : composition.entrySet()) {
            section.set("composition." + entry.getKey().name(), entry.getValue());
        }

        // Save hologram position
        if (hologramLocation != null && hologramLocation.getWorld() != null) {
            section.set("hologram.world", hologramLocation.getWorld().getName());
            section.set("hologram.x", hologramLocation.getX());
            section.set("hologram.y", hologramLocation.getY());
            section.set("hologram.z", hologramLocation.getZ());
            section.set("hologram.yaw", hologramLocation.getYaw());
            section.set("hologram.pitch", hologramLocation.getPitch());
        } else {
            section.set("hologram", null);
        }
    }
}

