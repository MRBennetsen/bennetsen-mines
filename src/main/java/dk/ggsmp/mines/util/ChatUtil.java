package dk.ggsmp.mines.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/**
 * Utility class for color and message formatting.
 * Supports legacy color codes (&) and Adventure Components.
 */
public final class ChatUtil {

    public static final String PREFIX = "&8[&6&lbennetsen-mines&8] &r";

    private ChatUtil() {
    }

    /**
     * Translates alternate color codes ('&') to Minecraft section formatting ('§').
     *
     * @param text The input string.
     * @return Color-translated string.
     */
    public static String color(String text) {
        if (text == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Converts a string with legacy '&' formatting into a Kyori Adventure Component.
     *
     * @param text The input string.
     * @return Adventure Component.
     */
    public static Component toComponent(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    /**
     * Sends a prefixed formatted message to a command sender.
     *
     * @param sender  The recipient.
     * @param message The message with '&' color codes.
     */
    public static void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(color(PREFIX + message));
    }

    /**
     * Formats seconds into a human-readable digital clock format (e.g. 05:30 or 01:15:00).
     *
     * @param totalSeconds Number of seconds.
     * @return Formatted countdown string.
     */
    public static String formatTime(int totalSeconds) {
        if (totalSeconds < 0) {
            totalSeconds = 0;
        }

        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }
}

