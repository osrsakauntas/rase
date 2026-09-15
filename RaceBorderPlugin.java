package com.kodari.raceborder;

import com.kodari.raceborder.command.RaceCommand;
import com.kodari.raceborder.api.ProfessionsAdapter;
import com.kodari.raceborder.config.ConfigManager;
import com.kodari.raceborder.database.DatabaseManager;
import com.kodari.raceborder.listener.RaceListener;
import com.kodari.raceborder.manager.BorderManager;
import com.kodari.raceborder.manager.RaceManager;
import com.kodari.raceborder.manager.RaceProgressionManager;
import com.kodari.raceborder.manager.RaceGuiManager;
import com.kodari.raceborder.manager.RaceQuestManager;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class RaceBorderPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private BorderManager borderManager;
    private RaceManager raceManager;
    private RaceProgressionManager progressionManager;
    private RaceGuiManager guiManager;
    private RaceQuestManager questManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        configManager = new ConfigManager(this);
        configManager.load();
        databaseManager = new DatabaseManager(this);
        databaseManager.open();
        borderManager = new BorderManager(this);
        raceManager = new RaceManager(this, configManager, databaseManager, borderManager);
        progressionManager = new RaceProgressionManager(this, configManager, databaseManager);
        questManager = new RaceQuestManager(this, databaseManager);
        ProfessionsAdapter.register(this);
        guiManager = new RaceGuiManager(this);
        raceManager.reload();

        RaceCommand command = new RaceCommand(this);
        getCommand("rase").setExecutor(command);
        getCommand("rase").setTabCompleter(command);
        Bukkit.getPluginManager().registerEvents(new RaceListener(raceManager), this);
        Bukkit.getPluginManager().registerEvents(guiManager, this);
    }

    public void reloadRaceConfiguration() {
        reloadConfig();
        configManager.load();
        raceManager.reload();
    }

    @Override
    public void onDisable() {
        Bukkit.getServicesManager().unregisterAll(this);
        if (raceManager != null) {
            raceManager.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
    }
}