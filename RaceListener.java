package com.kodari.raceborder.listener;

import com.kodari.raceborder.manager.RaceManager;
import com.kodari.raceborder.model.QuestAction;
import com.kodari.raceborder.util.MessageUtil;
import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class RaceListener implements Listener {
    private final RaceManager races;

    public RaceListener(RaceManager races) {
        this.races = races;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(races.getPlugin(), () -> {
            races.restore(event.getPlayer());
            races.updateAdminPrefix(event.getPlayer());
            races.updateTabListOrder();
        }, 1L);
        Bukkit.getScheduler().runTaskLater(races.getPlugin(), races::updateTabListOrder, 20L);
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (isOpCommand(event.getMessage())) {
            scheduleAdminPrefixSync();
        }
    }

    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        if (isOpCommand(event.getCommand())) {
            scheduleAdminPrefixSync();
        }
    }

    private void scheduleAdminPrefixSync() {
        Bukkit.getScheduler().runTask(races.getPlugin(), races::syncAdminPrefixes);
    }

    private boolean isOpCommand(String rawCommand) {
        String command = rawCommand.trim();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        int space = command.indexOf(' ');
        String label = (space >= 0 ? command.substring(0, space) : command).toLowerCase(java.util.Locale.ROOT);
        int namespace = label.indexOf(':');
        if (namespace >= 0) {
            label = label.substring(namespace + 1);
        }
        return label.equals("op") || label.equals("deop");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        races.quit(event.getPlayer());
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        XMaterial placedMaterial = XMaterial.matchXMaterial(event.getBlock().getType());
        boolean allowedOutside = "DIRT".equals(placedMaterial.name())
                || "COBBLESTONE".equals(placedMaterial.name());
        if (!races.canPlace(event.getPlayer(), event.getBlock().getLocation(), allowedOutside)) {
            event.setCancelled(true);
            races.warnOutsidePlacement(event.getPlayer());
            return;
        }
        races.getPlugin().getQuestManager().record(event.getPlayer(), QuestAction.PLACE,
                XMaterial.matchXMaterial(event.getBlock().getType()).name(), 1);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (denyRestrictedItem(event.getPlayer(), event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            return;
        }
        if (races.getPlugin().getProgressionManager().isConfiguredCauldron(event.getBlock())) {
            if (!races.getPlugin().getProgressionManager().handleCauldronBreak(event.getPlayer(), event.getBlock())) {
                event.setCancelled(true);
            }
            return;
        }
        if (!races.canBreak(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        String material = XMaterial.matchXMaterial(event.getBlock().getType()).name();
        races.getPlugin().getQuestManager().record(event.getPlayer(), QuestAction.MINE, material, 1);
        if (isMatureCrop(event.getBlock().getBlockData())) {
            races.getPlugin().getQuestManager().record(event.getPlayer(), QuestAction.HARVEST, material, 1);
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && denyRestrictedItem(player, event.getRecipe().getResult())) {
            event.setCancelled(true);
            return;
        }
        if (event.getWhoClicked() instanceof Player player) {
            String material = XMaterial.matchXMaterial(event.getRecipe().getResult().getType()).name();
            races.getPlugin().getQuestManager().record(player, QuestAction.CRAFT, material,
                    event.getRecipe().getResult().getAmount());
        }
    }

    @EventHandler
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        String material = XMaterial.matchXMaterial(event.getItemType()).name();
        races.getPlugin().getQuestManager().record(event.getPlayer(), QuestAction.COOK, material,
                event.getItemAmount());
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        String material = XMaterial.matchXMaterial(event.getItem().getType()).name();
        races.getPlugin().getQuestManager().record(event.getEnchanter(), QuestAction.ENCHANT, material, 1);
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item item)) {
            return;
        }
        String material = XMaterial.matchXMaterial(item.getItemStack().getType()).name();
        races.getPlugin().getQuestManager().record(event.getPlayer(), QuestAction.CATCH, material,
                item.getItemStack().getAmount());
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        races.getPlugin().getQuestManager().record(killer, QuestAction.KILL,
                event.getEntityType().name(), 1);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player
                && denyRestrictedItem(player, player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (denyRestrictedItem(event.getPlayer(), item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (denyRestrictedItem(event.getPlayer(), event.getItem())) {
            event.setCancelled(true);
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null) {
            return;
        }
        if (races.getPlugin().getProgressionManager().handleCauldron(event.getPlayer(), event.getClickedBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getView().getType() == InventoryType.MERCHANT && event.getRawSlot() == 2
                && event.getCurrentItem() != null) {
            String result = XMaterial.matchXMaterial(event.getCurrentItem().getType()).name();
            races.getPlugin().getQuestManager().record(player, QuestAction.TRADE,
                    "TRADE_RESULT:" + result, event.getCurrentItem().getAmount());
        }
        if (event.getAction() == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY
                && races.getPlugin().getProgressionManager().isArmorItem(event.getCurrentItem())
                && denyRestrictedItem(player, event.getCurrentItem())) {
            event.setCancelled(true);
            return;
        }
        if (event.getSlotType() != InventoryType.SlotType.ARMOR) {
            return;
        }
        ItemStack candidate = event.getCursor();
        if (event.getClick().isKeyboardClick()) {
            candidate = event.getHotbarButton() < 0
                    ? null : player.getInventory().getItem(event.getHotbarButton());
        } else if (event.getAction() == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            candidate = event.getCurrentItem();
        }
        if (denyRestrictedItem(player, candidate)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean armorSlot = event.getRawSlots().stream().anyMatch(slot -> slot >= 5 && slot <= 8);
        if (armorSlot && denyRestrictedItem(player, event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        races.getPlugin().getProgressionManager()
                .handleDroppedOffering(event.getPlayer(), event.getItemDrop());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() != null) {
            races.handleMove(event.getPlayer(), event.getFrom(), event.getTo());
        }
    }

    private boolean denyRestrictedItem(Player player, ItemStack item) {
        if (races.getPlugin().getProgressionManager().isItemAllowed(player, item)) {
            return false;
        }
        races.getPlugin().getProgressionManager().sendItemLocked(player, item);
        return true;
    }

    private boolean isMatureCrop(org.bukkit.block.data.BlockData blockData) {
        return blockData instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge();
    }

}