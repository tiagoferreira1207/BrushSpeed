package com.brushspeed;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockData;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrushableBlock;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BrushManager {

    private final BrushSpeedPlugin plugin;

    // Player UUID -> individually set speed (overrides global)
    private final Map<UUID, Double> playerSpeeds = new HashMap<>();

    // Global speed applied to every player (null = not set)
    private Double globalSpeed = null;

    // Player UUID -> (block location key -> stroke count)
    private final Map<UUID, Map<String, Integer>> brushSessions = new HashMap<>();

    public BrushManager(BrushSpeedPlugin plugin) {
        this.plugin = plugin;
    }

    // Priority: per-player speed → global speed → config default
    public double getEffectiveSpeed(Player player) {
        if (playerSpeeds.containsKey(player.getUniqueId())) {
            return playerSpeeds.get(player.getUniqueId());
        }
        if (globalSpeed != null) {
            return globalSpeed;
        }
        return plugin.getConfig().getDouble("default-speed", 1.0);
    }

    public void setSpeed(Player player, double speed) {
        playerSpeeds.put(player.getUniqueId(), speed);
    }

    public void resetSpeed(Player player) {
        playerSpeeds.remove(player.getUniqueId());
    }

    public void setGlobalSpeed(double speed) {
        this.globalSpeed = speed;
        // Clear per-player overrides so everyone uses the new global speed
        playerSpeeds.clear();
    }

    public void resetGlobalSpeed() {
        this.globalSpeed = null;
        playerSpeeds.clear();
    }

    public Double getGlobalSpeed() {
        return globalSpeed;
    }

    /**
     * Increments the brush stroke counter for this player+block and returns the new count.
     */
    public int incrementStroke(Player player, Block block) {
        String key = blockKey(block);
        return brushSessions
                .computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .merge(key, 1, Integer::sum);
    }

    public void clearSession(Player player, Block block) {
        Map<String, Integer> sessions = brushSessions.get(player.getUniqueId());
        if (sessions != null) sessions.remove(blockKey(block));
    }

    public void clearAllSessions(Player player) {
        brushSessions.remove(player.getUniqueId());
    }

    public void cancelAll() {
        brushSessions.clear();
    }

    public void reload() {
        // Keep player speed overrides across reloads — only reset sessions
        brushSessions.clear();
    }

    /**
     * Manually excavates a suspicious block: drops its loot, replaces it with
     * normal sand/gravel, and plays the appropriate effects.
     */
    public void excavate(Player player, Block block) {
        if (!isSuspicious(block)) return;

        Location center = block.getLocation().add(0.5, 0.5, 0.5);

        // Capture block data before we replace the block
        BlockData originalData = block.getBlockData();

        // Get the hidden loot item from the block entity
        ItemStack loot = null;
        BlockState state = block.getState();
        if (state instanceof BrushableBlock brushable) {
            loot = brushable.getItem();
        }

        // Replace the suspicious block with its normal counterpart
        Material replacement = block.getType() == Material.SUSPICIOUS_SAND
                ? Material.SAND
                : Material.GRAVEL;
        block.setType(replacement);

        // Drop loot item
        if (loot != null && !loot.getType().isAir()) {
            block.getWorld().dropItemNaturally(center, loot);
        }

        // Particles and sound to mimic vanilla feel
        block.getWorld().spawnParticle(Particle.BLOCK, center, 20, 0.3, 0.3, 0.3, 0.05, originalData);
        block.getWorld().playSound(center, Sound.BLOCK_SAND_BREAK, 1.0f, 1.0f);

        clearSession(player, block);
    }

    public boolean isSuspicious(Block block) {
        return block.getType() == Material.SUSPICIOUS_SAND
                || block.getType() == Material.SUSPICIOUS_GRAVEL;
    }

    private String blockKey(Block block) {
        return block.getWorld().getName()
                + ":" + block.getX()
                + ":" + block.getY()
                + ":" + block.getZ();
    }
}
