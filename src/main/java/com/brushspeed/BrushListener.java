package com.brushspeed;

import io.papermc.paper.event.player.PlayerBrushEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class BrushListener implements Listener {

    private static final int DEFAULT_STROKES = 10;

    private final BrushSpeedPlugin plugin;
    private final BrushManager brushManager;

    public BrushListener(BrushSpeedPlugin plugin, BrushManager brushManager) {
        this.plugin = plugin;
        this.brushManager = brushManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerBrush(PlayerBrushEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        double speed = brushManager.getEffectiveSpeed(player);

        // Vanilla speed — let the game handle it, nothing to override
        if (speed == 1.0) return;

        // Take over: cancel the vanilla brush stroke and apply our own counter
        event.setCancelled(true);

        int baseStrokes = plugin.getConfig().getInt("base-strokes", DEFAULT_STROKES);
        // Higher speed = fewer strokes needed; lower speed = more strokes
        int required = Math.max(1, (int) Math.ceil(baseStrokes / speed));
        int current = brushManager.incrementStroke(player, block);

        if (plugin.getConfig().getBoolean("show-progress-bar", true)) {
            player.sendActionBar(buildProgressBar(current, required, speed));
        }

        if (current >= required) {
            brushManager.excavate(player, block);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        brushManager.clearAllSessions(event.getPlayer());
    }

    private Component buildProgressBar(int current, int required, double speed) {
        double progress = Math.min(1.0, (double) current / required);
        int total = 10;
        int filled = (int) (progress * total);

        // Color the bar based on speed (green = fast, yellow = normal-ish, red = slow)
        TextColor barColor = speed >= 2.0 ? NamedTextColor.GREEN
                : speed >= 1.0 ? NamedTextColor.YELLOW
                : NamedTextColor.RED;

        StringBuilder barStr = new StringBuilder();
        for (int i = 0; i < total; i++) {
            barStr.append(i < filled ? "█" : "░");
        }

        return Component.text()
                .append(Component.text("Brushing ", NamedTextColor.GOLD))
                .append(Component.text(barStr.toString(), barColor))
                .append(Component.text(" " + current + "/" + required, NamedTextColor.WHITE))
                .append(Component.text(" (" + BrushSpeedPlugin.formatSpeed(speed) + "x)", NamedTextColor.GRAY))
                .build();
    }
}
