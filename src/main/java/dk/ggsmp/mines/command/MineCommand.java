package dk.ggsmp.mines.command;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import dk.ggsmp.mines.BennetsenMines;
import dk.ggsmp.mines.mine.Mine;
import dk.ggsmp.mines.util.ChatUtil;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Command executor and tab completer for the /bm command.
 */
public class MineCommand implements CommandExecutor, TabCompleter {

    private final BennetsenMines plugin;

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "redefine", "addblock", "removeblock",
            "setholo", "removeholo", "reset", "setinterval",
            "setname", "list", "info", "delete", "reload", "help"
    );

    public MineCommand(BennetsenMines plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("bennetsenmines.admin")) {
            ChatUtil.sendMessage(sender, "&cYou do not have permission to use this command.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "redefine" -> handleRedefine(sender, args);
            case "addblock" -> handleAddBlock(sender, args);
            case "removeblock" -> handleRemoveBlock(sender, args);
            case "setholo" -> handleSetHolo(sender, args);
            case "removeholo" -> handleRemoveHolo(sender, args);
            case "reset" -> handleReset(sender, args);
            case "setinterval" -> handleSetInterval(sender, args);
            case "setname" -> handleSetName(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "reload" -> handleReload(sender);
            default -> {
                ChatUtil.sendMessage(sender, "&cUnknown subcommand. Type &e/" + label + " help &cfor a list of commands.");
            }
        }

        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatUtil.sendMessage(sender, "&cOnly in-game players can create mines using WorldEdit selections.");
            return;
        }

        if (args.length < 2) {
            ChatUtil.sendMessage(sender, "&cUsage: /bm create <name> [displayName]");
            return;
        }

        String name = args[1].toLowerCase();
        if (plugin.getMineManager().exists(name)) {
            ChatUtil.sendMessage(sender, "&cA mine with the name '&e" + name + "&c' already exists.");
            return;
        }

        Region selection = plugin.getWorldEditHook().getPlayerSelection(player);
        if (selection == null) {
            ChatUtil.sendMessage(sender, "&cYou must make a WorldEdit cuboid selection first using //wand or //pos1 and //pos2.");
            return;
        }

        String displayName = args.length > 2
                ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                : "&a&l" + args[1];

        BlockVector3 min = selection.getMinimumPoint();
        BlockVector3 max = selection.getMaximumPoint();

        Mine mine = new Mine(
                name,
                displayName,
                player.getWorld().getName(),
                min.x(), min.y(), min.z(),
                max.x(), max.y(), max.z(),
                600
        );

        plugin.getMineManager().addMine(mine);

        ChatUtil.sendMessage(sender, "&aSuccessfully created mine '&e" + name + "&a'!");
        ChatUtil.sendMessage(sender, "&7Bounds: &f" + min.x() + "," + min.y() + "," + min.z()
                + " &7to &f" + max.x() + "," + max.y() + "," + max.z()
                + " &8(&e" + mine.getVolume() + " blocks&8)");
        ChatUtil.sendMessage(sender, "&7Next step: Add blocks with &e/bm addblock " + name + " <material> <percentage>");
    }

    private void handleRedefine(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatUtil.sendMessage(sender, "&cOnly in-game players can redefine mines with WorldEdit selections.");
            return;
        }

        if (args.length < 2) {
            ChatUtil.sendMessage(sender, "&cUsage: /bm redefine <name>");
            return;
        }

        String name = args[1].toLowerCase();
        Mine mine = plugin.getMineManager().getMine(name);
        if (mine == null) {
            ChatUtil.sendMessage(sender, "&cMine '&e" + name + "&c' does not exist.");
            return;
        }

        Region selection = plugin.getWorldEditHook().getPlayerSelection(player);
        if (selection == null) {
            ChatUtil.sendMessage(sender, "&cYou must make a valid WorldEdit cuboid selection first.");
            return;
        }

        BlockVector3 min = selection.getMinimumPoint();
        BlockVector3 max = selection.getMaximumPoint();

        mine.setWorldName(player.getWorld().getName());
        mine.setBounds(min.x(), min.y(), min.z(),
                max.x(), max.y(), max.z());

        plugin.getMineManager().saveMines();

        ChatUtil.sendMessage(sender, "&aRedefined boundaries for mine '&e" + name + "&a'. New volume: &e" + mine.getVolume() + " blocks&a.");
    }

    private void handleAddBlock(CommandSender sender, String[] args) {
        if (args.length < 4) {
            ChatUtil.sendMessage(sender, "&cUsage: /bm addblock <name> <material> <percentage>");
            return;
        }

        String name = args[1].toLowerCase();
        Mine mine = plugin.getMineManager().getMine(name);
        if (mine == null) {
            ChatUtil.sendMessage(sender, "&cMine '&e" + name + "&c' does not exist.");
            return;
        }

        Material material = Material.matchMaterial(args[2].toUpperCase(Locale.ROOT));
        if (material == null || !material.isBlock()) {
            ChatUtil.sendMessage(sender, "&cInvalid block material: '&e" + args[2] + "&c'.");
            return;
        }

        double percentage;
        try {
            percentage = Double.parseDouble(args[3]);
            if (percentage <= 0.0 || percentage > 100.0) {
                ChatUtil.sendMessage(sender, "&cPercentage must be between 0.1 and 100.0.");
                return;
            }
        } catch (NumberFormatException e) {
            ChatUtil.sendMessage(sender, "&cInvalid percentage number: '&e" + args[3] + "&c'.");
            return;
        }

        mine.addBlock(material, percentage);
        plugin.getMineManager().saveMines();

        double total = mine.getTotalPercentage();
        ChatUtil.sendMessage(sender, "&aSet &e" + material.name() + " &ato &e" + percentage + "% &afor mine '&e" + name + "&a'.");
        if (total > 100.0) {
            ChatUtil.sendMessage(sender, "&e&lWarning: &eTotal percentage is &c" + String.format("%.1f", total) + "% &e(exceeds 100%).");
        } else {
            ChatUtil.sendMessage(sender, "&7Total mine composition is now: &f" + String.format("%.1f", total) + "%&7.");
        }
    }

    private void handleRemoveBlock(CommandSender sender, String[] args) {
        if (args.length < 3) {
            ChatUtil.sendMessage(sender, "&cUsage: /bm removeblock <name> <material>");
            return;
        }

        String name = args[1].toLowerCase();
        Mine mine = plugin.getMineManager().getMine(name);
        if (mine == null) {
            ChatUtil.sendMessage(sender, "&cMine '&e" + name + "&c' does not exist.");
            return;
        }

        Material material = Material.matchMaterial(args[2].toUpperCase(Locale.ROOT));
        if (material == null) {
            ChatUtil.sendMessage(sender, "&cInvalid material: '&e" + args[2] + "&c'.");
            return;
        }

        mine.removeBlock(material);
        plugin.getMineManager().saveMines();

        ChatUtil.sendMessage(sender, "&aRemoved &e" + material.name() + " &afrom mine '&e" + name + "&a'.");
    }

    private void handleSetHolo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatUtil.sendMessage(sender, "&cOnly in-game players can set hologram locations.");
            return;
        }

        if (args.length < 2) {
            ChatUtil.sendMessage(sender, "&cUsage: /bm setholo <name>");
            return;
        }

        String name = args[1].toLowerCase();
        Mine mine = plugin.getMineManager().getMine(name);
        if (mine == null) {
            ChatUtil.sendMessage(sender, "&cMine '&e" + name + "&c' does not exist.");
            return;
        }

        mine.setHologramLocation(player.getLocation());
        plugin.getMineManager().saveMines();
        mine.updateHologram(plugin);

        ChatUtil.sendMessage(sender, "&aHologram for mine '&e" + name + "&a' set to your current location!");
        ChatUtil.sendMessage(sender, "&7Displaying 2-line countdown with FancyHolograms.");
    }

    private void handleRemoveHolo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            ChatUtil.sendMessage(sender, "&cUsage: /bm removeholo <name>");
            return;
        }

        String name = args[1].toLowerCase();
        Mine mine = plugin.getMineManager().getMine(name);
        if (mine == null) {
            ChatUtil.sendMessage(sender, "&cMine '&e" + name + "&c' does not exist.");
            return;
        }

        plugin.getHologramHook().removeHologram(mine);
        mine.setHologramLocation(null);
        plugin.getMineManager().saveMines();

        ChatUtil.sendMessage(sender, "&aRemoved hologram for mine '&e" + name + "&a'.");
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            ChatUtil.sendMessage(sender, "&cUsage: /bm reset <name>");
            return;
        }

        String name = args[1].toLowerCase();
        Mine mine = plugin.getMineManager().getMine(name);
        if (mine == null) {
            ChatUtil.sendMessage(sender, "&cMine '&e" + name + "&c' does not exist.");
            return;
        }

        ChatUtil.sendMessage(sender, "&aForcing asynchronous reset for mine '&e" + name + "&a' via FAWE...");
        mine.reset(plugin);
        mine.setRemainingSeconds(mine.getResetInterval());
    }

    private void handleSetInterval(CommandSender sender, String[] args) {
        if (args.length < 3) {
            ChatUtil.sendMessage(sender, "&cUsage: /bm setinterval <name> <seconds>");
            return;
        }

        String name = args[1].toLowerCase();
        Mine mine = plugin.getMineManager().getMine(name);
        if (mine == null) {
            ChatUtil.sendMessage(sender, "&cMine '&e" + name + "&c' does not exist.");
            return;
        }

        int seconds;
        try {
            seconds = Integer.parseInt(args[2]);
            if (seconds <= 0) {
                ChatUtil.sendMessage(sender, "&cInterval must be greater than 0 seconds.");
                return;
            }
        } catch (NumberFormatException e) {
            ChatUtil.sendMessage(sender, "&cInvalid seconds integer: '&e" + args[2] + "&c'.");
            return;
        }

        mine.setResetInterval(seconds);
        plugin.getMineManager().saveMines();

        ChatUtil.sendMessage(sender, "&aReset interval for mine '&e" + name + "&a' set to &e" + seconds + " seconds &8(&f" + ChatUtil.formatTime(seconds) + "&8)&a.");
    }

    private void handleSetName(CommandSender sender, String[] args) {
        if (args.length < 3) {
            ChatUtil.sendMessage(sender, "&cUsage: /bm setname <name> <displayName...>");
            return;
        }

        String name = args[1].toLowerCase();
        Mine mine = plugin.getMineManager().getMine(name);
        if (mine == null) {
            ChatUtil.sendMessage(sender, "&cMine '&e" + name + "&c' does not exist.");
            return;
        }

        String newDisplayName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        mine.setDisplayName(newDisplayName);
        plugin.getMineManager().saveMines();
        mine.updateHologram(plugin);

        ChatUtil.sendMessage(sender, "&aUpdated display name for '&e" + name + "&a' to: &r" + ChatUtil.color(newDisplayName));
    }

    private void handleList(CommandSender sender) {
        var mines = plugin.getMineManager().getMines();
        if (mines.isEmpty()) {
            ChatUtil.sendMessage(sender, "&7No mines configured yet. Create one using &e/bm create <name>&7.");
            return;
        }

        ChatUtil.sendMessage(sender, "&6=== Active Mines (" + mines.size() + ") ===");
        for (Mine mine : mines) {
            String holoStatus = mine.hasHologram() ? "&a[Holo]" : "&7[No Holo]";
            ChatUtil.sendMessage(sender, "&8- &e" + mine.getId() + " &7(&r" + ChatUtil.color(mine.getDisplayName()) + "&7) "
                    + holoStatus + " &8| &7Next reset: &f" + mine.getFormattedTime()
                    + " &8| &7Blocks: &f" + mine.getComposition().size());
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            ChatUtil.sendMessage(sender, "&cUsage: /bm info <name>");
            return;
        }

        String name = args[1].toLowerCase();
        Mine mine = plugin.getMineManager().getMine(name);
        if (mine == null) {
            ChatUtil.sendMessage(sender, "&cMine '&e" + name + "&c' does not exist.");
            return;
        }

        ChatUtil.sendMessage(sender, "&6=== Mine Info: &e" + mine.getId() + " &6===");
        ChatUtil.sendMessage(sender, "&7Display Name: &r" + ChatUtil.color(mine.getDisplayName()));
        ChatUtil.sendMessage(sender, "&7World: &f" + mine.getWorldName());
        ChatUtil.sendMessage(sender, "&7Coordinates: &f" + mine.getMinX() + "," + mine.getMinY() + "," + mine.getMinZ()
                + " &7to &f" + mine.getMaxX() + "," + mine.getMaxY() + "," + mine.getMaxZ()
                + " &8(&e" + mine.getVolume() + " blocks&8)");
        ChatUtil.sendMessage(sender, "&7Reset Interval: &f" + mine.getResetInterval() + "s &8| &7Next Reset in: &f" + mine.getFormattedTime());
        ChatUtil.sendMessage(sender, "&7Hologram: " + (mine.hasHologram() ? "&aActive" : "&cNot configured"));

        ChatUtil.sendMessage(sender, "&7Composition (" + String.format("%.1f", mine.getTotalPercentage()) + "%):");
        if (mine.getComposition().isEmpty()) {
            ChatUtil.sendMessage(sender, "  &8- &cNone (defaults to 100% STONE)");
        } else {
            for (Map.Entry<Material, Double> entry : mine.getComposition().entrySet()) {
                ChatUtil.sendMessage(sender, "  &8- &e" + entry.getKey().name() + "&7: &f" + entry.getValue() + "%");
            }
        }
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            ChatUtil.sendMessage(sender, "&cUsage: /bm delete <name>");
            return;
        }

        String name = args[1].toLowerCase();
        if (plugin.getMineManager().deleteMine(name)) {
            ChatUtil.sendMessage(sender, "&aSuccessfully deleted mine '&e" + name + "&a'.");
        } else {
            ChatUtil.sendMessage(sender, "&cMine '&e" + name + "&c' does not exist.");
        }
    }

    private void handleReload(CommandSender sender) {
        plugin.getMineManager().loadMines();
        ChatUtil.sendMessage(sender, "&aReloaded mines.yml and refreshed all holograms.");
    }

    private void sendHelp(CommandSender sender, String label) {
        ChatUtil.sendMessage(sender, "&6=== &e&lbennetsen-mines Commands &6===");
        sender.sendMessage(ChatUtil.color("&e/" + label + " create <name> [displayName] &7- Create mine from WorldEdit selection"));
        sender.sendMessage(ChatUtil.color("&e/" + label + " redefine <name> &7- Redefine mine bounds from WorldEdit selection"));
        sender.sendMessage(ChatUtil.color("&e/" + label + " addblock <name> <material> <%> &7- Add/update block percentage"));
        sender.sendMessage(ChatUtil.color("&e/" + label + " removeblock <name> <material> &7- Remove block from composition"));
        sender.sendMessage(ChatUtil.color("&e/" + label + " setholo <name> &7- Set hologram spawn position at your location"));
        sender.sendMessage(ChatUtil.color("&e/" + label + " removeholo <name> &7- Remove hologram position"));
        sender.sendMessage(ChatUtil.color("&e/" + label + " reset <name> &7- Force an immediate asynchronous reset"));
        sender.sendMessage(ChatUtil.color("&e/" + label + " setinterval <name> <seconds> &7- Set reset interval in seconds"));
        sender.sendMessage(ChatUtil.color("&e/" + label + " setname <name> <displayName> &7- Change mine visual display name"));
        sender.sendMessage(ChatUtil.color("&e/" + label + " list &7- List all configured mines"));
        sender.sendMessage(ChatUtil.color("&e/" + label + " info <name> &7- Show detailed mine configuration"));
        sender.sendMessage(ChatUtil.color("&e/" + label + " delete <name> &7- Delete a mine"));
        sender.sendMessage(ChatUtil.color("&e/" + label + " reload &7- Reload mines.yml from disk"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("bennetsenmines.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterPrefix(SUBCOMMANDS, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (List.of("redefine", "addblock", "removeblock", "setholo", "removeholo", "reset", "setinterval", "setname", "info", "delete").contains(sub)) {
                List<String> mineIds = new ArrayList<>();
                for (Mine m : plugin.getMineManager().getMines()) {
                    mineIds.add(m.getId());
                }
                return filterPrefix(mineIds, args[1]);
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("addblock")) {
                List<String> materials = new ArrayList<>();
                for (Material mat : Material.values()) {
                    if (mat.isBlock()) {
                        materials.add(mat.name().toLowerCase(Locale.ROOT));
                    }
                }
                return filterPrefix(materials, args[2]);
            } else if (sub.equals("removeblock")) {
                String name = args[1].toLowerCase();
                Mine mine = plugin.getMineManager().getMine(name);
                if (mine != null) {
                    List<String> mats = new ArrayList<>();
                    for (Material m : mine.getComposition().keySet()) {
                        mats.add(m.name().toLowerCase(Locale.ROOT));
                    }
                    return filterPrefix(mats, args[2]);
                }
            }
        }

        return Collections.emptyList();
    }

    private List<String> filterPrefix(List<String> input, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String s : input) {
            if (s.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(s);
            }
        }
        return result;
    }
}
