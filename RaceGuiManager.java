package com.kodari.raceborder.manager;

import com.cryptomorin.xseries.XMaterial;
import com.kodari.raceborder.RaceBorderPlugin;
import com.kodari.raceborder.model.Race;
import com.kodari.raceborder.model.RacePhase;
import com.kodari.raceborder.model.RaceQuest;
import com.kodari.raceborder.model.RaceQuestRequirement;
import com.kodari.raceborder.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RaceGuiManager implements Listener {
    private final RaceBorderPlugin plugin;

    public RaceGuiManager(RaceBorderPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean openOfferingEditor(Player player) {
        Race race = plugin.getConfigManager().getRaces().values().stream().findFirst().orElse(null);
        if (race == null) {
            return false;
        }
        GuiHolder holder = new GuiHolder(GuiType.OFFERING_EDITOR, race.getId());
        Inventory inventory = Bukkit.createInventory(holder, 27,
                MessageUtil.color("&8Visų rasių aukos"));
        holder.inventory = inventory;

        String path = "races." + race.getId() + ".cauldron.offerings";
        if (plugin.getConfig().isConfigurationSection(path)) {
            int slot = 0;
            for (String material : plugin.getConfig().getConfigurationSection(path).getKeys(false)) {
                if (slot >= 26) {
                    break;
                }
                ItemStack item = XMaterial.matchXMaterial(material)
                        .map(XMaterial::parseItem).orElse(null);
                if (item == null) {
                    continue;
                }
                item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(),
                        plugin.getConfig().getInt(path + "." + material + ".amount", 1))));
                inventory.setItem(slot++, item);
            }
        }
        updateOfferingSummary(inventory);
        player.openInventory(inventory);
        player.sendMessage(MessageUtil.color("&7Įdėk reikalingus aukų itemus į lentelę ir uždaryk ją išsaugojimui. "
                + "&fVieno item stack kiekis = reikalingas kiekis."));
        return true;
    }

    public boolean openCauldronSelector(Player player) {
        List<Race> races = new ArrayList<>(plugin.getConfigManager().getRaces().values());
        if (races.isEmpty()) {
            return false;
        }
        GuiHolder holder = new GuiHolder(GuiType.CAULDRON_SELECTOR, "");
        Inventory inventory = createInventory(holder, 54, "&8Pasirink rasę katilui");
        for (int slot = 0; slot < races.size() && slot < 45; slot++) {
            Race race = races.get(slot);
            inventory.setItem(slot, menuItem("CAULDRON", "&6" + race.getDisplayName(),
                    "&7Pasirinkti šią rasę katilui"));
        }
        inventory.setItem(49, menuItem("BARRIER", "&cUždaryti"));
        player.openInventory(inventory);
        return true;
    }

    public boolean openInfo(Player player, String requestedRaceId) {
        Race race = requestedRaceId == null
                ? plugin.getRaceManager().getRace(player)
                : plugin.getRaceManager().getConfiguredRace(requestedRaceId);
        if (race == null) {
            return false;
        }
        openInfoMenu(player, race);
        return true;
    }

    private void openInfoMenu(Player player, Race race) {
        GuiHolder holder = new GuiHolder(GuiType.INFO_MENU, race.getId());
        Inventory inventory = createInventory(holder, 27, "&8Rasės informacija: &b" + race.getDisplayName());
        inventory.setItem(11, menuItem("BOOK", "&6Rasės aukos itemai",
                "&7Peržiūrėk, kokius itemus reikia", "&7aukoti į rasės katilą."));
        RacePhase phase = plugin.getProgressionManager().getPhase(race.getId());
        inventory.setItem(13, menuItem("WRITABLE_BOOK", "&dRasės questai",
                "&7Aktyvūs questai: &f" + phase.name(),
                "&7Atlikite profesijų užduotis",
                "&7ir gaukite rasės reputaciją."));
        inventory.setItem(15, menuItem("EXPERIENCE_BOTTLE", "&bRasės progresas",
                "&7Reputacija, dabartinė fazė", "&7ir kita atrakinama fazė."));
        inventory.setItem(22, menuItem("BARRIER", "&cUždaryti"));
        player.openInventory(inventory);
    }

    private void openOfferingsInfo(Player player, Race race) {
        GuiHolder holder = new GuiHolder(GuiType.OFFERINGS_INFO, race.getId());
        Inventory inventory = createInventory(holder, 54, "&8Rasės aukų itemai: &b" + race.getDisplayName());
        String path = "races." + race.getId() + ".cauldron.offerings";
        int slot = 0;
        boolean hasOfferings = false;
        if (plugin.getConfig().isConfigurationSection(path)) {
            for (String material : plugin.getConfig().getConfigurationSection(path).getKeys(false)) {
                hasOfferings = true;
                if (slot >= 45) {
                    break;
                }
                ItemStack item = XMaterial.matchXMaterial(material).map(XMaterial::parseItem).orElse(null);
                if (item == null) {
                    continue;
                }
                int amount = plugin.getConfig().getInt(path + "." + material + ".amount", 1);
                int reputation = plugin.getProgressionManager().calculateOfferingReputation(material, amount);
                int deposited = plugin.getConfig().getInt(path + "." + material + ".deposited", 0);
                if (deposited >= amount) {
                    continue;
                }
                item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), amount)));
                setMeta(item, "&e" + material,
                        List.of("&7Reikalingas kiekis: &f" + amount,
                                "&7Jau įdėta: &f" + deposited + "/" + amount,
                                "&7Atlygis už visus itemus: &a+" + reputation + " reputacijos"));
                inventory.setItem(slot++, item);
            }
        }
        if (slot == 0) {
            inventory.setItem(22, menuItem("LIME_GLASS_PANE",
                    hasOfferings ? "&aVisi aukų itemai surinkti" : "&7Aukų dar nėra"));
        }
        inventory.setItem(49, menuItem("ARROW", "&eGrįžti"));
        player.openInventory(inventory);
    }

    private void openProgressInfo(Player player, Race race) {
        GuiHolder holder = new GuiHolder(GuiType.PROGRESS_INFO, race.getId());
        Inventory inventory = createInventory(holder, 27, "&8Rasės progresas: &b" + race.getDisplayName());
        RaceProgressionManager progression = plugin.getProgressionManager();
        RacePhase phase = progression.getPhase(race.getId());
        int reputation = progression.getReputation(race.getId());
        String next = phase == RacePhase.NETHERITE
                ? "Maksimali fazė"
                : String.valueOf(progression.nextThreshold(race.getId()));
        inventory.setItem(11, menuItem("EXPERIENCE_BOTTLE", "&bDabartinis progresas",
                "&7Reputacija: &f" + reputation,
                "&7Dabartinė fazė: &f" + phase.name(),
                "&7Kita fazė nuo: &f" + next));
        int phaseSlot = 13;
        for (RacePhase value : RacePhase.values()) {
            String status = progression.getReputation(race.getId()) >= progression.threshold(value)
                    ? "&aAtrakinta" : "&cUžrakinta";
            inventory.setItem(phaseSlot++, menuItem("PAPER", "&e" + value.name(),
                    "&7Reikalinga reputacija: &f" + progression.threshold(value), status));
        }
        inventory.setItem(22, menuItem("ARROW", "&eGrįžti"));
        player.openInventory(inventory);
    }

    private void openQuestInfo(Player player, Race race) {
        RacePhase phase = plugin.getProgressionManager().getPhase(race.getId());
        GuiHolder holder = new GuiHolder(GuiType.QUESTS_INFO, race.getId());
        Inventory inventory = createInventory(holder, 54,
                "&8Rasės questai: &b" + race.getDisplayName() + " &7[" + phase.name() + "]");
        List<RaceQuest> quests = plugin.getQuestManager().getVisibleQuests(race.getId());
        RaceQuest active = plugin.getQuestManager().getActiveQuest(race.getId());
        int slot = 0;
        for (RaceQuest quest : quests) {
            List<String> lore = new ArrayList<>();
            boolean completed = plugin.getQuestManager().isCompleted(race.getId(), quest);
            boolean isActive = active != null && active.getId().equals(quest.getId());
            lore.add("&8&m--------------------");
            lore.add("&7" + quest.getDescription());
            lore.add("");
            lore.add(completed ? "&a✓ Užbaigtas" : isActive ? "&e▶ Aktyvus questas" : "&7Nepradėtas");
            lore.add("");
            lore.add("&6Reikalavimai:");
            for (RaceQuestRequirement requirement : quest.getRequirements()) {
                int progress = plugin.getQuestManager().getProgress(race.getId(), quest, requirement);
                boolean requirementCompleted = progress >= requirement.getAmount();
                String status = requirementCompleted ? "&a✔" : "&e▸";
                lore.add(status + " &f" + requirement.getProfession().displayName());
                lore.add("  &7" + actionName(requirement.getAction().name(), requirement.getTarget()) + " &f"
                        + requirement.getAmount() + "x " + formatTarget(requirement.getTarget(), requirement.getAction().name())
                        + " &8(&e" + progress + "&7/&e" + requirement.getAmount() + "&8)");
            }
            lore.add("");
            lore.add("&6Atlygis: &a+" + plugin.getQuestManager().reward(race.getId(), quest) + " reputacijos");
            lore.add("");
            if (completed) {
                lore.add("&a✔ Šis questas jau užbaigtas");
            } else if (isActive) {
                lore.add("&cPaspausk, kad atšauktum questą");
                lore.add("&8Po atšaukimo taikoma &f10 min.&8 pauzė");
            } else if (active != null) {
                lore.add("&cRasė jau turi kitą aktyvų questą");
            } else {
                long cooldown = plugin.getQuestManager().getCancelCooldownRemaining(race.getId());
                lore.add(cooldown > 0L ? "&cGalėsi imti po: &f" + formatCooldown(cooldown) : "&ePaspausk, kad paimtum questą");
            }
            lore.add("&8&m--------------------");
            inventory.setItem(slot++, menuItem("WRITABLE_BOOK", "&d" + quest.getName(), lore.toArray(new String[0])));
        }
        inventory.setItem(49, menuItem("ARROW", "&eGrįžti"));
        player.openInventory(inventory);
    }

    private String actionName(String action) {
        return switch (action) {
            case "HARVEST" -> "nurinkti";
            case "KILL" -> "nužudyti";
            case "MINE" -> "iškasti";
            case "CATCH" -> "pagauti";
            case "ENCHANT" -> "užburti";
            case "PLACE" -> "pastatyti";
            case "CRAFT" -> "iškepti";
            case "COOK" -> "iškepti";
            case "TRADE" -> "atlikti";
            default -> action.toLowerCase();
        };
    }

    private String actionName(String action, String target) {
        if ("TRADE".equalsIgnoreCase(action) && "EMERALD".equalsIgnoreCase(target)) {
            return "surinkti";
        }
        return actionName(action);
    }

    private String formatTarget(String target) {
        return formatTarget(target, "");
    }

    private String formatTarget(String target, String action) {
        if ("TRADE".equalsIgnoreCase(action) && (target == null || target.equalsIgnoreCase("ANY"))) {
            return "mainų";
        }
        if ("TOOLS_OR_ARMOR".equalsIgnoreCase(target)) {
            return "įrankius / šarvus";
        }
        if (target == null || target.equalsIgnoreCase("ANY")) {
            return "bet ką";
        }
        return switch (target.toUpperCase(Locale.ROOT)) {
            case "SHEEP" -> "avis";
            case "COW" -> "karves";
            case "PIG" -> "kiaules";
            case "BREAD" -> "duoną";
            case "COOKIE" -> "sausainius";
            case "PUMPKIN_PIE" -> "moliūgų pyragą";
            case "CAKE" -> "tortą";
            case "COOKED_COD" -> "keptą menkę";
            case "COOKED_SALMON" -> "keptą lašišą";
            case "COOKED_BEEF" -> "keptą jautieną";
            case "COOKED_PORKCHOP" -> "keptą kiaulieną";
            case "COOKED_CHICKEN" -> "keptą vištieną";
            case "EMERALD" -> "emeraldus";
            default -> target.toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    private String formatCooldown(long milliseconds) {
        long totalSeconds = Math.max(1L, (milliseconds + 999L) / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return minutes + "m " + seconds + "s";
    }

    private Inventory createInventory(GuiHolder holder, int size, String title) {
        Inventory inventory = Bukkit.createInventory(holder, size, MessageUtil.color(title));
        holder.inventory = inventory;
        return inventory;
    }

    private ItemStack menuItem(String material, String name, String... lore) {
        ItemStack item = XMaterial.matchXMaterial(material).map(XMaterial::parseItem).orElse(null);
        if (item == null) {
            return null;
        }
        setMeta(item, name, List.of(lore));
        return item;
    }

    private void setMeta(ItemStack item, String name, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.setDisplayName(MessageUtil.color(name));
        List<String> coloredLore = new ArrayList<>();
        for (String line : lore) {
            coloredLore.add(MessageUtil.color(line));
        }
        meta.setLore(coloredLore);
        item.setItemMeta(meta);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        if (holder.type == GuiType.OFFERING_EDITOR) {
            Inventory topInventory = event.getView().getTopInventory();
            if (event.getRawSlot() == 26) {
                event.setCancelled(true);
                return;
            }
            if (event.getClickedInventory() == topInventory
                    && (event.isShiftClick() || event.getClick().isKeyboardClick())) {
                event.setCancelled(true);
            }
            Bukkit.getScheduler().runTask(plugin, () -> updateOfferingSummary(topInventory));
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (holder.type == GuiType.CAULDRON_SELECTOR) {
            if (slot == 49) {
                player.closeInventory();
                return;
            }
            if (slot < 0 || slot >= 45) {
                return;
            }
            List<Race> races = new ArrayList<>(plugin.getConfigManager().getRaces().values());
            if (slot >= races.size()) {
                return;
            }
            Race race = races.get(slot);
            player.closeInventory();
            if (plugin.getProgressionManager().setCauldron(player, race.getId())) {
                player.sendMessage(MessageUtil.color("&aRasės katilas pastatytas rasėms: &f" + race.getDisplayName() + "&a."));
            } else {
                MessageUtil.send(plugin, player, "race-not-found");
            }
        } else if (holder.type == GuiType.INFO_MENU) {
            if (slot == 11) {
                Race race = plugin.getRaceManager().getConfiguredRace(holder.raceId);
                if (race != null) {
                    openOfferingsInfo(player, race);
                }
            } else if (slot == 13) {
                Race race = plugin.getRaceManager().getConfiguredRace(holder.raceId);
                if (race != null) {
                    openQuestInfo(player, race);
                }
            } else if (slot == 15) {
                Race race = plugin.getRaceManager().getConfiguredRace(holder.raceId);
                if (race != null) {
                    openProgressInfo(player, race);
                }
            } else if (slot == 22) {
                player.closeInventory();
            }
        } else if (holder.type == GuiType.QUESTS_INFO) {
            if (slot == 49) {
                Race race = plugin.getRaceManager().getConfiguredRace(holder.raceId);
                if (race != null) {
                    openInfoMenu(player, race);
                }
                return;
            }
            Race race = plugin.getRaceManager().getConfiguredRace(holder.raceId);
            List<RaceQuest> quests = race == null ? List.of()
                    : plugin.getQuestManager().getVisibleQuests(race.getId());
            if (race == null || slot < 0 || slot >= quests.size()) {
                return;
            }
            RaceQuest quest = quests.get(slot);
            if (plugin.getQuestManager().takeQuest(race.getId(), quest)) {
                player.sendMessage(MessageUtil.color("&aPaimtas rasės questas: &f" + quest.getName()));
                openQuestInfo(player, race);
            } else if (plugin.getQuestManager().getActiveQuest(race.getId()) != null
                    && plugin.getQuestManager().getActiveQuest(race.getId()).getId().equals(quest.getId())
                    && plugin.getQuestManager().cancelQuest(race.getId(), quest)) {
                player.sendMessage(MessageUtil.color("&eRasės questas atšauktas: &f" + quest.getName()
                        + "&e. Jo progresas ištrintas."));
                openQuestInfo(player, race);
            } else if (plugin.getQuestManager().isCompleted(race.getId(), quest)) {
                player.sendMessage(MessageUtil.color("&eŠis questas jau užbaigtas."));
            } else if (plugin.getQuestManager().getCancelCooldownRemaining(race.getId()) > 0L) {
                player.sendMessage(MessageUtil.color("&cPo quest atšaukimo turi palaukti &f"
                        + formatCooldown(plugin.getQuestManager().getCancelCooldownRemaining(race.getId())) + "&c."));
            } else {
                player.sendMessage(MessageUtil.color("&eRasė jau turi paimtą questą. Pirmiausia jį užbaik."));
            }
        } else if (holder.type == GuiType.OFFERINGS_INFO) {
            if (slot == 49) {
                Race race = plugin.getRaceManager().getConfiguredRace(holder.raceId);
                if (race != null) {
                    openInfoMenu(player, race);
                }
            }
        } else if (holder.type == GuiType.PROGRESS_INFO && slot == 22) {
            Race race = plugin.getRaceManager().getConfiguredRace(holder.raceId);
            if (race != null) {
                openInfoMenu(player, race);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        if (holder.type != GuiType.OFFERING_EDITOR
                || event.getRawSlots().stream().anyMatch(slot -> slot >= event.getView().getTopInventory().getSize())) {
            event.setCancelled(true);
            return;
        }
        if (event.getRawSlots().contains(26)) {
            event.setCancelled(true);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> updateOfferingSummary(event.getView().getTopInventory()));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)
                || holder.type != GuiType.OFFERING_EDITOR
                || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        saveOfferings(holder.raceId, event.getInventory());
        returnItems(player, event.getInventory());
        for (Race race : plugin.getConfigManager().getRaces().values()) {
            notifyRaceMembers(race.getId());
        }
        player.sendMessage(MessageUtil.color("&aVienodi aukų reikalavimai išsaugoti visoms rasėms."));
    }

    private void saveOfferings(String raceId, Inventory inventory) {
        Map<String, Integer> amounts = new LinkedHashMap<>();
        for (int slot = 0; slot < 26; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            String material = XMaterial.matchXMaterial(item.getType()).name();
            amounts.merge(material, item.getAmount(), Integer::sum);
        }
        for (Race race : plugin.getConfigManager().getRaces().values()) {
            String path = "races." + race.getId() + ".cauldron.offerings";
            plugin.getConfig().set(path, null);
            for (Map.Entry<String, Integer> entry : amounts.entrySet()) {
                String materialPath = path + "." + entry.getKey();
                plugin.getConfig().set(materialPath + ".amount", entry.getValue());
                plugin.getConfig().set(materialPath + ".reputation",
                        plugin.getProgressionManager().calculateOfferingReputation(entry.getKey(), entry.getValue()));
            }
            plugin.getConfig().set("races." + race.getId() + ".cauldron.offerings-completed", false);
        }
        plugin.saveConfig();
    }

    private void updateOfferingSummary(Inventory inventory) {
        int reputation = 0;
        for (int slot = 0; slot < 26; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            String material = XMaterial.matchXMaterial(item.getType()).name();
            reputation += plugin.getProgressionManager()
                    .calculateOfferingReputation(material, item.getAmount());
        }
        ItemStack book = menuItem("BOOK", "&6Aukos atlygis",
                "&7Rasė gaus, kai visi itemai bus",
                "&7surinkti ir įdėti į katilą:",
                "&a+" + reputation + " reputacijos");
        inventory.setItem(26, book);
    }

    private void notifyRaceMembers(String raceId) {
        Race race = plugin.getRaceManager().getConfiguredRace(raceId);
        if (race == null) {
            return;
        }
        String path = "races." + race.getId() + ".cauldron.offerings";
        List<String> requirements = new ArrayList<>();
        if (plugin.getConfig().isConfigurationSection(path)) {
            for (String material : plugin.getConfig().getConfigurationSection(path).getKeys(false)) {
                int amount = plugin.getConfig().getInt(path + "." + material + ".amount", 1);
                requirements.add("&f" + amount + "x " + material);
            }
        }
        String items = requirements.isEmpty() ? "&cnėra nustatyta" : String.join("&7, ", requirements);
        String message = plugin.getConfig().getString("messages.offering-requirements-updated",
                "&aRasės katilo aukoms reikia surinkti: %items%.")
                .replace("%race%", race.getDisplayName())
                .replace("%items%", items);
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        for (java.util.UUID uuid : plugin.getDatabaseManager().findPlayersByRace(race.getId()).keySet()) {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null) {
                member.sendMessage(MessageUtil.color(prefix + message));
            }
        }
    }

    private void returnItems(Player player, Inventory inventory) {
        for (int slot = 0; slot < 26; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }

    private enum GuiType {
        OFFERING_EDITOR,
        CAULDRON_SELECTOR,
        INFO_MENU,
        OFFERINGS_INFO,
        PROGRESS_INFO
        ,QUESTS_INFO
    }

    private static class GuiHolder implements InventoryHolder {
        private final GuiType type;
        private final String raceId;
        private Inventory inventory;

        private GuiHolder(GuiType type, String raceId) {
            this.type = type;
            this.raceId = raceId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}