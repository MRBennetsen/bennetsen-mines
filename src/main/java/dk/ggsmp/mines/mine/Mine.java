package dk.ggsmp.mines.mine;

import dk.ggsmp.mines.BennetsenMines;
import dk.ggsmp.mines.hook.WorldGuardHook;
import dk.ggsmp.mines.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents a discrete mine region with defined boundaries, block composition,
 * reset countdown schedule, safe evacuation point, and localized notifications.
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

    // Optional Hologram position & custom lines
    private Location hologramLocation;
    private final List<String> hologramLines = new ArrayList<>();

    // Safe Evacuation Point (set via /bm setspawn)
    private Location spawnLocation;

    // Optional WorldGuard region identifier for localized notifications
    private String worldGuardRegion;

    // Atomic guard to prevent concurrent duplicate resets
    private final AtomicBoolean resetting = new AtomicBoolean(false);

    /**
     * Constructs a new Mine instance.
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
     * Decrements countdown, sends localized warning alerts to players in the mine's zone,
     * triggers resets when expired, and updates live holograms.
     *
     * @param plugin The main plugin instance.
     */
    public void tickCountdown(BennetsenMines plugin) {
        if (remainingSeconds > 0) {
            remainingSeconds--;
        }

        // Localized warning alerts for players in the mine's zone (no global spam)
        sendWarningAlerts(plugin, remainingSeconds);

        if (remainingSeconds <= 0) {
            reset(plugin);
            remainingSeconds = resetInterval;
        }

        // Keep hologram live countdown updated
        updateHologram(plugin);
    }

    /**
     * Executes the mine block reset asynchronously via FastAsyncWorldEdit.
     * Evacuates any players inside the mine cuboid to prevent suffocation.
     *
     * @param plugin The main plugin instance.
     */
    public void reset(BennetsenMines plugin) {
        if (!resetting.compareAndSet(false, true)) {
            // Already performing reset
            return;
        }

        // 1. Evacuate players inside the mine to prevent suffocation
        evacuatePlayers(plugin);

        // 2. Play sound & title alert for players in the localized notification zone
        for (Player p : getPlayersInNotificationZone(plugin)) {
            p.sendTitle(ChatUtil.color("&a&lMINE RESET"), ChatUtil.color("&7Ores replenished!"), 10, 30, 10);
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.2f);
        }

        // 3. Perform asynchronous FAWE replacement
        plugin.getWorldEditHook().resetMineAsync(this).whenComplete((success, throwable) -> {
            resetting.set(false);
            if (throwable != null) {
                plugin.getLogger().severe("Failed to reset mine '" + id + "': " + throwable.getMessage());
            } else {
                plugin.getLogger().info("Mine '" + id + "' has successfully been reset via FAWE.");
            }
            updateHologram(plugin);
        });
    }

    /**
     * Safely evacuates any players currently inside the mine cuboid to prevent suffocation.
     * Never teleports players into the mine - only extracts them on reset.
     */
    public void evacuatePlayers(BennetsenMines plugin) {
        World world = getWorld();
        if (world == null) return;

        Location destination = getSafeEvacuationLocation(plugin);

        for (Player player : world.getPlayers()) {
            if (contains(player.getLocation())) {
                player.teleportAsync(destination).thenAccept(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        player.sendTitle(ChatUtil.color("&c&lMINE RESET"), ChatUtil.color("&7Evacuated to safe ground!"), 10, 40, 10);
                        player.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                        ChatUtil.sendMessage(player, "&cYou have been safely evacuated as the mine is resetting!");
                    }
                });
            }
        }
    }

    /**
     * Determines the safest evacuation destination.
     * Priority:
     * 1. Configured mine spawn point (/bm setspawn)
     * 2. WorldGuard region teleport flag (if linked)
     * 3. Centered position 1.5 blocks above the top of the mine cuboid
     */
    public Location getSafeEvacuationLocation(BennetsenMines plugin) {
        if (spawnLocation != null && spawnLocation.getWorld() != null) {
            return spawnLocation;
        }

        World world = getWorld();
        if (plugin.getWorldGuardHook() != null && plugin.getWorldGuardHook().isAvailable()) {
            String wg = (worldGuardRegion != null) ? worldGuardRegion : id;
            Location wgTele = plugin.getWorldGuardHook().getRegionTeleportLocation(world, wg);
            if (wgTele != null) {
                return wgTele;
            }
        }

        // Fallback: Centered 1.5 blocks above the top of the mine cuboid
        double centerX = (minX + maxX) / 2.0 + 0.5;
        double surfaceY = maxY + 1.5;
        double centerZ = (minZ + maxZ) / 2.0 + 0.5;
        return new Location(world, centerX, surfaceY, centerZ);
    }

    /**
     * Finds players located in this mine's localized notification zone.
     * If a WorldGuard region is associated, queries that region; otherwise
     * targets players inside the mine or within an 8-block proximity.
     */
    public List<Player> getPlayersInNotificationZone(BennetsenMines plugin) {
        World world = getWorld();
        if (world == null) return Collections.emptyList();

        if (plugin.getWorldGuardHook() != null && plugin.getWorldGuardHook().isAvailable()) {
            String wg = (worldGuardRegion != null) ? worldGuardRegion : id;
            if (plugin.getWorldGuardHook().regionExists(world, wg)) {
                return plugin.getWorldGuardHook().getPlayersInRegion(world, wg);
            }
        }

        // Fallback: Players inside or within 8 blocks of the mine
        List<Player> nearby = new ArrayList<>();
        int pMinX = minX - 8;
        int pMaxX = maxX + 8;
        int pMinY = minY - 4;
        int pMaxY = maxY + 8;
        int pMinZ = minZ - 8;
        int pMaxZ = maxZ + 8;

        for (Player p : world.getPlayers()) {
            Location loc = p.getLocation();
            if (loc.getBlockX() >= pMinX && loc.getBlockX() <= pMaxX
                    && loc.getBlockY() >= pMinY && loc.getBlockY() <= pMaxY
                    && loc.getBlockZ() >= pMinZ && loc.getBlockZ() <= pMaxZ) {
                nearby.add(p);
            }
        }
        return nearby;
    }

    /**
     * Sends localized warning ActionBars, Titles, and Sound alerts to players
     * in the mine's zone at configured intervals (60s, 30s, 10s, 5s-1s).
     */
    public void sendWarningAlerts(BennetsenMines plugin, int seconds) {
        if (seconds != 60 && seconds != 30 && seconds != 10 && seconds > 5) {
            return;
        }

        List<Player> players = getPlayersInNotificationZone(plugin);
        if (players.isEmpty()) {
            return;
        }

        String formattedTime = ChatUtil.formatTime(seconds);

        for (Player player : players) {
            player.sendActionBar(ChatUtil.toComponent("&e&l" + displayName + " &7resets in &f" + formattedTime));

            if (seconds <= 5 && seconds > 0) {
                float pitch = (float) (0.8 + (6 - seconds) * 0.2);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, pitch);

                if (seconds <= 3) {
                    player.sendTitle(ChatUtil.color("&c&l" + seconds), ChatUtil.color("&7Mine resetting soon!"), 0, 22, 3);
                }
            }
        }
    }

    /**
     * Updates or creates the FancyHolograms display for this mine if a location is set.
     */
    public void updateHologram(BennetsenMines plugin) {
        if (hologramLocation == null) {
            return;
        }
        plugin.getHologramHook().updateOrCreateHologram(this);
    }

    /**
     * Generates formatted lines for FancyHolograms, substituting placeholders.
     */
    public List<String> getRenderedHologramLines() {
        List<String> template = (hologramLines != null && !hologramLines.isEmpty())
                ? hologramLines
                : List.of("%name%", "&7Resets in: &f%time%");

        String status = resetting.get() ? "&eResetting..." : "&aActive";
        List<String> rendered = new ArrayList<>(template.size());

        for (String line : template) {
            String processed = line
                    .replace("%name%", displayName)
                    .replace("%id%", id)
                    .replace("%time%", getFormattedTime())
                    .replace("%seconds%", String.valueOf(remainingSeconds))
                    .replace("%volume%", String.valueOf(getVolume()))
                    .replace("%world%", worldName)
                    .replace("%status%", status);
            rendered.add(ChatUtil.color(processed));
        }

        return rendered;
    }

    public String getId() {
        return id;
    }

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

    public int getVolume() {
        return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

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

    public Map<Material, Double> getComposition() {
        return Collections.unmodifiableMap(composition);
    }

    public void setComposition(Map<Material, Double> newComposition) {
        composition.clear();
        if (newComposition != null) {
            composition.putAll(newComposition);
        }
    }

    public void addBlock(Material material, double percentage) {
        if (percentage <= 0.0) {
            composition.remove(material);
        } else {
            composition.put(material, percentage);
        }
    }

    public void removeBlock(Material material) {
        composition.remove(material);
    }

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

    public List<String> getHologramLines() {
        return Collections.unmodifiableList(hologramLines);
    }

    public void setHologramLines(List<String> lines) {
        hologramLines.clear();
        if (lines != null) {
            hologramLines.addAll(lines);
        }
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public void setSpawnLocation(Location spawnLocation) {
        this.spawnLocation = spawnLocation;
    }

    public boolean hasSpawnLocation() {
        return spawnLocation != null;
    }

    public String getWorldGuardRegion() {
        return worldGuardRegion;
    }

    public void setWorldGuardRegion(String worldGuardRegion) {
        this.worldGuardRegion = (worldGuardRegion != null && !worldGuardRegion.isBlank()) ? worldGuardRegion : null;
    }

    public String getHologramName() {
        return "bennetsen_mine_" + id;
    }

    public boolean isResetting() {
        return resetting.get();
    }

    /**
     * Deserializes a Mine object from a YAML ConfigurationSection.
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

        // Load hologram position and custom lines
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

            if (holoSection.isList("lines")) {
                mine.setHologramLines(holoSection.getStringList("lines"));
            }
        }

        // Load evacuation spawn location
        if (section.isConfigurationSection("spawn")) {
            ConfigurationSection spawnSec = section.getConfigurationSection("spawn");
            String spawnWorldName = spawnSec.getString("world", world);
            World spawnWorld = Bukkit.getWorld(spawnWorldName);
            double x = spawnSec.getDouble("x");
            double y = spawnSec.getDouble("y");
            double z = spawnSec.getDouble("z");
            float yaw = (float) spawnSec.getDouble("yaw", 0.0);
            float pitch = (float) spawnSec.getDouble("pitch", 0.0);

            mine.setSpawnLocation(new Location(spawnWorld, x, y, z, yaw, pitch));
        }

        // Load WorldGuard region linkage
        if (section.isString("worldguard-region")) {
            mine.setWorldGuardRegion(section.getString("worldguard-region"));
        }

        return mine;
    }

    /**
     * Serializes this Mine into the given ConfigurationSection.
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
        section.set("composition", null);
        for (Map.Entry<Material, Double> entry : composition.entrySet()) {
            section.set("composition." + entry.getKey().name(), entry.getValue());
        }

        // Save hologram position and custom lines
        if (hologramLocation != null && hologramLocation.getWorld() != null) {
            section.set("hologram.world", hologramLocation.getWorld().getName());
            section.set("hologram.x", hologramLocation.getX());
            section.set("hologram.y", hologramLocation.getY());
            section.set("hologram.z", hologramLocation.getZ());
            section.set("hologram.yaw", hologramLocation.getYaw());
            section.set("hologram.pitch", hologramLocation.getPitch());
            if (!hologramLines.isEmpty()) {
                section.set("hologram.lines", hologramLines);
            } else {
                section.set("hologram.lines", null);
            }
        } else {
            section.set("hologram", null);
        }

        // Save evacuation spawn position
        if (spawnLocation != null && spawnLocation.getWorld() != null) {
            section.set("spawn.world", spawnLocation.getWorld().getName());
            section.set("spawn.x", spawnLocation.getX());
            section.set("spawn.y", spawnLocation.getY());
            section.set("spawn.z", spawnLocation.getZ());
            section.set("spawn.yaw", spawnLocation.getYaw());
            section.set("spawn.pitch", spawnLocation.getPitch());
        } else {
            section.set("spawn", null);
        }

        // Save WorldGuard region linkage
        section.set("worldguard-region", worldGuardRegion);
    }
}
