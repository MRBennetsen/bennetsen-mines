package dk.ggsmp.mines.mine;

import dk.ggsmp.mines.BennetsenMines;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages all active mines and persistence with the custom mines.yml file.
 */
public class MineManager {

    private final BennetsenMines plugin;
    private final File file;
    private FileConfiguration config;
    private final Map<String, Mine> mines = new ConcurrentHashMap<>();

    public MineManager(BennetsenMines plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mines.yml");
        loadMines();
    }

    /**
     * Loads or reloads all mine definitions from mines.yml.
     */
    public synchronized void loadMines() {
        mines.clear();

        if (!file.exists()) {
            plugin.saveResource("mines.yml", false);
        }

        this.config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("mines");

        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection mineSec = section.getConfigurationSection(key);
                if (mineSec != null) {
                    try {
                        Mine mine = Mine.deserialize(key, mineSec);
                        mines.put(mine.getId(), mine);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to load mine '" + key + "' from mines.yml", e);
                    }
                }
            }
        }

        plugin.getLogger().info("Successfully loaded " + mines.size() + " mine(s) from mines.yml.");

        // Initial hologram refresh for all loaded mines
        for (Mine mine : mines.values()) {
            mine.updateHologram(plugin);
        }
    }

    /**
     * Saves all in-memory mines back to mines.yml.
     */
    public synchronized void saveMines() {
        if (config == null) {
            config = new YamlConfiguration();
        }

        config.set("mines", null); // Clear current section

        for (Mine mine : mines.values()) {
            ConfigurationSection mineSec = config.createSection("mines." + mine.getId());
            mine.serialize(mineSec);
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save mines to mines.yml", e);
        }
    }

    /**
     * Retrieves a mine by its ID (case-insensitive).
     */
    public Mine getMine(String id) {
        if (id == null) return null;
        return mines.get(id.toLowerCase());
    }

    /**
     * Gets all currently loaded mines.
     */
    public Collection<Mine> getMines() {
        return Collections.unmodifiableCollection(mines.values());
    }

    /**
     * Registers a new mine and persists it to file.
     */
    public void addMine(Mine mine) {
        mines.put(mine.getId(), mine);
        mine.updateHologram(plugin);
        saveMines();
    }

    /**
     * Deletes a mine, removes its hologram, and saves changes.
     */
    public boolean deleteMine(String id) {
        Mine mine = mines.remove(id.toLowerCase());
        if (mine != null) {
            plugin.getHologramHook().removeHologram(mine);
            saveMines();
            return true;
        }
        return false;
    }

    /**
     * Checks if a mine with the given ID exists.
     */
    public boolean exists(String id) {
        return id != null && mines.containsKey(id.toLowerCase());
    }

    /**
     * Ticks countdown timers for all mines and updates their live holograms.
     * Invoked every second by the repeating task.
     */
    public void tickAll() {
        for (Mine mine : mines.values()) {
            try {
                mine.tickCountdown(plugin);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error ticking countdown for mine " + mine.getId(), e);
            }
        }
    }
}

