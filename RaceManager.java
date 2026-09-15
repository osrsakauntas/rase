package com.kodari.raceborder.manager;

import com.kodari.raceborder.RaceBorderPlugin;
import com.kodari.raceborder.config.ConfigManager;
import com.kodari.raceborder.database.DatabaseManager;
import com.kodari.raceborder.model.Race;
import com.kodari.raceborder.util.MessageUtil;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PrefixNode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;

public class RaceManager {
    private static final String ADMIN_PREFIX = MessageUtil.color("&8[&cADMIN&8] &r");
    private final RaceBorderPlugin plugin;
    private final ConfigManager configManager;
    private final DatabaseManager database;
    private final BorderManager borders;
    private final Map<UUID, Race> activeRaces = new HashMap<>();
    private final Map<UUID, Boolean> outsideBorders = new HashMap<>();
    private final Set<UUID> outsidePlacementWarnings = new HashSet<>();

    public RaceManager(RaceBorderPlugin plugin, ConfigManager configManager, DatabaseManager database, BorderManager borders) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.database = database;
        this.borders = borders;
    }

    public RaceBorderPlugin getPlugin() {
        return plugin;
    }

    public boolean assign(Player player, String raceId) {
        Race race = configManager.getRace(raceId);
        if (race == null) {
            MessageUtil.send(plugin, player, "race-not-found");
            return false;
        }
        if (race.getId().equals(getRaceId(player))) {
            MessageUtil.send(plugin, player, "already-in-race", Map.of("race", race.getDisplayName()));
            return false;
        }
        Race previousRace = getRace(player);
        if (previousRace == null) {
            previousRace = configManager.getRace(database.findRace(player.getUniqueId()));
        }
        activeRaces.put(player.getUniqueId(), race);
        database.assign(player.getUniqueId(), race.getId());
        updateRacePrefix(player.getUniqueId(), previousRace, race);
        borders.apply(player, race);
        outsideBorders.put(player.getUniqueId(), false);
        outsidePlacementWarnings.remove(player.getUniqueId());
        updateTabListOrder();
        MessageUtil.send(plugin, player, "joined", Map.of("race", race.getDisplayName()));
        return true;
    }

    public boolean teleportToRace(Player player, Race race) {
        if (race.getWorld() == null || Bukkit.getWorld(race.getWorld()) == null) {
            player.sendMessage(MessageUtil.color("&cThe race world is not loaded."));
            return false;
        }

        player.teleport(new Location(Bukkit.getWorld(race.getWorld()), race.getSpawnX(), race.getSpawnY(),
                race.getSpawnZ(), race.getSpawnYaw(), race.getSpawnPitch()));
        borders.apply(player, race);
        outsideBorders.put(player.getUniqueId(), false);
        outsidePlacementWarnings.remove(player.getUniqueId());
        return true;
    }

    public void restore(Player player) {
        UUID uuid = player.getUniqueId();
        String raceId = database.findRace(uuid);
        Race race = configManager.getRace(raceId);
        if (race == null || Bukkit.getWorld(race.getWorld()) == null) {
            activeRaces.remove(uuid);
            removeStaleRacePrefixes(uuid);
            if (raceId != null) {
                database.remove(uuid);
            }
            return;
        }

        activeRaces.put(uuid, race);
        updateRacePrefix(uuid, null, race);
        updateAdminPrefix(player);
        borders.apply(player, race);
        outsideBorders.put(uuid, false);
        outsidePlacementWarnings.remove(uuid);
        updateTabListOrder();
    }

    public void remove(Player player) {
        Race race = getRace(player);
        String databaseRaceId = database.findRace(player.getUniqueId());
        if (race == null && databaseRaceId == null) {
            removeStaleRacePrefixes(player.getUniqueId());
            MessageUtil.send(plugin, player, "not-in-race");
            return;
        }
        if (race == null) {
            race = configManager.getRace(databaseRaceId);
        }
        activeRaces.remove(player.getUniqueId());
        updateRacePrefix(player.getUniqueId(), race, null);
        if (race == null) {
            removeStaleRacePrefixes(player.getUniqueId());
        }
        database.remove(player.getUniqueId());
        borders.clear(player);
        outsideBorders.remove(player.getUniqueId());
        outsidePlacementWarnings.remove(player.getUniqueId());
        updateTabListOrder();
        MessageUtil.send(plugin, player, "left");
    }

    private void updateRacePrefix(UUID uuid, Race previousRace, Race newRace) {
        LuckPermsProvider.get().getUserManager().modifyUser(uuid, user -> {
            if (previousRace != null) {
                String oldPrefix = normalizedPrefix(racePrefix(previousRace));
                user.data().toCollection().stream()
                        .filter(node -> node instanceof PrefixNode)
                        .map(node -> (PrefixNode) node)
                        .filter(node -> normalizedPrefix(node.getMetaValue()).equals(oldPrefix))
                        .toList()
                        .forEach(user.data()::remove);
            } else if (newRace != null) {
                user.data().toCollection().stream()
                        .filter(node -> node instanceof PrefixNode)
                        .map(node -> (PrefixNode) node)
                        .filter(node -> isRacePrefix(node.getMetaValue()))
                        .toList()
                        .forEach(user.data()::remove);
            }
            if (newRace != null) {
                user.data().add(PrefixNode.builder(racePrefix(newRace), 100).build());
            }
        });
    }

    private void removeStaleRacePrefixes(UUID uuid) {
        LuckPermsProvider.get().getUserManager().modifyUser(uuid, user -> user.data().toCollection().stream()
                .filter(node -> node instanceof PrefixNode)
                .map(node -> (PrefixNode) node)
                .filter(node -> isRacePrefix(node.getMetaValue()))
                .toList()
                .forEach(user.data()::remove));
    }

    public void updateAdminPrefix(Player player) {
        UUID uuid = player.getUniqueId();
        boolean operator = player.isOp();
        LuckPermsProvider.get().getUserManager().modifyUser(uuid, user -> {
            user.data().toCollection().stream()
                    .filter(node -> node instanceof PrefixNode)
                    .map(node -> (PrefixNode) node)
                    .filter(node -> isAdminPrefix(node.getMetaValue()))
                    .toList()
                    .forEach(user.data()::remove);
            if (operator) {
                user.data().add(PrefixNode.builder(ADMIN_PREFIX, 200).build());
            }
        });
    }

    public void syncAdminPrefixes() {
        Bukkit.getOnlinePlayers().forEach(this::updateAdminPrefix);
        updateTabListOrder();
    }

    public void updateTabListOrder() {
        List<Race> configuredRaces = new ArrayList<>(configManager.getRaces().values());
        configuredRaces.sort(Comparator.comparing(
                race -> ChatColor.stripColor(MessageUtil.color(race.getDisplayName())),
                String.CASE_INSENSITIVE_ORDER));
        Map<String, Integer> raceOrder = new HashMap<>();
        for (int index = 0; index < configuredRaces.size(); index++) {
            raceOrder.put(configuredRaces.get(index).getId(), index);
        }

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator
                .comparingInt((Player player) -> tabGroupOrder(player, raceOrder))
                .thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        for (int index = 0; index < players.size(); index++) {
            players.get(index).setPlayerListOrder(index);
            applyTabSorting(players.get(index), raceOrder);
        }
    }

    private void applyTabSorting(Player player, Map<String, Integer> raceOrder) {
        try {
            Class<?> tabApiClass = Class.forName("me.neznamy.tab.api.TabAPI");
            Class<?> tabPlayerClass = Class.forName("me.neznamy.tab.api.TabPlayer");
            Class<?> sortingManagerClass = Class.forName("me.neznamy.tab.api.tablist.SortingManager");

            Object tabApi = tabApiClass.getMethod("getInstance").invoke(null);
            Object tabPlayer = tabApiClass.getMethod("getPlayer", UUID.class).invoke(tabApi, player.getUniqueId());
            if (tabPlayer == null) {
                return;
            }

            Object sortingManager = tabApiClass.getMethod("getSortingManager").invoke(tabApi);
            if (sortingManager == null) {
                return;
            }

            tabPlayerClass.getMethod("setTemporaryGroup", String.class)
                    .invoke(tabPlayer, (Object) null);
            int order = tabGroupOrder(player, raceOrder);
            sortingManagerClass.getMethod("forceTeamName", tabPlayerClass, String.class)
                    .invoke(sortingManager, tabPlayer, String.format(Locale.ROOT, "%04d", order));
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    private int tabGroupOrder(Player player, Map<String, Integer> raceOrder) {
        if (isAdmin(player)) {
            return 0;
        }
        Race race = getRace(player);
        return race == null ? raceOrder.size() + 1
                : raceOrder.getOrDefault(race.getId(), raceOrder.size()) + 1;
    }

    private boolean isAdmin(Player player) {
        return player.isOp() || player.hasPermission("raceborder.admin");
    }

    private boolean isAdminPrefix(String prefix) {
        return normalizedPrefix(prefix).equals(normalizedPrefix(ADMIN_PREFIX));
    }

    private boolean isRacePrefix(String prefix) {
        String plain = ChatColor.stripColor(prefix);
        return plain != null && plain.trim().startsWith("[") && plain.trim().endsWith("]")
                && !isAdminPrefix(prefix);
    }

    private String normalizedPrefix(String prefix) {
        String plain = ChatColor.stripColor(prefix);
        return plain == null ? "" : plain.trim().toLowerCase(Locale.ROOT);
    }

    private String racePrefix(Race race) {
        String displayName = ChatColor.stripColor(MessageUtil.color(race.getDisplayName()));
        return MessageUtil.color("&8[" + race.getPrefixColor() + displayName + "&8] &r");
    }

    public void quit(Player player) {
        activeRaces.remove(player.getUniqueId());
        outsideBorders.remove(player.getUniqueId());
        outsidePlacementWarnings.remove(player.getUniqueId());
        borders.clear(player);
    }

    public Race getRace(Player player) {
        return activeRaces.get(player.getUniqueId());
    }

    public Race getRace(UUID uuid) {
        return activeRaces.get(uuid);
    }

    public boolean canPlace(Player player, Location location, boolean allowedOutside) {
        Race race = getRace(player);
        return race != null && (allowedOutside || findTerritory(location) != null);
    }

    public boolean canBreak(Player player, Location location) {
        Race race = getRace(player);
        return race != null;
    }

    public void warnOutsidePlacement(Player player) {
        Race race = getRace(player);
        boolean outside = race == null
                ? findTerritory(player.getLocation()) == null
                : !borders.isInside(race, player.getLocation());
        if (outside && outsidePlacementWarnings.add(player.getUniqueId())) {
            MessageUtil.send(plugin, player, "outside-border");
        }
    }

    public void handleMove(Player player, Location from, Location to) {
        handleUnassignedMove(player, from, to);
    }

    private void handleUnassignedMove(Player player, Location from, Location to) {
        Race fromRace = findTerritory(from);
        Race toRace = findTerritory(to);
        String fromId = fromRace == null ? null : fromRace.getId();
        String toId = toRace == null ? null : toRace.getId();

        if (java.util.Objects.equals(fromId, toId)) {
            return;
        }

        if (fromRace != null) {
            showBorderTitle(player, "left-border-title", "left-border-subtitle", fromRace);
        }
        if (toRace != null) {
            showBorderTitle(player, "entered-border-title", "entered-border-subtitle", toRace);
        }
    }

    private Race findTerritory(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        for (Race race : configManager.getRaces().values()) {
            if (race.getWorld() != null && race.getWorld().equals(location.getWorld().getName())
                    && borders.isInside(race, location)) {
                return race;
            }
        }
        return null;
    }

    private void showBorderTitle(Player player, String titleKey, String subtitleKey, Race race) {
        String title = plugin.getConfig().getString("messages." + titleKey, "")
                .replace("%race%", race.getDisplayName());
        String subtitle = plugin.getConfig().getString("messages." + subtitleKey, "")
                .replace("%race%", race.getDisplayName());
        player.sendTitle(MessageUtil.color(title), MessageUtil.color(subtitle), 10, 60, 10);
    }

    public String getRaceId(Player player) {
        Race race = getRace(player);
        return race == null ? null : race.getId();
    }

    public String getRaceName(UUID uuid) {
        Race race = activeRaces.get(uuid);
        return race == null ? "" : race.getDisplayName();
    }

    public Collection<Race> getConfiguredRaces() {
        return configManager.getRaces().values();
    }

    public Race getConfiguredRace(String raceId) {
        return configManager.getRace(raceId);
    }

    public boolean deleteRace(String raceId) {
        Race race = configManager.getRace(raceId);
        if (race == null) {
            return false;
        }

        for (Map.Entry<UUID, Race> entry : new HashMap<>(activeRaces).entrySet()) {
            if (!race.getId().equals(entry.getValue().getId())) {
                continue;
            }
            updateRacePrefix(entry.getKey(), entry.getValue(), null);
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                borders.clear(player);
            }
            activeRaces.remove(entry.getKey());
            outsideBorders.remove(entry.getKey());
            outsidePlacementWarnings.remove(entry.getKey());
        }

        for (UUID uuid : database.findPlayersByRace(race.getId()).keySet()) {
            updateRacePrefix(uuid, race, null);
            removeStaleRacePrefixes(uuid);
        }

        database.removeRace(race.getId());
        plugin.getProgressionManager().removeRace(race.getId());
        plugin.getConfig().set("races." + race.getId(), null);
        plugin.saveConfig();
        plugin.reloadRaceConfiguration();
        return true;
    }

    public void reload() {
        for (Map.Entry<UUID, Race> entry : new HashMap<>(activeRaces).entrySet()) {
            Race updated = configManager.getRace(entry.getValue().getId());
            if (updated == null) {
                updateRacePrefix(entry.getKey(), entry.getValue(), null);
                removeStaleRacePrefixes(entry.getKey());
                database.remove(entry.getKey());
                activeRaces.remove(entry.getKey());
            } else {
                activeRaces.put(entry.getKey(), updated);
                Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    borders.apply(player, updated);
                }
            }
        }
        for (Map.Entry<UUID, String> entry : database.findAssignments().entrySet()) {
            if (configManager.getRace(entry.getValue()) != null) {
                continue;
            }
            removeStaleRacePrefixes(entry.getKey());
            database.remove(entry.getKey());
            activeRaces.remove(entry.getKey());
        }
        updateTabListOrder();
    }

    public void shutdown() {
        activeRaces.clear();
        outsideBorders.clear();
        outsidePlacementWarnings.clear();
    }
}