package com.brushspeed;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockData;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrushableBlock;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BrushManager {

    private final BrushSpeedPlugin plugin;
    private final NamespacedKey speedKey;

    // Player UUID -> individually set speed (overrides global)
    private final Map<UUID, Double> playerSpeeds = new HashMap<>();

    // Global speed applied to every player (null = not set)
    private Double globalSpeed = null;

    // Player UUID -> (block location key -> stroke count)
    private final Map<UUID, Map<String, Integer>> brushSessions = new HashMap<>();

    public BrushManager(BrushSpeedPlugin plugin) {
        this.plugin = plugin;
        this.speedKey = new NamespacedKey(plugin, "speed");
    }

    // Priority: item speed → per-player speed → global speed → config default
    public double getEffectiveSpeed(Player player, ItemStack brush) {
        double itemSpeed = getItemSpeed(brush);
        if (itemSpeed > 0) return itemSpeed;

        if (playerSpeeds.containsKey(player.getUniqueId())) {
            return playerSpeeds.get(player.getUniqueId());
        }
        if (globalSpeed != null) return globalSpeed;
        return plugin.getConfig().getDouble("default-speed", 1.0);
    }

    // Overload used by commands where no specific brush is in context
    public double getEffectiveSpeed(Player player) {
        return getEffectiveSpeed(player, player.getInventory().getItemInMainHand());
    }

    // -------------------------------------------------------------------------
    // Item-based speed (PDC)
    // -------------------------------------------------------------------------

    public double getItemSpeed(ItemStack item) {
        if (item == null || item.getType() != Material.BRUSH) return -1;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return -1;
        Double value = meta.getPersistentDataContainer().get(speedKey, PersistentDataType.DOUBLE);
        return value != null ? value : -1;
    }

    public void applySpeedToItem(ItemStack item, double speed) {
        if (item == null || item.getType() != Material.BRUSH) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        meta.getPersistentDataContainer().set(speedKey, PersistentDataType.DOUBLE, speed);

        // Rebuild lore: remove any existing speed line, then insert ours at the top
        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.removeIf(this::isSpeedLoreLine);
        lore.add(0, buildSpeedLore(speed));
        meta.lore(lore);

        item.setItemMeta(meta);
    }

    public void removeSpeedFromItem(ItemStack item) {
        if (item == null || item.getType() != Material.BRUSH) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        meta.getPersistentDataContainer().remove(speedKey);

        if (meta.lore() != null) {
            List<Component> lore = new ArrayList<>(meta.lore());
            lore.removeIf(this::isSpeedLoreLine);
            meta.lore(lore.isEmpty() ? null : lore);
        }

        item.setItemMeta(meta);
    }

    private Component buildSpeedLore(double speed) {
        return Component.text("⚡ Brush Speed: ", NamedTextColor.GOLD)
                .append(Component.text(BrushSpeedPlugin.formatSpeed(speed) + "x", NamedTextColor.WHITE));
    }

    private boolean isSpeedLoreLine(Component line) {
        // Detect our lore line by checking for the PDC key on a fresh read is not
        // possible on a Component, so we match on the gold "⚡ Brush Speed:" prefix.
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(line).startsWith("⚡ Brush Speed:");
    }

    // -------------------------------------------------------------------------
    // Player / global speed
    // -------------------------------------------------------------------------

    public void setSpeed(Player player, double speed) {
        playerSpeeds.put(player.getUniqueId(), speed);
    }

    public void resetSpeed(Player player) {
        playerSpeeds.remove(player.getUniqueId());
    }

    public void setGlobalSpeed(double speed) {
        this.globalSpeed = speed;
        playerSpeeds.clear();
    }

    public void resetGlobalSpeed() {
        this.globalSpeed = null;
        playerSpeeds.clear();
    }

    public Double getGlobalSpeed() {
        return globalSpeed;
    }

    // -------------------------------------------------------------------------
    // Brush sessions
    // -------------------------------------------------------------------------

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
        brushSessions.clear();
    }

    // -------------------------------------------------------------------------
    // Excavation
    // -------------------------------------------------------------------------

    public void excavate(Player player, Block block) {
        if (!isSuspicious(block)) return;

        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        BlockData originalData = block.getBlockData();

        ItemStack loot = null;
        BlockState state = block.getState();
        if (state instanceof BrushableBlock brushable) {
            loot = brushable.getItem();
        }

        Material replacement = block.getType() == Material.SUSPICIOUS_SAND
                ? Material.SAND
                : Material.GRAVEL;
        block.setType(replacement);

        if (loot != null && !loot.getType().isAir()) {
            block.getWorld().dropItemNaturally(center, loot);
        }

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
