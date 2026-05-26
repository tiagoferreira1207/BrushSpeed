package com.brushspeed;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Brushable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class BrushTask extends BukkitRunnable {

    private final BrushSpeedPlugin plugin;
    private final BrushManager manager;
    private final Player player;
    private final Block block;
    private final double speed;
    private final int required;
    private final Material originalType;
    private int elapsed = 0;

    public BrushTask(BrushSpeedPlugin plugin, BrushManager manager,
                     Player player, Block block, ItemStack brush, double speed, int required) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.block = block;
        this.speed = speed;
        this.required = required;
        this.originalType = block.getType();
    }

    @Override
    public void run() {
        if (!isStillBrushing()) {
            resetDusted();
            cancel();
            manager.clearTask(player);
            return;
        }

        elapsed++;

        updateDusted();
        playSound();

        if (plugin.getConfig().getBoolean("show-progress-bar", true)) {
            player.sendActionBar(buildBar());
        }

        if (elapsed >= required) {
            manager.excavate(player, block);
            cancel();
            manager.clearTask(player);
        }
    }

    private boolean isStillBrushing() {
        if (!player.isOnline()) return false;
        // Block was already changed (e.g. vanilla or another plugin modified it)
        if (block.getType() != originalType) return false;
        // Player must still be holding a brush
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() != Material.BRUSH) return false;
        // Player must still be aiming at this specific block within reach
        Block target = player.getTargetBlockExact(5, FluidCollisionMode.NEVER);
        return block.equals(target);
    }

    private void updateDusted() {
        BlockData data = block.getBlockData();
        if (data instanceof Brushable brushable) {
            int max = brushable.getMaximumDusted();
            int dusted = (int) Math.min(max, Math.ceil((double) elapsed / required * max));
            brushable.setDusted(dusted);
            block.setBlockData(brushable, false);
        }
    }

    private void resetDusted() {
        BlockData data = block.getBlockData();
        if (data instanceof Brushable brushable) {
            brushable.setDusted(0);
            block.setBlockData(brushable, false);
        }
    }

    private void playSound() {
        if (elapsed % 8 != 1) return;
        Sound sound = originalType == Material.SUSPICIOUS_SAND
                ? Sound.ITEM_BRUSH_BRUSHING_SAND
                : Sound.ITEM_BRUSH_BRUSHING_GRAVEL;
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), sound, 1.0f, 1.0f);
    }

    private Component buildBar() {
        double progress = Math.min(1.0, (double) elapsed / required);
        int filled = (int) (progress * 10);

        TextColor barColor = speed >= 2.0 ? NamedTextColor.GREEN
                : speed >= 1.0 ? NamedTextColor.YELLOW
                : NamedTextColor.RED;

        StringBuilder barStr = new StringBuilder();
        for (int i = 0; i < 10; i++) barStr.append(i < filled ? "█" : "░");

        return Component.text()
                .append(Component.text("Brushing ", NamedTextColor.GOLD))
                .append(Component.text(barStr.toString(), barColor))
                .append(Component.text(" " + elapsed + "/" + required, NamedTextColor.WHITE))
                .append(Component.text(" (" + BrushSpeedPlugin.formatSpeed(speed) + "x)", NamedTextColor.GRAY))
                .build();
    }
}
