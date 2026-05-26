package com.brushspeed;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class BrushListener implements Listener {

    private final BrushManager brushManager;

    public BrushListener(BrushSpeedPlugin plugin, BrushManager brushManager) {
        this.brushManager = brushManager;
    }

    /**
     * Fires when the player right-clicks a block.
     * We intercept brush-on-suspicious-block interactions and handle speed ourselves.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only care about right-clicking a block with the main hand
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || !brushManager.isSuspicious(block)) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.BRUSH) return;

        Player player = event.getPlayer();

        // Cancel vanilla brushing — we take full control of the speed
        event.setCancelled(true);

        // Start (or restart) our timed session for this player + block
        brushManager.startSession(player, block, item);
    }

    /**
     * Cancel the session if the player switches their held item.
     */
    @EventHandler
    public void onItemSwitch(PlayerItemHeldEvent event) {
        brushManager.cancelSession(event.getPlayer());
    }

    /**
     * Clean up when the player disconnects.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        brushManager.cancelSession(event.getPlayer());
    }
}
