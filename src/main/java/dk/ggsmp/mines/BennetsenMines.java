package dk.ggsmp.mines;

import dk.ggsmp.mines.command.MineCommand;
import dk.ggsmp.mines.hook.HologramHook;
import dk.ggsmp.mines.hook.WorldEditHook;
import dk.ggsmp.mines.mine.MineManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Main plugin class for bennetsen-mines.
 * Manages plugin lifecycle, integrations, commands, and the repeating timer task.
 */
public class BennetsenMines extends JavaPlugin {

    private static BennetsenMines instance;

    private MineManager mineManager;
    private WorldEditHook worldEditHook;
    private HologramHook hologramHook;

    private BukkitTask timerTask;
    private Object foliaTask; // Kept as Object for compatibility with standard Paper & Folia

    @Override
    public void onEnable() {
        instance = this;

        // Ensure plugin directory exists
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        getLogger().info("Initializing bennetsen-mines...");

        // Initialize hooks
        this.worldEditHook = new WorldEditHook(this);
        this.hologramHook = new HologramHook(this);

        // Initialize mine storage and manager
        this.mineManager = new MineManager(this);

        // Register commands (Modern Paper registerCommand with legacy Bukkit fallback)
        MineCommand cmd = new MineCommand(this);
        try {
            registerCommand("bm", "BennetsenMines management command", java.util.List.of("bennetsenmines", "bmines", "mrl"), cmd);
            getLogger().info("Registered command 'bm' via Paper command registry.");
        } catch (NoSuchMethodError | UnsupportedOperationException e) {
            try {
                PluginCommand command = getCommand("bm");
                if (command != null) {
                    command.setExecutor(cmd);
                    command.setTabCompleter(cmd);
                    getLogger().info("Registered command 'bm' via Bukkit PluginCommand.");
                }
            } catch (Throwable ignored) {
            }
        }

        // Start repeating 1-second countdown and update task
        startTimerTask();

        getLogger().info("bennetsen-mines has been successfully enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling bennetsen-mines...");

        // Stop timer task
        stopTimerTask();

        // Save state to mines.yml
        if (mineManager != null) {
            mineManager.saveMines();
        }

        instance = null;
        getLogger().info("bennetsen-mines has been successfully disabled.");
    }

    /**
     * Starts the repeating 1-second timer task that ticks mine countdowns,
     * triggers resets, and updates FancyHolograms.
     * Supports both modern Folia schedulers and standard Paper schedulers.
     */
    private void startTimerTask() {
        try {
            // Attempt Folia GlobalRegionScheduler (20 ticks = 1 second)
            this.foliaTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, task -> {
                if (mineManager != null) {
                    mineManager.tickAll();
                }
            }, 20L, 20L);
            getLogger().info("Started Folia GlobalRegionScheduler 1-second timer.");
        } catch (NoSuchMethodError | NoClassDefFoundError ignored) {
            // Standard Paper scheduler
            this.timerTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                if (mineManager != null) {
                    mineManager.tickAll();
                }
            }, 20L, 20L);
            getLogger().info("Started Paper BukkitScheduler 1-second timer.");
        }
    }

    /**
     * Safely cancels the active timer task on server stop or plugin disable.
     */
    private void stopTimerTask() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }

        if (foliaTask != null) {
            try {
                // io.papermc.paper.threadedregions.scheduler.ScheduledTask#cancel()
                foliaTask.getClass().getMethod("cancel").invoke(foliaTask);
            } catch (Exception ignored) {
            }
            foliaTask = null;
        }
    }

    public static BennetsenMines getInstance() {
        return instance;
    }

    public MineManager getMineManager() {
        return mineManager;
    }

    public WorldEditHook getWorldEditHook() {
        return worldEditHook;
    }

    public HologramHook getHologramHook() {
        return hologramHook;
    }
}

