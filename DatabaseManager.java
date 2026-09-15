package com.kodari.raceborder.database;

import com.kodari.raceborder.RaceBorderPlugin;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DatabaseManager {
    private final RaceBorderPlugin plugin;
    private Connection connection;

    public DatabaseManager(RaceBorderPlugin plugin) {
        this.plugin = plugin;
    }

    public void open() {
        try {
            File file = new File(plugin.getDataFolder(), plugin.getConfig().getString("database.file", "races.db"));
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("Could not create the plugin data folder.");
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_races (uuid TEXT PRIMARY KEY, race_id TEXT NOT NULL)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS race_reputation (race_id TEXT PRIMARY KEY, reputation INTEGER NOT NULL DEFAULT 0)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS race_quest_progress (race_id TEXT NOT NULL, quest_id TEXT NOT NULL, requirement_index INTEGER NOT NULL, progress INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (race_id, quest_id, requirement_index))");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS race_quest_completed (race_id TEXT NOT NULL, quest_id TEXT NOT NULL, PRIMARY KEY (race_id, quest_id))");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS race_quest_active (race_id TEXT PRIMARY KEY, quest_id TEXT NOT NULL)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS race_quest_cancel_cooldown (race_id TEXT PRIMARY KEY, available_at INTEGER NOT NULL)");
                statement.executeUpdate("DROP TABLE IF EXISTS player_professions");
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("Unable to open the race database: " + exception.getMessage());
        }
    }

    public void assign(UUID playerId, String raceId) {
        if (connection == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("INSERT OR REPLACE INTO player_races (uuid, race_id) VALUES (?, ?)")) {
            statement.setString(1, playerId.toString());
            statement.setString(2, raceId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Unable to save player race: " + exception.getMessage());
        }
    }

    public String findRace(UUID playerId) {
        if (connection == null) {
            return null;
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT race_id FROM player_races WHERE uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString("race_id") : null;
            }
        } catch (SQLException exception) {
            return null;
        }
    }

    public void remove(UUID playerId) {
        if (connection == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM player_races WHERE uuid = ?")) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Unable to remove player race: " + exception.getMessage());
        }
    }

    public void removeRace(String raceId) {
        if (connection == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM player_races WHERE race_id = ?")) {
            statement.setString(1, raceId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Unable to remove race assignments: " + exception.getMessage());
        }
    }

    public Map<UUID, String> findAssignments() {
        Map<UUID, String> assignments = new HashMap<>();
        if (connection == null) {
            return assignments;
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT uuid, race_id FROM player_races");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                assignments.put(UUID.fromString(result.getString("uuid")), result.getString("race_id"));
            }
        } catch (SQLException | IllegalArgumentException exception) {
            plugin.getLogger().warning("Unable to load race assignments: " + exception.getMessage());
        }
        return assignments;
    }

    public Map<UUID, String> findPlayersByRace(String raceId) {
        Map<UUID, String> assignments = new HashMap<>();
        if (connection == null) {
            return assignments;
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT uuid, race_id FROM player_races WHERE race_id = ?")) {
            statement.setString(1, raceId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    assignments.put(UUID.fromString(result.getString("uuid")), result.getString("race_id"));
                }
            }
        } catch (SQLException | IllegalArgumentException exception) {
            plugin.getLogger().warning("Unable to load race assignments: " + exception.getMessage());
        }
        return assignments;
    }

    public int getRaceReputation(String raceId) {
        if (connection == null) {
            return 0;
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT reputation FROM race_reputation WHERE race_id = ?")) {
            statement.setString(1, raceId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt("reputation") : 0;
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Unable to load race reputation: " + exception.getMessage());
            return 0;
        }
    }

    public int addRaceReputation(String raceId, int amount) {
        if (connection == null || amount <= 0) {
            return getRaceReputation(raceId);
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO race_reputation (race_id, reputation) VALUES (?, ?) "
                        + "ON CONFLICT(race_id) DO UPDATE SET reputation = reputation + excluded.reputation")) {
            statement.setString(1, raceId);
            statement.setInt(2, amount);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Unable to save race reputation: " + exception.getMessage());
        }
        return getRaceReputation(raceId);
    }

    public void setRaceReputation(String raceId, int reputation) {
        if (connection == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO race_reputation (race_id, reputation) VALUES (?, ?) "
                        + "ON CONFLICT(race_id) DO UPDATE SET reputation = excluded.reputation")) {
            statement.setString(1, raceId);
            statement.setInt(2, Math.max(0, reputation));
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Unable to set race reputation: " + exception.getMessage());
        }
    }

    public void removeRaceProgress(String raceId) {
        if (connection == null) {
            return;
        }
        try (PreparedStatement reputation = connection.prepareStatement("DELETE FROM race_reputation WHERE race_id = ?");
             PreparedStatement questProgress = connection.prepareStatement("DELETE FROM race_quest_progress WHERE race_id = ?");
             PreparedStatement questCompleted = connection.prepareStatement("DELETE FROM race_quest_completed WHERE race_id = ?");
             PreparedStatement questActive = connection.prepareStatement("DELETE FROM race_quest_active WHERE race_id = ?");
             PreparedStatement questCooldown = connection.prepareStatement("DELETE FROM race_quest_cancel_cooldown WHERE race_id = ?")) {
            reputation.setString(1, raceId);
            reputation.executeUpdate();
            questProgress.setString(1, raceId);
            questProgress.executeUpdate();
            questCompleted.setString(1, raceId);
            questCompleted.executeUpdate();
            questActive.setString(1, raceId);
            questActive.executeUpdate();
            questCooldown.setString(1, raceId);
            questCooldown.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Unable to remove race progression: " + exception.getMessage());
        }
    }

    public int getQuestProgress(String raceId, String questId, int requirementIndex) {
        if (connection == null) {
            return 0;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT progress FROM race_quest_progress WHERE race_id = ? AND quest_id = ? AND requirement_index = ?")) {
            statement.setString(1, raceId);
            statement.setString(2, questId);
            statement.setInt(3, requirementIndex);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt("progress") : 0;
            }
        } catch (SQLException exception) {
            return 0;
        }
    }

    public void setQuestProgress(String raceId, String questId, int requirementIndex, int progress) {
        if (connection == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO race_quest_progress (race_id, quest_id, requirement_index, progress) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT(race_id, quest_id, requirement_index) DO UPDATE SET progress = excluded.progress")) {
            statement.setString(1, raceId);
            statement.setString(2, questId);
            statement.setInt(3, requirementIndex);
            statement.setInt(4, Math.max(0, progress));
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Unable to save race quest progress: " + exception.getMessage());
        }
    }

    public boolean isQuestCompleted(String raceId, String questId) {
        if (connection == null) {
            return false;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM race_quest_completed WHERE race_id = ? AND quest_id = ?")) {
            statement.setString(1, raceId);
            statement.setString(2, questId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            return false;
        }
    }

    public void completeQuest(String raceId, String questId) {
        if (connection == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR IGNORE INTO race_quest_completed (race_id, quest_id) VALUES (?, ?)")) {
            statement.setString(1, raceId);
            statement.setString(2, questId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Unable to save completed race quest: " + exception.getMessage());
        }
    }

    public String getActiveQuest(String raceId) {
        if (connection == null) {
            return null;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT quest_id FROM race_quest_active WHERE race_id = ?")) {
            statement.setString(1, raceId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString("quest_id") : null;
            }
        } catch (SQLException exception) {
            return null;
        }
    }

    public boolean takeQuest(String raceId, String questId) {
        if (connection == null || getActiveQuest(raceId) != null) {
            return false;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO race_quest_active (race_id, quest_id) VALUES (?, ?)")) {
            statement.setString(1, raceId);
            statement.setString(2, questId);
            statement.executeUpdate();
            return true;
        } catch (SQLException exception) {
            return false;
        }
    }

    public long getQuestCancelCooldown(String raceId) {
        if (connection == null) {
            return 0L;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT available_at FROM race_quest_cancel_cooldown WHERE race_id = ?")) {
            statement.setString(1, raceId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong("available_at") : 0L;
            }
        } catch (SQLException exception) {
            return 0L;
        }
    }

    public void setQuestCancelCooldown(String raceId, long availableAt) {
        if (connection == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO race_quest_cancel_cooldown (race_id, available_at) VALUES (?, ?) "
                        + "ON CONFLICT(race_id) DO UPDATE SET available_at = excluded.available_at")) {
            statement.setString(1, raceId);
            statement.setLong(2, availableAt);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Unable to save race quest cooldown: " + exception.getMessage());
        }
    }

    public void clearQuestCancelCooldown(String raceId) {
        if (connection == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM race_quest_cancel_cooldown WHERE race_id = ?")) {
            statement.setString(1, raceId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Unable to clear race quest cooldown: " + exception.getMessage());
        }
    }

    public void clearActiveQuest(String raceId) {
        if (connection == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM race_quest_active WHERE race_id = ?")) {
            statement.setString(1, raceId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Unable to clear active race quest: " + exception.getMessage());
        }
    }

    public boolean cancelQuest(String raceId, String questId) {
        if (connection == null) {
            return false;
        }
        try (PreparedStatement active = connection.prepareStatement(
                "DELETE FROM race_quest_active WHERE race_id = ? AND quest_id = ?");
             PreparedStatement progress = connection.prepareStatement(
                     "DELETE FROM race_quest_progress WHERE race_id = ? AND quest_id = ?")) {
            active.setString(1, raceId);
            active.setString(2, questId);
            int cancelled = active.executeUpdate();
            if (cancelled == 0) {
                return false;
            }
            progress.setString(1, raceId);
            progress.setString(2, questId);
            progress.executeUpdate();
            return true;
        } catch (SQLException exception) {
            plugin.getLogger().warning("Unable to cancel race quest: " + exception.getMessage());
            return false;
        }
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }
}