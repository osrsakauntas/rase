package com.kodari.raceborder.api;

import com.kodari.raceborder.model.Profession;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Locale;
import java.text.Normalizer;
import java.util.UUID;

public final class ProfessionsAdapter implements ProfessionProvider {
    private static final String[] PLUGIN_NAMES = {"Professions", "professions"};
    private static final String[] DIRECT_METHODS = {
            "getProfession", "getPlayerProfession", "getProfessionName", "getActiveProfession",
            "getSelectedProfession", "getCurrentProfession", "getJob", "getPlayerJob"
    };
    private static final String[] MANAGER_METHODS = {
            "getProfessionManager", "getPlayerManager", "getPlayerDataManager", "getProfessionDataManager",
            "getDataManager", "getDatabaseManager", "getUserManager", "getRepository", "getStorage",
            "getProfessionService", "getApi", "getManager"
    };

    private final Plugin professionsPlugin;

    private ProfessionsAdapter(Plugin professionsPlugin) {
        this.professionsPlugin = professionsPlugin;
    }

    public static boolean register(Plugin owner) {
        Plugin professions = findPlugin();
        if (professions == null) {
            owner.getLogger().warning("Professions plugin not found; race quest profession progress is disabled.");
            return false;
        }
        Bukkit.getServicesManager().register(ProfessionProvider.class,
                new ProfessionsAdapter(professions), owner, ServicePriority.Normal);
        owner.getLogger().info("Connected to " + professions.getName() + " for race quest professions.");
        return true;
    }

    private static Plugin findPlugin() {
        for (String name : PLUGIN_NAMES) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
            if (plugin != null && plugin.isEnabled()) {
                return plugin;
            }
        }
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (plugin.getName().toLowerCase(Locale.ROOT).contains("profession") && plugin.isEnabled()) {
                return plugin;
            }
        }
        return null;
    }

    @Override
    public Profession getProfession(Player player) {
        Object value = invokeCandidates(professionsPlugin, player, 0);
        Profession profession = parse(value);
        if (profession != null) {
            return profession;
        }
        profession = searchObject(professionsPlugin, player, 0, new IdentityHashMap<>());
        return profession == null ? getLuckPermsProfession(player) : profession;
    }

    private Profession getLuckPermsProfession(Player player) {
        try {
            var user = LuckPermsProvider.get().getUserManager().getUser(player.getUniqueId());
            if (user == null) {
                return null;
            }
            var metaData = user.getCachedData().getMetaData();
            Profession profession = parseText(metaData.getSuffix());
            return profession == null ? parseText(metaData.getPrefix()) : profession;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Profession searchManagers(Object source, Player player, int depth) {
        if (depth > 4) {
            return null;
        }
        for (String methodName : MANAGER_METHODS) {
            Object manager = invokeNoArgs(source, methodName);
            if (manager == null || manager == source) {
                continue;
            }
            Profession direct = parse(invokeCandidates(manager, player, depth));
            if (direct != null) {
                return direct;
            }
            Profession nested = searchObject(manager, player, depth + 1, new IdentityHashMap<>());
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private Profession searchObject(Object source, Player player, int depth,
                                    Map<Object, Boolean> visited) {
        if (source == null || depth > 4 || visited.put(source, Boolean.TRUE) != null) {
            return null;
        }

        Profession direct = parse(source);
        if (direct != null) {
            return direct;
        }

        if (source instanceof Map<?, ?> map) {
            for (Object key : new Object[]{player.getUniqueId(), player.getName(), player}) {
                Profession profession = searchObject(map.get(key), player, depth + 1, visited);
                if (profession != null) {
                    return profession;
                }
            }
        }

        Profession fromKnownManagers = searchManagers(source, player, depth + 1);
        if (fromKnownManagers != null) {
            return fromKnownManagers;
        }

        for (Method method : source.getClass().getMethods()) {
            if (method.getParameterCount() != 1 || !isPlayerLookup(method.getName())) {
                continue;
            }
            Object argument = argument(method.getParameterTypes()[0], player);
            if (argument == null && method.getParameterTypes()[0].isPrimitive()) {
                continue;
            }
            try {
                Object result = method.invoke(source, argument);
                Profession profession = parse(result);
                if (profession != null) {
                    return profession;
                }
                profession = searchObject(result, player, depth + 1, visited);
                if (profession != null) {
                    return profession;
                }
            } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            }
        }

        for (Field field : source.getClass().getDeclaredFields()) {
            if (!isProfessionField(field.getName()) && !isDataField(field.getName())) {
                continue;
            }
            try {
                field.setAccessible(true);
                Profession profession = searchObject(field.get(source), player, depth + 1, visited);
                if (profession != null) {
                    return profession;
                }
            } catch (IllegalAccessException | RuntimeException ignored) {
            }
        }
        return null;
    }

    private Object invokeCandidates(Object source, Player player, int depth) {
        for (String methodName : DIRECT_METHODS) {
            for (Method method : source.getClass().getMethods()) {
                if (!method.getName().equalsIgnoreCase(methodName) || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> parameter = method.getParameterTypes()[0];
                Object argument = argument(parameter, player);
                if (argument == null && parameter.isPrimitive()) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    Object result = method.invoke(source, argument);
                    Profession profession = parse(result);
                    if (profession != null) {
                        return profession;
                    }
                    if (result != null && depth < 4) {
                        Profession nested = searchObject(result, player, depth + 1, new IdentityHashMap<>());
                        if (nested != null) {
                            return nested;
                        }
                    }
                } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
                    // The adapter tries several possible API shapes because the jar has no public API contract.
                }
            }
        }
        return null;
    }

    private Object argument(Class<?> parameter, Player player) {
        if (parameter.isAssignableFrom(Player.class) || parameter.isAssignableFrom(player.getClass())) {
            return player;
        }
        if (parameter == String.class) {
            return player.getName();
        }
        if (parameter == UUID.class) {
            return player.getUniqueId();
        }
        return null;
    }

    private Object invokeNoArgs(Object source, String name) {
        try {
            Method method = source.getClass().getMethod(name);
            method.setAccessible(true);
            return method.invoke(source);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private boolean isPlayerLookup(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.contains("profession") || normalized.contains("playerdata")
                || normalized.contains("userdata") || normalized.contains("profile")
                || normalized.contains("player") || normalized.contains("user");
    }

    private boolean isProfessionField(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.equals("profession") || normalized.equals("job")
                || normalized.equals("selectedprofession") || normalized.equals("selectedjob");
    }

    private boolean isDataField(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.contains("manager") || normalized.contains("data")
                || normalized.contains("service") || normalized.contains("repository")
                || normalized.contains("storage");
    }

    private Profession parse(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Profession profession) {
            return profession;
        }
        if (value instanceof String string) {
            return parseText(string);
        }
        if (value instanceof Enum<?> enumValue) {
            return Profession.parse(enumValue.name());
        }
        if (value instanceof Map<?, ?> map) {
            for (Object key : new Object[]{"profession", "job", "selectedProfession", "selectedJob"}) {
                Profession profession = parse(map.get(key));
                if (profession != null) {
                    return profession;
                }
            }
            Object playerData = map.get("player");
            Profession profession = parse(playerData);
            if (profession != null) {
                return profession;
            }
        }
        for (String methodName : new String[]{"getName", "getProfession", "getType"}) {
            Object nested = invokeNoArgs(value, methodName);
            Profession profession = parse(nested);
            if (profession != null) {
                return profession;
            }
        }
        return Profession.parse(value.toString());
    }

    private Profession parseText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String plain = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', value));
        if (plain == null) {
            return null;
        }
        Profession exact = Profession.parse(plain.trim());
        if (exact != null) {
            return exact;
        }
        String normalized = plain.toUpperCase(Locale.ROOT);
        String compact = compact(normalized);
        for (Profession profession : Profession.values()) {
            if (compact.contains(compact(profession.displayName()))) {
                return profession;
            }
        }
        for (String name : new String[]{
                "FARMER", "MERCHANT", "BLACKSMITH", "ARCHITECT", "FISHERMAN", "ENCHANTER", "BAKER",
                "ZEMDIRBYS", "UKININKAS", "PREKEIVIS", "PIRKLYS", "AMATININKAS", "KALVIS",
                "MURININKAS", "ARCHITEKTAS", "ZUKLYS", "ZVEJYS", "ZYNYS", "BUREJAS",
                "KEPORIUS", "KEPEJAS"}) {
            if (compact.contains(compact(name))) {
                return Profession.parse(name);
            }
        }
        return null;
    }

    private String compact(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z]", "");
    }
}