package com.kodari.raceborder.manager;

import com.cryptomorin.xseries.XMaterial;
import com.kodari.raceborder.RaceBorderPlugin;
import com.kodari.raceborder.api.ProfessionProvider;
import com.kodari.raceborder.database.DatabaseManager;
import com.kodari.raceborder.model.Profession;
import com.kodari.raceborder.model.QuestAction;
import com.kodari.raceborder.model.Race;
import com.kodari.raceborder.model.RacePhase;
import com.kodari.raceborder.model.RaceQuest;
import com.kodari.raceborder.model.RaceQuestRequirement;
import com.kodari.raceborder.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class RaceQuestManager {
    private static final long QUEST_CANCEL_COOLDOWN_MILLIS = 10 * 60 * 1000L;
    private final RaceBorderPlugin plugin;
    private final DatabaseManager database;
    private final Map<RacePhase, List<RaceQuest>> quests = new EnumMap<>(RacePhase.class);

    public RaceQuestManager(RaceBorderPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
        createQuests();
    }

    public List<RaceQuest> getVisibleQuests(String raceId) {
        RacePhase phase = plugin.getProgressionManager().getPhase(raceId);
        if (phase == RacePhase.NETHERITE) {
            return List.of();
        }
        return quests.getOrDefault(phase, List.of());
    }

    public int getProgress(String raceId, RaceQuest quest, RaceQuestRequirement requirement) {
        return database.getQuestProgress(raceId, quest.getId(), requirement.getIndex());
    }

    public boolean isCompleted(String raceId, RaceQuest quest) {
        return database.isQuestCompleted(raceId, quest.getId());
    }

    public RaceQuest getActiveQuest(String raceId) {
        String questId = database.getActiveQuest(raceId);
        if (questId == null) {
            return null;
        }
        return quests.values().stream()
                .flatMap(List::stream)
                .filter(quest -> quest.getId().equals(questId))
                .findFirst().orElse(null);
    }

    public boolean takeQuest(String raceId, RaceQuest quest) {
        if (plugin.getProgressionManager().getPhase(raceId) == RacePhase.NETHERITE
                || quest.getPhase() == RacePhase.NETHERITE
                || isCompleted(raceId, quest) || getActiveQuest(raceId) != null) {
            return false;
        }
        if (getCancelCooldownRemaining(raceId) > 0L) {
            return false;
        }
        database.clearQuestCancelCooldown(raceId);
        return database.takeQuest(raceId, quest.getId());
    }

    public boolean cancelQuest(String raceId, RaceQuest quest) {
        RaceQuest active = getActiveQuest(raceId);
        if (active == null || !active.getId().equals(quest.getId())) {
            return false;
        }
        if (!database.cancelQuest(raceId, quest.getId())) {
            return false;
        }
        database.setQuestCancelCooldown(raceId, System.currentTimeMillis() + QUEST_CANCEL_COOLDOWN_MILLIS);
        return true;
    }

    public long getCancelCooldownRemaining(String raceId) {
        return Math.max(0L, database.getQuestCancelCooldown(raceId) - System.currentTimeMillis());
    }

    public int reward(String raceId) {
        RaceQuest active = getActiveQuest(raceId);
        if (active != null) {
            return reward(raceId, active);
        }
        List<RaceQuest> visible = getVisibleQuests(raceId);
        return visible.isEmpty() ? 0 : reward(raceId, visible.get(0));
    }

    public int reward(String raceId, RaceQuest quest) {
        RacePhase phase = plugin.getProgressionManager().getPhase(raceId);
        if (phase == RacePhase.NETHERITE) {
            return 0;
        }
        int remaining = Math.max(1, plugin.getProgressionManager().nextThreshold(raceId)
                - plugin.getProgressionManager().getReputation(raceId));
        int phaseGap = Math.max(1, plugin.getProgressionManager().threshold(RacePhase.values()[phase.ordinal() + 1])
                - plugin.getProgressionManager().threshold(phase));
        double difficultyRate = 0.03D + phase.ordinal() * 0.005D + quest.getDifficulty() * 0.004D;
        return Math.max(1, Math.min(remaining, (int) Math.ceil(phaseGap * difficultyRate)));
    }

    public Profession getExternalProfession(Player player) {
        RegisteredServiceProvider<ProfessionProvider> registration = Bukkit.getServicesManager()
                .getRegistration(ProfessionProvider.class);
        return registration == null ? null : registration.getProvider().getProfession(player);
    }

    public void record(Player player, QuestAction action, String target, int amount) {
        if (amount <= 0) {
            return;
        }
        Race race = plugin.getRaceManager().getRace(player);
        Profession profession = getExternalProfession(player);
        if (race == null || profession == null) {
            return;
        }
        RaceQuest activeQuest = getActiveQuest(race.getId());
        if (activeQuest == null) {
            return;
        }
        String normalizedTarget = target == null ? "ANY" : target.toUpperCase(java.util.Locale.ROOT);
        boolean tradeResult = normalizedTarget.startsWith("TRADE_RESULT:");
        String actualTarget = tradeResult ? normalizedTarget.substring("TRADE_RESULT:".length()) : normalizedTarget;
        boolean changed = false;
        for (RaceQuestRequirement requirement : activeQuest.getRequirements()) {
            if (requirement.getProfession() != profession || requirement.getAction() != action
                    || !matchesTarget(requirement.getTarget(), actualTarget)) {
                continue;
            }
            int current = database.getQuestProgress(race.getId(), activeQuest.getId(), requirement.getIndex());
            int progressAmount = tradeResult && "ANY".equalsIgnoreCase(requirement.getTarget()) ? 1 : amount;
            int updated = Math.min(requirement.getAmount(), current + progressAmount);
            if (updated != current) {
                database.setQuestProgress(race.getId(), activeQuest.getId(), requirement.getIndex(), updated);
                changed = true;
            }
        }
        if (changed && isQuestComplete(race.getId(), activeQuest)) {
            database.completeQuest(race.getId(), activeQuest.getId());
            int reward = reward(race.getId(), activeQuest);
            plugin.getProgressionManager().addReputation(race.getId(), reward);
            database.clearActiveQuest(race.getId());
            announceCompletion(race, activeQuest, reward);
        }
    }

    private boolean matchesTarget(String required, String actual) {
        if (required == null || required.equalsIgnoreCase("ANY")) {
            return true;
        }
        if (required.equalsIgnoreCase("TOOLS_OR_ARMOR")) {
            return isToolOrArmor(actual);
        }
        String requiredMaterial = required.toUpperCase(java.util.Locale.ROOT);
        String actualMaterial = actual.toUpperCase(java.util.Locale.ROOT);
        if (requiredMaterial.equals(actualMaterial)) {
            return true;
        }
        return oreName(requiredMaterial).equals(oreName(actualMaterial));
    }

    private boolean isToolOrArmor(String material) {
        String normalized = material.toUpperCase(java.util.Locale.ROOT);
        return normalized.endsWith("_SWORD")
                || normalized.endsWith("_PICKAXE")
                || normalized.endsWith("_AXE")
                || normalized.endsWith("_SHOVEL")
                || normalized.endsWith("_HOE")
                || normalized.endsWith("_HELMET")
                || normalized.endsWith("_CHESTPLATE")
                || normalized.endsWith("_LEGGINGS")
                || normalized.endsWith("_BOOTS")
                || normalized.equals("ELYTRA")
                || normalized.equals("SHEARS")
                || normalized.equals("FLINT_AND_STEEL");
    }

    private String oreName(String material) {
        String normalized = material.startsWith("DEEPSLATE_")
                ? material.substring("DEEPSLATE_".length()) : material;
        return normalized.endsWith("_ORE")
                ? normalized.substring(0, normalized.length() - 4) : normalized;
    }

    private boolean isQuestComplete(String raceId, RaceQuest quest) {
        for (RaceQuestRequirement requirement : quest.getRequirements()) {
            if (getProgress(raceId, quest, requirement) < requirement.getAmount()) {
                return false;
            }
        }
        return true;
    }

    private void announceCompletion(Race race, RaceQuest quest, int reward) {
        String rewardText = reward <= 0 ? "maksimalus lygis" : "+" + reward + " reputacijos";
        for (java.util.UUID uuid : database.findPlayersByRace(race.getId()).keySet()) {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null) {
                member.sendMessage(MessageUtil.color("&6Rasė &f" + race.getDisplayName() +
                        " &6užbaigė questą &e" + quest.getName() + "&6! Atlygis: &a" + rewardText));
            }
        }
    }

    private void createQuests() {
        quests.put(RacePhase.STONE, List.of(
                quest(RacePhase.STONE, 1, "Pirmasis židinys", "Rasė pradeda savo istoriją nuo bendro židinio ir pirmųjų atsargų.", 250, "CARROTS", 50, "COAL_ORE", 10, "COD", 10, "TOOLS_OR_ARMOR", 50, "COBBLESTONE", 50, "BREAD", 50),
                quest(RacePhase.STONE, 2, "Bendras stogas", "Kiekvienas specialistas prisideda prie pirmųjų saugių namų.", 300, "WHEAT", 60, "IRON_ORE", 10, "SALMON", 10, "TOOLS_OR_ARMOR", 60, "STONE", 60, "COOKIE", 75),
                quest(RacePhase.STONE, 3, "Gyvenvietės pamatai", "Maistas, rūda ir pastogė tampa tvirtais rasės pamatais.", 25, "PIG", 70, "COAL_ORE", 10, "COD", 10, "TOOLS_OR_ARMOR", 70, "OAK_PLANKS", 70, "PUMPKIN_PIE", 100),
                quest(RacePhase.STONE, 4, "Saugūs namai", "Pirmąją gyvenvietę sustiprins visų profesijų darbas.", 350, "CARROTS", 80, "IRON_ORE", 12, "SALMON", 12, "TOOLS_OR_ARMOR", 80, "COBBLESTONE", 80, "COOKED_COD", 125),
                quest(RacePhase.STONE, 5, "Rasės susitelkimas", "Sukaupkite atsargas ir suruoškite pirmąją bendruomenės šventę.", 400, "WHEAT", 100, "COAL_ORE", 12, "COD", 12, "TOOLS_OR_ARMOR", 90, "OAK_PLANKS", 90, "BREAD", 150)));
        quests.put(RacePhase.COPPER, List.of(
                quest(RacePhase.COPPER, 1, "Auganti bendruomenė", "Auganti rasė turi pasirūpinti kiekvienu savo nariu.", 500, "CARROTS", 120, "IRON_ORE", 20, "SALMON", 20, "TOOLS_OR_ARMOR", 100, "STONE", 175, "COOKIE", 200),
                quest(RacePhase.COPPER, 2, "Meistrų kelias", "Kalvė, laukai ir statybos ruošiasi naujam bendruomenės etapui.", 600, "POTATOES", 150, "COPPER_ORE", 25, "COD", 25, "TOOLS_OR_ARMOR", 120, "COBBLESTONE", 120, "PUMPKIN_PIE", "EMERALD", 250),
                quest(RacePhase.COPPER, 3, "Gyvenvietės kelias", "Sukurkite patogų kelią tarp rasės dirbtuvių ir namų.", 700, "WHEAT", 180, "IRON_ORE", 25, "SALMON", 25, "TOOLS_OR_ARMOR", 140, "OAK_PLANKS", 140, "CAKE", 300),
                quest(RacePhase.COPPER, 4, "Rasės pažadas", "Bendras darbas parodys, kad gyvenvietė pasiruošusi augti.", 800, "CARROTS", 200, "COAL_ORE", 30, "COD", 30, "TOOLS_OR_ARMOR", 160, "STONE", 160, "BREAD", 350),
                quest(RacePhase.COPPER, 5, "Tvirtesnė gyvenvietė", "Kasykla ir gyvenvietė bus užbaigtos tik veikiant kartu.", 40, "SHEEP", 220, "IRON_ORE", 30, "SALMON", 30, "TOOLS_OR_ARMOR", 180, "COBBLESTONE", 180, "COOKIE", 400)));
        quests.put(RacePhase.IRON, List.of(
                quest(RacePhase.IRON, 1, "Rasės sargyba", "Didesnei rasei reikia didesnių atsargų ir patikimos tvarkos.", 900, "POTATOES", 250, "IRON_ORE", 35, "COD", 150, "TOOLS_OR_ARMOR", 100, "STONE", 350, "PUMPKIN_PIE", 450),
                quest(RacePhase.IRON, 2, "Geležinė valia", "Kalvė ir kitos profesijos stiprina rasę bendram keliui.", 1000, "WHEAT", 300, "GOLD_ORE", 40, "SALMON", 200, "TOOLS_OR_ARMOR", 120, "COBBLESTONE", 400, "COOKED_BEEF", 500),
                quest(RacePhase.IRON, 3, "Saugūs vartai", "Sukurkite apsaugą, kurią galėtų vadinti savais visos rasės nariai.", 1100, "POTATOES", 350, "IRON_ORE", 45, "COD", 250, "TOOLS_OR_ARMOR", 140, "OAK_PLANKS", 450, "BREAD", 550),
                quest(RacePhase.IRON, 4, "Rasės darbai", "Dideliam bendram projektui reikia kiekvieno specialisto indėlio.", 60, "COW", 400, "REDSTONE_ORE", 50, "SALMON", 300, "TOOLS_OR_ARMOR", 160, "STONE", 500, "COOKIE", 600),
                quest(RacePhase.IRON, 5, "Vienybės išbandymas", "Užbaikite pirmą didelį projektą, kuris tarnaus visai rasei.", 1300, "WHEAT", 450, "GOLD_ORE", 55, "COD", 350, "TOOLS_OR_ARMOR", 180, "COBBLESTONE", 550, "PUMPKIN_PIE", 650)));
        quests.put(RacePhase.DIAMOND, List.of(
                quest(RacePhase.DIAMOND, 1, "Rasės klestėjimas", "Stipri rasė turi aprūpinti didelę ir augančią bendruomenę.", 1500, "CARROTS", 500, "DIAMOND_ORE", 55, "SALMON", 350, "TOOLS_OR_ARMOR", 200, "STONE", 650, "COOKED_CHICKEN", 700),
                quest(RacePhase.DIAMOND, 2, "Protėvių kelias", "Būrėjai, kalviai ir statytojai kuria iškilmingą kelią į rasės širdį.", 60, "SHEEP", 550, "GOLD_ORE", 60, "COD", 400, "TOOLS_OR_ARMOR", 220, "GLASS", 700, "COOKIE", "EMERALD", 800),
                quest(RacePhase.DIAMOND, 3, "Didžioji dirbtuvė", "Kiekvienos profesijos darbas padės įrengti bendrą rasės dirbtuvę.", 1800, "POTATOES", 600, "DIAMOND_ORE", 65, "SALMON", 450, "TOOLS_OR_ARMOR", 240, "DEEPSLATE", 750, "PUMPKIN_PIE", 900),
                quest(RacePhase.DIAMOND, 4, "Rasės šviesa", "Pastatykite ženklą, kuris primintų visiems apie rasės stiprybę.", 2000, "CARROTS", 650, "GOLD_ORE", 70, "COD", 500, "TOOLS_OR_ARMOR", 260, "STONE_BRICKS", 800, "COOKED_PORKCHOP", 1000),
                quest(RacePhase.DIAMOND, 5, "Bendras palikimas", "Susivienijusi rasė pasiruošusi palikti po savęs tvirtą ir gražų miestą.", 2200, "WHEAT", 700, "DIAMOND_ORE", 75, "SALMON", 550, "TOOLS_OR_ARMOR", 280, "BRICKS", 850, "CAKE", 1100)));
        quests.put(RacePhase.NETHERITE, List.of(
                quest(RacePhase.NETHERITE, 1, "Rasės didybė", "Rasė kuria savarankišką gyvenimą, kuriame vietos užtenka kiekvienam.", 2400, "NETHER_WART", 800, "ANCIENT_DEBRIS", 80, "SALMON", 600, "TOOLS_OR_ARMOR", 300, "STONE_BRICKS", 900, "PUMPKIN_PIE", 1200),
                quest(RacePhase.NETHERITE, 2, "Senoji priesaika", "Didžiausiam rasės projektui reikia visų rankų ir ištikimybės savo namams.", 80, "PIG", 900, "ANCIENT_DEBRIS", 90, "COD", 650, "TOOLS_OR_ARMOR", 320, "BLACKSTONE", 950, "COOKED_SALMON", 1300),
                quest(RacePhase.NETHERITE, 3, "Nesulaužoma vienybė", "Sukurkite centrą, jungiantį visų profesijų darbus ir žmones.", 2800, "WHEAT", 1000, "ANCIENT_DEBRIS", 100, "SALMON", 700, "TOOLS_OR_ARMOR", 340, "GLASS", 1000, "CAKE", 1400),
                quest(RacePhase.NETHERITE, 4, "Rasės vainikas", "Rasės viršūnę pasiekia tik tie, kurie kuria kartu.", 100, "COW", 1100, "ANCIENT_DEBRIS", 110, "COD", 750, "TOOLS_OR_ARMOR", 360, "BLACKSTONE", 1050, "BREAD", "EMERALD", 1500),
                quest(RacePhase.NETHERITE, 5, "Amžinas palikimas", "Užbaikite didįjį projektą, kuris primins apie visos rasės vienybę.", 3200, "CARROTS", 1200, "ANCIENT_DEBRIS", 120, "SALMON", 800, "TOOLS_OR_ARMOR", 380, "OAK_PLANKS", 1100, "PUMPKIN_PIE", 1600)));
    }

    private RaceQuest quest(RacePhase phase, int number, String name, String description,
                            int farmerAmount, String farmerTarget, int blacksmithAmount, String blacksmithTarget,
                            int fishermanAmount, String fishermanTarget, int enchanterAmount, String enchanterTarget,
                            int architectAmount, String architectTarget, int bakerAmount, String bakerTarget,
                            int merchantAmount) {
        return quest(phase, number, name, description, farmerAmount, farmerTarget, blacksmithAmount, blacksmithTarget,
                fishermanAmount, fishermanTarget, enchanterAmount, enchanterTarget, architectAmount, architectTarget,
                bakerAmount, bakerTarget, "ANY", merchantAmount);
    }

    private RaceQuest quest(RacePhase phase, int number, String name, String description,
                            int farmerAmount, String farmerTarget, int blacksmithAmount, String blacksmithTarget,
                            int fishermanAmount, String fishermanTarget, int enchanterAmount, String enchanterTarget,
                            int architectAmount, String architectTarget, int bakerAmount, String bakerTarget,
                            String merchantTarget, int merchantAmount) {
        List<RaceQuestRequirement> requirements = new ArrayList<>();
        QuestAction farmerAction = isAnimalTarget(farmerTarget) ? QuestAction.KILL : QuestAction.HARVEST;
        QuestAction bakerAction = isCookedTarget(bakerTarget) ? QuestAction.COOK : QuestAction.CRAFT;
        requirements.add(new RaceQuestRequirement(0, Profession.FARMER, farmerAction, farmerTarget, farmerAmount));
        requirements.add(new RaceQuestRequirement(1, Profession.BLACKSMITH, QuestAction.MINE, blacksmithTarget, blacksmithAmount));
        requirements.add(new RaceQuestRequirement(2, Profession.FISHERMAN, QuestAction.CATCH, fishermanTarget, fishermanAmount));
        requirements.add(new RaceQuestRequirement(3, Profession.ENCHANTER, QuestAction.ENCHANT, enchanterTarget, enchanterAmount));
        requirements.add(new RaceQuestRequirement(4, Profession.ARCHITECT, QuestAction.PLACE, architectTarget, architectAmount));
        requirements.add(new RaceQuestRequirement(5, Profession.BAKER, bakerAction, bakerTarget, bakerAmount));
        requirements.add(new RaceQuestRequirement(6, Profession.MERCHANT, QuestAction.TRADE, merchantTarget, merchantAmount));
        return new RaceQuest(phase.name().toLowerCase() + "-" + number, phase, number,
                name, description, requirements);
    }

    private boolean isAnimalTarget(String target) {
        return Arrays.asList("SHEEP", "COW", "PIG").contains(target.toUpperCase(java.util.Locale.ROOT));
    }

    private boolean isCookedTarget(String target) {
        return target.toUpperCase(java.util.Locale.ROOT).startsWith("COOKED_");
    }
}