package com.kodari.raceborder.manager;

import com.cryptomorin.xseries.XMaterial;
import com.kodari.raceborder.RaceBorderPlugin;
import com.kodari.raceborder.config.ConfigManager;
import com.kodari.raceborder.database.DatabaseManager;
import com.kodari.raceborder.model.Race;
import com.kodari.raceborder.model.RacePhase;
import com.kodari.raceborder.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.Locale;

public class RaceProgressionManager {
    private final RaceBorderPlugin plugin;
    private final ConfigManager configManager;
    private final DatabaseManager database;

    public RaceProgressionManager(RaceBorderPlugin plugin, ConfigManager configManager, DatabaseManager database) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.database = database;
    }

    public int getReputation(String raceId) {
        return database.getRaceReputation(raceId);
    }

    public RacePhase getPhase(String raceId) {
        int reputation = getReputation(raceId);
        RacePhase phase = RacePhase.STONE;
        for (RacePhase candidate : RacePhase.values()) {
            if (reputation >= threshold(candidate)) {
                phase = candidate;
            }
        }
        return phase;
    }

    public boolean isItemAllowed(Player player, ItemStack item) {
        if (item == null || item.getType().isAir() || !isProgressionItem(item)) {
            return true;
        }
        Race race = plugin.getRaceManager().getRace(player);
        if (race == null) {
            return false;
        }
        RacePhase currentPhase = getPhase(race.getId());
        RacePhase requiredPhase = requiredPhase(item);
        return currentPhase.ordinal() >= requiredPhase.ordinal();
    }

    public void sendItemLocked(Player player, ItemStack item) {
        if (item == null) {
            return;
        }
        String material = XMaterial.matchXMaterial(item.getType()).name();
        int itemTier = itemTier(material);
        RacePhase requiredPhase = RacePhase.NETHERITE;
        for (RacePhase phase : RacePhase.values()) {
            if (itemTier <= unlockedItemTier(phase)) {
                requiredPhase = phase;
                break;
            }
        }
        MessageUtil.send(plugin, player, "item-locked", Map.of(
                "item", material,
                "phase", requiredPhase.name()));
    }

    private int unlockedItemTier(RacePhase phase) {
        return switch (phase) {
            case STONE -> 1;
            case COPPER -> 2;
            case IRON -> 3;
            case DIAMOND -> 4;
            case NETHERITE -> 5;
        };
    }

    private RacePhase requiredPhase(ItemStack item) {
        return switch (itemTier(XMaterial.matchXMaterial(item.getType()).name())) {
            case 0, 1 -> RacePhase.STONE;
            case 2 -> RacePhase.COPPER;
            case 3 -> RacePhase.IRON;
            case 4 -> RacePhase.DIAMOND;
            default -> RacePhase.NETHERITE;
        };
    }

    private boolean isProgressionItem(ItemStack item) {
        String material = XMaterial.matchXMaterial(item.getType()).name();
        return material.endsWith("_PICKAXE") || material.endsWith("_AXE")
                || material.endsWith("_SHOVEL") || material.endsWith("_HOE")
                || material.endsWith("_SWORD") || material.endsWith("_HELMET")
                || material.endsWith("_CHESTPLATE") || material.endsWith("_LEGGINGS")
                || material.endsWith("_BOOTS") || material.equals("TURTLE_HELMET");
    }

    public boolean isArmorItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        String material = XMaterial.matchXMaterial(item.getType()).name();
        return material.endsWith("_HELMET") || material.endsWith("_CHESTPLATE")
                || material.endsWith("_LEGGINGS") || material.endsWith("_BOOTS")
                || material.equals("TURTLE_HELMET");
    }

    private int itemTier(String material) {
        if (material.startsWith("WOODEN_") || material.startsWith("LEATHER_")) {
            return 0;
        }
        if (material.startsWith("STONE_") || material.startsWith("CHAINMAIL_")) {
            return 1;
        }
        if (material.startsWith("COPPER_")) {
            return 2;
        }
        if (material.startsWith("IRON_") || material.startsWith("GOLDEN_")
                || material.equals("TURTLE_HELMET")) {
            return 3;
        }
        if (material.startsWith("DIAMOND_")) {
            return 4;
        }
        if (material.startsWith("NETHERITE_")) {
            return 5;
        }
        return 0;
    }

    public int threshold(RacePhase phase) {
        return Math.max(0, plugin.getConfig().getInt("progression.phases." + phase.name() + ".reputation",
                phase == RacePhase.STONE ? 0 : 10000));
    }

    public int nextThreshold(String raceId) {
        RacePhase current = getPhase(raceId);
        if (current == RacePhase.NETHERITE) {
            return threshold(current);
        }
        return threshold(RacePhase.values()[current.ordinal() + 1]);
    }

    public void addReputation(Player player, String raceId, int amount, String source) {
        if (amount <= 0) {
            return;
        }
        Race race = configManager.getRace(raceId);
        if (race == null) {
            return;
        }
        int oldReputation = getReputation(raceId);
        int newReputation = addReputation(raceId, amount);
        MessageUtil.send(plugin, player, "reputation-gained", Map.of(
                "race", race.getDisplayName(),
                "amount", String.valueOf(amount),
                "reputation", String.valueOf(newReputation)));
    }

    public int addReputation(String raceId, int amount) {
        if (amount <= 0 || configManager.getRace(raceId) == null) {
            return getReputation(raceId);
        }
        Race race = configManager.getRace(raceId);
        int oldReputation = getReputation(raceId);
        int newReputation = database.addRaceReputation(raceId, amount);
        RacePhase oldPhase = phaseAt(oldReputation);
        RacePhase newPhase = phaseAt(newReputation);
        if (newPhase != oldPhase) {
            MessageUtil.broadcast(plugin, "phase-unlocked", Map.of(
                    "race", race.getDisplayName(), "phase", newPhase.name()));
        }
        return newReputation;
    }

    public boolean promoteToPhase(String raceId, RacePhase targetPhase) {
        Race race = configManager.getRace(raceId);
        if (race == null || targetPhase == null) {
            return false;
        }
        RacePhase currentPhase = getPhase(raceId);
        if (targetPhase.ordinal() <= currentPhase.ordinal()) {
            return false;
        }
        database.setRaceReputation(raceId, threshold(targetPhase));
        MessageUtil.broadcast(plugin, "phase-unlocked", Map.of(
                "race", race.getDisplayName(), "phase", targetPhase.name()));
        return true;
    }

    public boolean demoteToPhase(String raceId, RacePhase targetPhase) {
        Race race = configManager.getRace(raceId);
        if (race == null || targetPhase == null) {
            return false;
        }
        RacePhase currentPhase = getPhase(raceId);
        if (targetPhase.ordinal() >= currentPhase.ordinal()) {
            return false;
        }
        database.setRaceReputation(raceId, threshold(targetPhase));
        return true;
    }

    public int removeReputation(String raceId, int amount) {
        if (amount <= 0 || configManager.getRace(raceId) == null) {
            return getReputation(raceId);
        }
        int reputation = Math.max(0, getReputation(raceId) - amount);
        database.setRaceReputation(raceId, reputation);
        return reputation;
    }

    private RacePhase phaseAt(int reputation) {
        RacePhase phase = RacePhase.STONE;
        for (RacePhase candidate : RacePhase.values()) {
            if (reputation >= threshold(candidate)) {
                phase = candidate;
            }
        }
        return phase;
    }

    public boolean setCauldron(Player player, String raceId) {
        Race race = configManager.getRace(raceId);
        if (race == null) {
            return false;
        }
        var location = player.getLocation().getBlock().getLocation();
        XMaterial.matchXMaterial("CAULDRON").map(XMaterial::parseMaterial).ifPresent(location.getBlock()::setType);
        removeConfiguredHologram(race.getId());
        var signBlock = location.clone().add(0, 1, 0).getBlock();
        if (signBlock.getState() instanceof org.bukkit.block.Sign) {
            XMaterial.matchXMaterial("AIR").map(XMaterial::parseMaterial).ifPresent(signBlock::setType);
        }
        ArmorStand hologram = (ArmorStand) location.getWorld().spawnEntity(
                location.clone().add(0.5, 1.25, 0.5), EntityType.ARMOR_STAND);
        hologram.setInvisible(true);
        hologram.setMarker(true);
        hologram.setGravity(false);
        hologram.setInvulnerable(true);
        hologram.setBasePlate(false);
        hologram.setCustomName(MessageUtil.color("&6Rases Katilas"));
        hologram.setCustomNameVisible(true);
        String path = "races." + race.getId() + ".cauldron";
        plugin.getConfig().set(path + ".world", location.getWorld().getName());
        plugin.getConfig().set(path + ".x", location.getBlockX());
        plugin.getConfig().set(path + ".y", location.getBlockY());
        plugin.getConfig().set(path + ".z", location.getBlockZ());
        plugin.getConfig().set(path + ".hologram-uuid", hologram.getUniqueId().toString());
        plugin.saveConfig();
        return true;
    }

    private void removeConfiguredHologram(String raceId) {
        String uuidValue = plugin.getConfig().getString("races." + raceId + ".cauldron.hologram-uuid", "");
        if (uuidValue.isEmpty()) {
            return;
        }
        try {
            Entity entity = Bukkit.getEntity(java.util.UUID.fromString(uuidValue));
            if (entity != null) {
                entity.remove();
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    public boolean addOffering(String raceId, String material, int amount, int reputation) {
        Race race = configManager.getRace(raceId);
        if (race == null || amount <= 0 || reputation <= 0 || XMaterial.matchXMaterial(material).isEmpty()) {
            return false;
        }
        String normalized = XMaterial.matchXMaterial(material).get().name();
        for (Race configuredRace : configManager.getRaces().values()) {
            String path = "races." + configuredRace.getId() + ".cauldron.offerings." + normalized;
            plugin.getConfig().set(path + ".amount", amount);
            plugin.getConfig().set(path + ".reputation", calculateOfferingReputation(normalized, amount));
            plugin.getConfig().set(path + ".deposited", 0);
            plugin.getConfig().set("races." + configuredRace.getId()
                    + ".cauldron.offerings-completed", false);
        }
        plugin.saveConfig();
        return true;
    }

    public int calculateOfferingReputation(String material, int amount) {
        String normalized = material.toUpperCase(Locale.ROOT);
        int unitReputation = switch (normalized) {
            case "DIRT", "COARSE_DIRT", "ROOTED_DIRT", "SAND", "RED_SAND", "GRAVEL",
                    "COBBLESTONE", "NETHERRACK", "END_STONE", "CLAY", "SNOWBALL" -> 1;
            case "STONE", "DEEPSLATE", "BLACKSTONE", "ANDESITE", "DIORITE", "GRANITE",
                    "TUFF", "CALCITE", "BASALT", "BRICKS", "STONE_BRICKS", "GLASS",
                    "TORCH", "REDSTONE_TORCH", "LADDER", "SCAFFOLDING" -> 2;
            case "OAK_LOG", "SPRUCE_LOG", "BIRCH_LOG", "JUNGLE_LOG", "ACACIA_LOG", "DARK_OAK_LOG",
                    "MANGROVE_LOG", "CHERRY_LOG", "BAMBOO_BLOCK", "OAK_PLANKS", "SPRUCE_PLANKS",
                    "BIRCH_PLANKS", "JUNGLE_PLANKS", "ACACIA_PLANKS", "DARK_OAK_PLANKS",
                    "MANGROVE_PLANKS", "CHERRY_PLANKS", "BAMBOO_PLANKS" -> 3;
            case "LANTERN" -> 12;
            case "SOUL_LANTERN" -> 16;
            case "COAL", "CHARCOAL", "FLINT", "BONE", "STRING", "FEATHER", "WHEAT", "PAPER",
                    "BOOK", "QUARTZ" -> 4;
            case "COPPER_INGOT" -> 6;
            case "COPPER_BLOCK" -> 54;
            case "RAW_COPPER", "COPPER_ORE", "DEEPSLATE_COPPER_ORE" -> 4;
            case "IRON_INGOT" -> 10;
            case "IRON_BLOCK" -> 90;
            case "RAW_IRON", "IRON_ORE", "DEEPSLATE_IRON_ORE" -> 8;
            case "GOLD_INGOT" -> 20;
            case "GOLD_BLOCK" -> 180;
            case "RAW_GOLD", "GOLD_ORE", "DEEPSLATE_GOLD_ORE" -> 16;
            case "REDSTONE" -> 5;
            case "LAPIS_LAZULI" -> 7;
            case "AMETHYST_SHARD" -> 15;
            case "OBSIDIAN" -> 35;
            case "ENDER_PEARL" -> 45;
            case "BLAZE_ROD" -> 60;
            case "GHAST_TEAR" -> 100;
            case "SLIME_BALL" -> 25;
            case "MAGMA_CREAM" -> 35;
            case "DIAMOND" -> 125;
            case "DIAMOND_BLOCK" -> 1125;
            case "EMERALD" -> 150;
            case "EMERALD_BLOCK" -> 1350;
            case "ANCIENT_DEBRIS" -> 450;
            case "NETHERITE_SCRAP" -> 225;
            case "NETHERITE_INGOT" -> 900;
            case "NETHERITE_BLOCK" -> 8100;
            case "NETHER_STAR" -> 2500;
            case "ELYTRA" -> 2000;
            case "DRAGON_EGG" -> 5000;
            case "DRAGON_HEAD" -> 1500;
            case "ENCHANTED_GOLDEN_APPLE" -> 1200;
            case "BEACON" -> 1800;
            case "CONDUIT" -> 1000;
            default -> craftedOfferingReputation(normalized);
        };
        return Math.max(1, unitReputation * Math.max(1, amount));
    }

    private int craftedOfferingReputation(String material) {
        if (material.equals("GOLDEN_APPLE")) {
            return 180;
        }
        if (material.endsWith("_PICKAXE") || material.endsWith("_AXE")
                || material.endsWith("_SHOVEL") || material.endsWith("_HOE")
                || material.endsWith("_SWORD")) {
            if (material.startsWith("NETHERITE_")) {
                return 3000;
            }
            if (material.startsWith("DIAMOND_")) {
                return 450;
            }
            if (material.startsWith("GOLDEN_")) {
                return 90;
            }
            if (material.startsWith("IRON_")) {
                return 75;
            }
            if (material.startsWith("STONE_")) {
                return 12;
            }
            if (material.startsWith("WOODEN_")) {
                return 8;
            }
        }
        if (material.endsWith("_HELMET") || material.endsWith("_CHESTPLATE")
                || material.endsWith("_LEGGINGS") || material.endsWith("_BOOTS")) {
            if (material.startsWith("NETHERITE_")) {
                return 4500;
            }
            if (material.startsWith("DIAMOND_")) {
                return 700;
            }
            if (material.startsWith("GOLDEN_")) {
                return 120;
            }
            if (material.startsWith("IRON_")) {
                return 110;
            }
            if (material.startsWith("CHAINMAIL_")) {
                return 160;
            }
            if (material.startsWith("LEATHER_")) {
                return 25;
            }
        }
        if (material.endsWith("_BLOCK")) {
            return 20;
        }
        return 3;
    }

    public boolean handleCauldron(Player player, Block block) {
        for (Race race : configManager.getRaces().values()) {
            String path = "races." + race.getId() + ".cauldron";
            if (!isConfiguredLocation(path, block)) {
                continue;
            }
            Race playerRace = plugin.getRaceManager().getRace(player);
            if (playerRace == null || !playerRace.getId().equals(race.getId())) {
                MessageUtil.send(plugin, player, "wrong-race-cauldron", Map.of("race", race.getDisplayName()));
                return true;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) {
                MessageUtil.send(plugin, player, "cauldron-help");
                return true;
            }
            return processOffering(player, race, hand);
        }
        return false;
    }

    public boolean handleDroppedOffering(Player player, Item droppedItem) {
        Race race = findNearbyCauldronRace(droppedItem.getLocation());
        if (race == null) {
            return false;
        }
        Race playerRace = plugin.getRaceManager().getRace(player);
        if (playerRace == null || !playerRace.getId().equals(race.getId())) {
            MessageUtil.send(plugin, player, "wrong-race-cauldron", Map.of("race", race.getDisplayName()));
            return true;
        }
        processOffering(player, race, droppedItem.getItemStack());
        if (droppedItem.isValid()) {
            ItemStack remaining = droppedItem.getItemStack();
            if (remaining.getType().isAir() || remaining.getAmount() <= 0) {
                droppedItem.remove();
            } else {
                droppedItem.setItemStack(remaining);
            }
        }
        return true;
    }

    private boolean processOffering(Player player, Race race, ItemStack offering) {
        if (offering.getType().isAir()) {
            MessageUtil.send(plugin, player, "cauldron-help");
            return true;
        }
        String material = XMaterial.matchXMaterial(offering.getType()).name();
        String offerPath = "races." + race.getId() + ".cauldron.offerings." + material;
        if (!plugin.getConfig().contains(offerPath)) {
            MessageUtil.send(plugin, player, "invalid-offering");
            return true;
        }
        int required = plugin.getConfig().getInt(offerPath + ".amount", 1);
        int deposited = plugin.getConfig().getInt(offerPath + ".deposited", 0);
        int remaining = Math.max(0, required - deposited);
        if (remaining == 0) {
            MessageUtil.send(plugin, player, "offering-already-complete", Map.of("item", material));
            return true;
        }
        int accepted = Math.min(offering.getAmount(), remaining);
        offering.setAmount(offering.getAmount() - accepted);
        plugin.getConfig().set(offerPath + ".deposited", deposited + accepted);
        plugin.saveConfig();

        if (areAllOfferingsComplete(race.getId())) {
            if (!plugin.getConfig().getBoolean("races." + race.getId() + ".cauldron.offerings-completed", false)) {
                int reputation = totalOfferingReputation(race.getId());
                plugin.getConfig().set("races." + race.getId() + ".cauldron.offerings-completed", true);
                plugin.saveConfig();
                addReputation(player, race.getId(), reputation, "cauldron");
                MessageUtil.send(plugin, player, "offerings-complete", Map.of(
                        "reputation", String.valueOf(reputation)));
            }
        } else {
            MessageUtil.send(plugin, player, "offering-progress", Map.of(
                    "item", material,
                    "deposited", String.valueOf(deposited + accepted),
                    "amount", String.valueOf(required)));
        }
        return true;
    }

    private boolean areAllOfferingsComplete(String raceId) {
        String path = "races." + raceId + ".cauldron.offerings";
        if (!plugin.getConfig().isConfigurationSection(path)) {
            return false;
        }
        for (String material : plugin.getConfig().getConfigurationSection(path).getKeys(false)) {
            int required = plugin.getConfig().getInt(path + "." + material + ".amount", 1);
            int deposited = plugin.getConfig().getInt(path + "." + material + ".deposited", 0);
            if (deposited < required) {
                return false;
            }
        }
        return true;
    }

    private int totalOfferingReputation(String raceId) {
        String path = "races." + raceId + ".cauldron.offerings";
        int reputation = 0;
        if (!plugin.getConfig().isConfigurationSection(path)) {
            return reputation;
        }
        for (String material : plugin.getConfig().getConfigurationSection(path).getKeys(false)) {
            int amount = plugin.getConfig().getInt(path + "." + material + ".amount", 1);
            reputation += calculateOfferingReputation(material, amount);
        }
        return reputation;
    }

    private Race findNearbyCauldronRace(Location location) {
        Race nearest = null;
        double nearestDistance = 3.5 * 3.5;
        for (Race race : configManager.getRaces().values()) {
            String path = "races." + race.getId() + ".cauldron";
            String worldName = plugin.getConfig().getString(path + ".world", "");
            if (!worldName.equals(location.getWorld().getName())) {
                continue;
            }
            Location cauldron = new Location(location.getWorld(),
                    plugin.getConfig().getInt(path + ".x") + 0.5,
                    plugin.getConfig().getInt(path + ".y") + 0.5,
                    plugin.getConfig().getInt(path + ".z") + 0.5);
            double distance = location.distanceSquared(cauldron);
            if (distance <= nearestDistance) {
                nearest = race;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    public boolean isConfiguredCauldron(Block block) {
        for (Race race : configManager.getRaces().values()) {
            if (isConfiguredLocation("races." + race.getId() + ".cauldron", block)) {
                return true;
            }
        }
        return false;
    }

    public boolean handleCauldronBreak(Player player, Block block) {
        for (Race race : configManager.getRaces().values()) {
            String path = "races." + race.getId() + ".cauldron";
            if (!isConfiguredLocation(path, block)) {
                continue;
            }
            boolean admin = player.isOp() || player.hasPermission("raceborder.admin");
            if (!admin) {
                Race playerRace = plugin.getRaceManager().getRace(player);
                if (playerRace != null && playerRace.getId().equals(race.getId())) {
                    MessageUtil.send(plugin, player, "cauldron-protected");
                }
                return false;
            }
            removeConfiguredHologram(race.getId());
            plugin.getConfig().set(path, null);
            plugin.saveConfig();
            return true;
        }
        return false;
    }

    private boolean isConfiguredLocation(String path, Block block) {
        return plugin.getConfig().getString(path + ".world", "").equals(block.getWorld().getName())
                && plugin.getConfig().getInt(path + ".x") == block.getX()
                && plugin.getConfig().getInt(path + ".y") == block.getY()
                && plugin.getConfig().getInt(path + ".z") == block.getZ();
    }

    public boolean upgradeHeldItem(Player player) {
        Race race = plugin.getRaceManager().getRace(player);
        if (race == null) {
            return false;
        }
        RacePhase phase = getPhase(race.getId());
        String targetBase = plugin.getConfig().getString("progression.upgrades." + phase.name() + ".material", "");
        if (targetBase.isEmpty()) {
            MessageUtil.send(plugin, player, "upgrade-locked", Map.of("phase", phase.name()));
            return false;
        }
        ItemStack oldItem = player.getInventory().getItemInMainHand();
        String oldName = XMaterial.matchXMaterial(oldItem.getType()).name();
        String suffix = upgradeSuffix(oldName);
        if (suffix == null) {
            MessageUtil.send(plugin, player, "upgrade-invalid");
            return false;
        }
        ItemStack upgraded = XMaterial.matchXMaterial(targetBase.toUpperCase(Locale.ROOT) + suffix)
                .map(XMaterial::parseItem).orElse(null);
        if (upgraded == null) {
            MessageUtil.send(plugin, player, "upgrade-invalid");
            return false;
        }
        ItemMeta meta = oldItem.getItemMeta();
        if (meta != null) {
            upgraded.setItemMeta(meta);
        }
        upgraded.setAmount(oldItem.getAmount());
        player.getInventory().setItemInMainHand(upgraded);
        MessageUtil.send(plugin, player, "item-upgraded", Map.of("phase", phase.name()));
        return true;
    }

    private String upgradeSuffix(String name) {
        for (String suffix : new String[]{"_PICKAXE", "_AXE", "_SHOVEL", "_HOE", "_SWORD", "_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS"}) {
            if (name.endsWith(suffix)) {
                return suffix;
            }
        }
        return null;
    }

    public void removeRace(String raceId) {
        database.removeRaceProgress(raceId);
    }
}