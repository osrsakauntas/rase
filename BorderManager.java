package com.kodari.raceborder.manager;

import com.kodari.raceborder.RaceBorderPlugin;
import com.kodari.raceborder.model.Race;
import com.cryptomorin.xseries.particles.XParticle;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BorderManager {
    private final RaceBorderPlugin plugin;
    private final Map<UUID, BukkitTask> visualBorders = new HashMap<>();

    public BorderManager(RaceBorderPlugin plugin) {
        this.plugin = plugin;
    }

    public void apply(Player player, Race race) {
        cancelVisualBorder(player);
        player.setWorldBorder(null);
        visualBorders.put(player.getUniqueId(), Bukkit.getScheduler().runTaskTimer(plugin,
                () -> drawBorder(player, race), 0L, 10L));
    }

    public void clear(Player player) {
        cancelVisualBorder(player);
        player.setWorldBorder(null);
    }

    private void cancelVisualBorder(Player player) {
        BukkitTask task = visualBorders.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    private void drawBorder(Player player, Race race) {
        if (!player.isOnline() || player.getWorld() == null || !race.getWorld().equals(player.getWorld().getName())) {
            return;
        }

        XParticle.of("END_ROD").ifPresent(particle -> drawBorder(player, race, particle));
    }

    private void drawBorder(Player player, Race race, XParticle particle) {
        Location playerLocation = player.getLocation();
        double baseY = Math.max(player.getWorld().getMinHeight(), Math.floor(playerLocation.getY()) - 1);
        double halfSize = race.getBorderSize() / 2.0;

        if (race.getShape().name().equals("CIRCLE")) {
            int points = Math.max(32, (int) Math.ceil(race.getBorderSize() * Math.PI));
            for (int i = 0; i < points; i++) {
                double angle = 2.0 * Math.PI * i / points;
                for (int level = 0; level <= 4; level++) {
                    spawnBorderParticle(player, race.getCenterX() + Math.cos(angle) * halfSize,
                            baseY + level, race.getCenterZ() + Math.sin(angle) * halfSize, particle);
                }
            }
            return;
        }

        int points = Math.max(1, (int) Math.ceil(race.getBorderSize()));
        for (int i = 0; i <= points; i++) {
            double progress = (double) i / points;
            double x = race.getCenterX() - halfSize + race.getBorderSize() * progress;
            double z = race.getCenterZ() - halfSize + race.getBorderSize() * progress;
            for (int level = 0; level <= 4; level++) {
                double y = baseY + level;
                spawnBorderParticle(player, x, y, race.getCenterZ() - halfSize, particle);
                spawnBorderParticle(player, x, y, race.getCenterZ() + halfSize, particle);
                spawnBorderParticle(player, race.getCenterX() - halfSize, y, z, particle);
                spawnBorderParticle(player, race.getCenterX() + halfSize, y, z, particle);
            }
        }
    }

    private void spawnBorderParticle(Player player, double x, double y, double z, XParticle particle) {
        player.spawnParticle(particle.get(), x, y, z, 1, 0, 0, 0, 0);
    }

    public boolean isInside(Race race, Location location) {
        if (location.getWorld() == null || !race.getWorld().equals(location.getWorld().getName())) {
            return true;
        }

        double x = location.getBlockX() + 0.5;
        double z = location.getBlockZ() + 0.5;
        double halfSize = race.getBorderSize() / 2.0;
        double offsetX = x - race.getCenterX();
        double offsetZ = z - race.getCenterZ();

        if (race.getShape().name().equals("CIRCLE")) {
            return offsetX * offsetX + offsetZ * offsetZ <= halfSize * halfSize;
        }
        return Math.abs(offsetX) <= halfSize && Math.abs(offsetZ) <= halfSize;
    }
}