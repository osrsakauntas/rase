package com.kodari.raceborder.config;

import com.kodari.raceborder.RaceBorderPlugin;
import com.kodari.raceborder.model.BorderShape;
import com.kodari.raceborder.model.Race;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConfigManager {
    private final RaceBorderPlugin plugin;
    private final Map<String, Race> races = new LinkedHashMap<>();

    public ConfigManager(RaceBorderPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        races.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("races");
        if (section != null) for (String id : section.getKeys(false)) {
            ConfigurationSection raceSection = section.getConfigurationSection(id);
            if (raceSection == null) {
                continue;
            }
            double centerX = raceSection.getDouble("center.x");
            double centerZ = raceSection.getDouble("center.z");
            races.put(id.toLowerCase(), new Race(id.toLowerCase(), raceSection.getString("display-name", id),
                    raceSection.getString("prefix-color", "&f"), raceSection.getString("world", "world"),
                    BorderShape.parse(raceSection.getString("shape", "square")),
                    centerX, centerZ, Math.max(1.0, raceSection.getDouble("border-size", 64.0)),
                    raceSection.getDouble("spawn.x", centerX + 0.5), raceSection.getDouble("spawn.y", 100.0),
                    raceSection.getDouble("spawn.z", centerZ + 0.5), (float) raceSection.getDouble("spawn.yaw", 0.0),
                    (float) raceSection.getDouble("spawn.pitch", 0.0)));
        }

    }

    public Race getRace(String id) {
        return id == null ? null : races.get(id.toLowerCase());
    }

    public Map<String, Race> getRaces() {
        return Collections.unmodifiableMap(races);
    }

}