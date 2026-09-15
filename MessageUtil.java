package com.kodari.raceborder.util;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.Map;

public final class MessageUtil {
    private MessageUtil() {
    }

    public static void send(Plugin plugin, CommandSender sender, String key, Map<String, String> replacements) {
        String defaultMessage = "outside-border".equals(key)
                ? "&cUž rasės teritorijos ribų šio bloko statyti negalima."
                : "&cTrūksta pranešimo: " + key;
        String message = plugin.getConfig().getString("messages." + key, defaultMessage);
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            message = message.replace("%" + replacement.getKey() + "%", replacement.getValue());
        }
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + message));
    }

    public static void send(Plugin plugin, CommandSender sender, String key) {
        send(plugin, sender, key, Map.of());
    }

    public static void broadcast(Plugin plugin, String key, Map<String, String> replacements) {
        String defaultMessage = "&cTrūksta pranešimo: " + key;
        String message = plugin.getConfig().getString("messages." + key, defaultMessage);
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            message = message.replace("%" + replacement.getKey() + "%", replacement.getValue());
        }
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', prefix + message));
    }

    public static String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }
}