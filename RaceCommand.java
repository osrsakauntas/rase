package com.kodari.raceborder.command;

import com.kodari.raceborder.RaceBorderPlugin;
import com.kodari.raceborder.manager.RaceManager;
import com.kodari.raceborder.model.Race;
import com.kodari.raceborder.model.RacePhase;
import com.kodari.raceborder.util.MessageUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
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

public class RaceCommand implements CommandExecutor, TabCompleter {
    private final RaceBorderPlugin plugin;
    private final RaceManager races;

    public RaceCommand(RaceBorderPlugin plugin) {
        this.plugin = plugin;
        this.races = plugin.getRaceManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (subcommand.equals("paskirti")) {
            if (!requireAdmin(sender)) {
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(MessageUtil.color("&cNaudojimas: /rase paskirti <zaidejas> <rase>"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(MessageUtil.color("&cZaidejas turi buti prisijunges."));
                return true;
            }
            if (races.assign(target, args[2])) {
                MessageUtil.send(plugin, sender, "assigned", java.util.Map.of("player", target.getName(), "race", races.getRace(target).getDisplayName()));
            }
            return true;
        }
        if (subcommand.equals("nuimti")) {
            if (!requireAdmin(sender)) {
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(MessageUtil.color("&cNaudojimas: /rase nuimti <zaidejas>"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(MessageUtil.color("&cZaidejas turi buti prisijunges."));
                return true;
            }
            races.remove(target);
            return true;
        }
        if (subcommand.equals("border")) {
            if (!requireAdmin(sender)) {
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(MessageUtil.color("&cNaudojimas: /rase border <rase> <dydis>"));
                return true;
            }
            double size;
            try {
                size = Double.parseDouble(args[2]);
            } catch (NumberFormatException exception) {
                sender.sendMessage(MessageUtil.color("&cBorderio dydis turi buti skaicius."));
                return true;
            }
            Race race = races.getConfiguredRace(args[1]);
            if (race == null || size <= 0) {
                sender.sendMessage(MessageUtil.color("&cNeteisinga rase arba borderio dydis."));
                return true;
            }
            plugin.getConfig().set("races." + race.getId() + ".border-size", size);
            plugin.saveConfig();
            plugin.reloadRaceConfiguration();
            MessageUtil.send(plugin, sender, "border-changed", java.util.Map.of("race", race.getDisplayName(), "size", String.valueOf(size)));
            return true;
        }
        if (subcommand.equals("sukurti")) {
            if (!requireAdmin(sender)) {
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageUtil.color("&cŠią komandą gali naudoti tik žaidėjas."));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(MessageUtil.color("&cNaudojimas: /rase sukurti <pavadinimas> [spalva]"));
                return true;
            }
            String prefixColor = "&f";
            int nameEnd = args.length;
            if (args.length >= 3) {
                String selectedColor = parseColor(args[args.length - 1]);
                if (selectedColor != null) {
                    prefixColor = selectedColor;
                    nameEnd--;
                }
            }
            String displayName = String.join(" ", Arrays.copyOfRange(args, 1, nameEnd)).trim();
            String id = createRaceId(displayName);
            String path = "races." + id;
            plugin.getConfig().set(path + ".display-name", displayName);
            plugin.getConfig().set(path + ".prefix-color", prefixColor);
            plugin.getConfig().set(path + ".world", player.getWorld().getName());
            plugin.getConfig().set(path + ".shape", "square");
            plugin.getConfig().set(path + ".center.x", player.getLocation().getX());
            plugin.getConfig().set(path + ".center.z", player.getLocation().getZ());
            plugin.getConfig().set(path + ".border-size", 96);
            plugin.getConfig().set(path + ".spawn.x", player.getLocation().getX());
            plugin.getConfig().set(path + ".spawn.y", player.getLocation().getY());
            plugin.getConfig().set(path + ".spawn.z", player.getLocation().getZ());
            plugin.getConfig().set(path + ".spawn.yaw", player.getLocation().getYaw());
            plugin.getConfig().set(path + ".spawn.pitch", player.getLocation().getPitch());
            plugin.saveConfig();
            plugin.reloadRaceConfiguration();
            MessageUtil.send(plugin, sender, "race-created", java.util.Map.of("race", displayName, "size", "96x96"));
            return true;
        }
        if (subcommand.equals("istrinti")) {
            if (!requireAdmin(sender)) {
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(MessageUtil.color("&cNaudojimas: /rase istrinti <rase>"));
                return true;
            }
            Race race = races.getConfiguredRace(args[1]);
            if (race == null) {
                MessageUtil.send(plugin, sender, "race-not-found");
                return true;
            }
            races.deleteRace(race.getId());
            MessageUtil.send(plugin, sender, "race-deleted", java.util.Map.of("race", race.getDisplayName()));
            return true;
        }
        if (subcommand.equals("reload")) {
            if (!requireAdmin(sender)) {
                return true;
            }
            plugin.reloadRaceConfiguration();
            MessageUtil.send(plugin, sender, "reloaded");
            return true;
        }
        if (subcommand.equals("pakeltilygi")) {
            if (!requireAdmin(sender)) {
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(MessageUtil.color("&cNaudojimas: /rase pakeltilygi <rase> <lygis>"));
                return true;
            }
            Race race = races.getConfiguredRace(args[1]);
            RacePhase phase;
            try {
                phase = RacePhase.valueOf(args[2].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                sender.sendMessage(MessageUtil.color("&cNeteisingas lygis. Galimi: STONE, COPPER, IRON, DIAMOND, NETHERITE."));
                return true;
            }
            if (race == null) {
                MessageUtil.send(plugin, sender, "race-not-found");
                return true;
            }
            if (!plugin.getProgressionManager().promoteToPhase(race.getId(), phase)) {
                sender.sendMessage(MessageUtil.color("&eRasė jau yra pasiekusi šį arba aukštesnį lygį."));
                return true;
            }
            sender.sendMessage(MessageUtil.color("&aRasės &f" + race.getDisplayName() + " &alygis pakeltas į &f" + phase.name() + "&a."));
            return true;
        }
        if (subcommand.equals("nuleistilygi")) {
            if (!requireAdmin(sender)) {
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(MessageUtil.color("&cNaudojimas: /rase nuleistilygi <rase> <lygis>"));
                return true;
            }
            Race race = races.getConfiguredRace(args[1]);
            RacePhase phase;
            try {
                phase = RacePhase.valueOf(args[2].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                sender.sendMessage(MessageUtil.color("&cNeteisingas lygis. Galimi: STONE, COPPER, IRON, DIAMOND, NETHERITE."));
                return true;
            }
            if (race == null) {
                MessageUtil.send(plugin, sender, "race-not-found");
                return true;
            }
            if (!plugin.getProgressionManager().demoteToPhase(race.getId(), phase)) {
                sender.sendMessage(MessageUtil.color("&eRasė jau yra šiame arba žemesniame lygyje."));
                return true;
            }
            sender.sendMessage(MessageUtil.color("&aRasės &f" + race.getDisplayName() + " &alygis nuleistas į &f" + phase.name() + "&a."));
            return true;
        }
        if (subcommand.equals("reputacija")) {
            if (!requireAdmin(sender)) {
                return true;
            }
            if (args.length < 4 || (!args[1].equalsIgnoreCase("duoti") && !args[1].equalsIgnoreCase("nuimti"))) {
                sender.sendMessage(MessageUtil.color("&cNaudojimas: /rase reputacija <duoti|nuimti> <kiekis> <rase>"));
                return true;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException exception) {
                sender.sendMessage(MessageUtil.color("&cReputacijos kiekis turi būti sveikas skaičius."));
                return true;
            }
            Race race = races.getConfiguredRace(args[3]);
            if (race == null) {
                MessageUtil.send(plugin, sender, "race-not-found");
                return true;
            }
            if (amount <= 0) {
                sender.sendMessage(MessageUtil.color("&cReputacijos kiekis turi būti didesnis už nulį."));
                return true;
            }
            boolean give = args[1].equalsIgnoreCase("duoti");
            int reputation = give
                    ? plugin.getProgressionManager().addReputation(race.getId(), amount)
                    : plugin.getProgressionManager().removeReputation(race.getId(), amount);
            sender.sendMessage(MessageUtil.color("&aRasei &f" + race.getDisplayName() + (give ? " &aduota &f+" : " &anuimta &f-") + amount
                    + " &areputacijos. Iš viso: &f" + reputation + "&a."));
            return true;
        }
        if (subcommand.equals("katilas")) {
            if (!requireAdmin(sender)) {
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageUtil.color("&cŠią komandą gali naudoti tik žaidėjas."));
                return true;
            }
            if (args.length == 1) {
                List<Race> availableRaces = new ArrayList<>(races.getConfiguredRaces());
                if (availableRaces.isEmpty()) {
                    MessageUtil.send(plugin, sender, "race-not-found");
                    return true;
                }
                player.sendMessage(MessageUtil.color("&8&m------------------------------"));
                player.sendMessage(MessageUtil.color("&6Pasirink rasę, kuriai priskirti katilą:"));
                for (Race race : availableRaces) {
                    TextComponent choice = new TextComponent(MessageUtil.color(
                            "&e• &f" + race.getDisplayName() + " &7[&aPasirinkti&7]"));
                    choice.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            "/rase katilas " + race.getId()));
                    player.spigot().sendMessage(choice);
                }
                player.sendMessage(MessageUtil.color("&8&m------------------------------"));
                return true;
            }
            if (args.length != 2) {
                sender.sendMessage(MessageUtil.color("&cNaudojimas: /rase katilas"));
                return true;
            }
            Race race = races.getConfiguredRace(args[1]);
            if (race == null) {
                MessageUtil.send(plugin, sender, "race-not-found");
                return true;
            }
            if (plugin.getProgressionManager().setCauldron(player, race.getId())) {
                player.sendMessage(MessageUtil.color("&aRasės katilas pastatytas rasei: &f" + race.getDisplayName() + "&a."));
            } else {
                MessageUtil.send(plugin, sender, "race-not-found");
            }
            return true;
        }
        if (subcommand.equals("auka")) {
            if (!requireAdmin(sender)) {
                return true;
            }
            if (args.length == 1) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(MessageUtil.color("&cŠią komandą gali naudoti tik žaidėjas."));
                    return true;
                }
                if (!plugin.getGuiManager().openOfferingEditor(player)) {
                    MessageUtil.send(plugin, sender, "race-not-found");
                }
                return true;
            }
            sender.sendMessage(MessageUtil.color("&cNaudojimas: /rase auka"));
            return true;
        }
        if (subcommand.equals("info")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageUtil.color("&cŠią komandą gali naudoti tik žaidėjas."));
                return true;
            }
            String raceId = args.length >= 2 ? args[1] : null;
            if (raceId != null && !requireAdmin(sender)) {
                return true;
            }
            if (!plugin.getGuiManager().openInfo(player, raceId)) {
                MessageUtil.send(plugin, sender, raceId == null ? "not-in-race" : "race-not-found");
            }
            return true;
        }
        if (subcommand.equals("upgrade")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageUtil.color("&cŠią komandą gali naudoti tik žaidėjas."));
                return true;
            }
            plugin.getProgressionManager().upgradeHeldItem(player);
            return true;
        }
        if (subcommand.equals("tp")) {
            if (!requireAdmin(sender)) {
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(MessageUtil.color("&cNaudojimas: /rase tp <zaidejas> <rase>"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(MessageUtil.color("&cZaidejas turi buti prisijunges."));
                return true;
            }
            Race race = races.getConfiguredRace(args[2]);
            if (race == null) {
                MessageUtil.send(plugin, sender, "race-not-found");
                return true;
            }
            if (races.teleportToRace(target, race)) {
                sender.sendMessage(MessageUtil.color("&aŽaidėjas " + target.getName() + " nuteleportuotas į rasės "
                        + race.getDisplayName() + " teritoriją."));
            }
            return true;
        }
        sender.sendMessage(MessageUtil.color("&cNezinoma komanda."));
        return true;
    }

    private boolean requireAdmin(CommandSender sender) {
        if (!sender.hasPermission("raceborder.admin")) {
            MessageUtil.send(plugin, sender, "no-permission");
            return false;
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(MessageUtil.color("&b/rase paskirti <zaidejas> <rase>\n&b/rase nuimti <zaidejas>\n&b/rase border <rase> <dydis>\n&b/rase sukurti <pavadinimas>\n&b/rase istrinti <rase>\n&b/rase tp <zaidejas> <rase>\n&b/rase pakeltilygi <rase> <lygis>\n&b/rase nuleistilygi <rase> <lygis>\n&b/rase reputacija <duoti|nuimti> <kiekis> <rase>\n&b/rase katilas\n&b/rase auka\n&b/rase info\n&b/rase upgrade\n&b/rase reload"));
    }

    private String createRaceId(String displayName) {
        String base = displayName.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (base.isEmpty()) {
            base = "rase";
        }
        String id = base;
        int number = 2;
        while (plugin.getConfig().contains("races." + id)) {
            id = base + "-" + number++;
        }
        return id;
    }

    private String parseColor(String value) {
        String color = value.toLowerCase(Locale.ROOT);
        if (color.matches("&[0-9a-f]")) {
            return color;
        }
        if (color.matches("[0-9a-f]")) {
            return "&" + color;
        }
        return switch (color) {
            case "black", "juoda" -> "&0";
            case "dark_blue", "tamsiai-melyna" -> "&1";
            case "dark_green", "tamsiai-zalia" -> "&2";
            case "dark_aqua", "tamsiai-zalsva" -> "&3";
            case "dark_red", "tamsiai-raudona" -> "&4";
            case "dark_purple", "tamsiai-violetine" -> "&5";
            case "gold", "auksine" -> "&6";
            case "gray", "pilka" -> "&7";
            case "dark_gray", "tamsiai-pilka" -> "&8";
            case "blue", "melyna" -> "&9";
            case "green", "zalia" -> "&a";
            case "aqua", "zalsva" -> "&b";
            case "red", "raudona" -> "&c";
            case "light_purple", "sviesiai-violetine" -> "&d";
            case "yellow", "geltona" -> "&e";
            case "white", "balta" -> "&f";
            default -> null;
        };
    }

    private List<String> colorChoices() {
        return List.of("&0", "&1", "&2", "&3", "&4", "&5", "&6", "&7", "&8", "&9", "&a", "&b", "&c", "&d", "&e", "&f");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partial(args[0], List.of("paskirti", "nuimti", "border", "sukurti", "tp", "pakeltilygi", "nuleistilygi", "reputacija", "katilas", "auka", "info", "upgrade", "reload"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("paskirti")) {
            return partial(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("nuimti")) {
            return partial(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("tp")) {
            return partial(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("katilas")) {
            return partial(args[1], races.getConfiguredRaces().stream().map(Race::getId).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("pakeltilygi")) {
            return partial(args[1], races.getConfiguredRaces().stream().map(Race::getId).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("nuleistilygi")) {
            return partial(args[1], races.getConfiguredRaces().stream().map(Race::getId).toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("pakeltilygi")) {
            return partial(args[2], Arrays.stream(RacePhase.values()).map(RacePhase::name).toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("nuleistilygi")) {
            return partial(args[2], Arrays.stream(RacePhase.values()).map(RacePhase::name).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reputacija")) {
            return partial(args[1], List.of("duoti", "nuimti"));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("reputacija")
                && (args[1].equalsIgnoreCase("duoti") || args[1].equalsIgnoreCase("nuimti"))) {
            return partial(args[3], races.getConfiguredRaces().stream().map(Race::getId).toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("tp")) {
            return partial(args[2], races.getConfiguredRaces().stream().map(Race::getId).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("border")) {
            return partial(args[1], races.getConfiguredRaces().stream().map(Race::getId).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            return partial(args[1], races.getConfiguredRaces().stream().map(Race::getId).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("istrinti")) {
            return partial(args[1], races.getConfiguredRaces().stream().map(Race::getId).toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("paskirti")) {
            return partial(args[2], races.getConfiguredRaces().stream().map(Race::getId).toList());
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("sukurti")) {
            return partial(args[args.length - 1], colorChoices());
        }
        return Collections.emptyList();
    }

    private List<String> partial(String input, List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(input.toLowerCase(Locale.ROOT))) {
                result.add(value);
            }
        }
        return result;
    }

}