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
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BrushManager {

    private final BrushSpeedPlugin plugin;
    private final NamespacedKey speedKey;

    // Player UUID -> individually set speed
    private final Map<UUID, Double> playerSpeeds = new HashMap<>();

    // Global speed applied to every player (null = not set)
    private Double globalSpeed = null;

    // Player UUID -> active brushing task
    private final Map<UUID, BukkitTask> activeTasks = new HashMap<>();

    public BrushManager(BrushSpeedPlugin plugin) {
        this.plugin = plugin;
        this.speedKey = new NamespacedKey(plugin, "speed");
    }

    // -------------------------------------------------------------------------
    // Speed resolution — priority: item → per-player → global → config default
    // -------------------------------------------------------------------------

    public double getEffectiveSpeed(Player player, ItemStack brush) {
        double itemSpeed = getItemSpeed(brush);
        if (itemSpeed > 0) return itemSpeed;

        if (playerSpeeds.containsKey(player.getUniqueId())) {
            return playerSpeeds.get(player.getUniqueId());
        }
        if (globalSpeed != null) return globalSpeed;
        return plugin.getConfig().getDouble("default-speed", 1.0);
    }

    // Overload for commands where we check the held item automatically
    public double getEffectiveSpeed(Player player) {
        return getEffectiveSpeed(player, player.getInventory().getItemInMainHand());
    }

    // -------------------------------------------------------------------------
    // Item-based speed (PDC + lore)
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
    // Task-based brush sessions
    // -------------------------------------------------------------------------

    public void startSession(Player player, Block block, ItemStack brush) {
        cancelSession(player); // cancel any existing session first

        double speed = getEffectiveSpeed(player, brush);
        int baseTicks = plugin.getConfig().getInt("base-ticks", 96);
        int required = Math.max(1, (int) Math.ceil(baseTicks / speed));

        BrushTask task = new BrushTask(plugin, this, player, block, brush, speed, required);
        BukkitTask bukkitTask = task.runTaskTimer(plugin, 1L, 1L);
        activeTasks.put(player.getUniqueId(), bukkitTask);
    }

    public void cancelSession(Player player) {
        BukkitTask task = activeTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    public void clearTask(Player player) {
        activeTasks.remove(player.getUniqueId());
    }

    public void cancelAll() {
        activeTasks.values().forEach(BukkitTask::cancel);
        activeTasks.clear();
    }

    public void reload() {
        cancelAll();
        playerSpeeds.clear();
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
    }

    public boolean isSuspicious(Block block) {
        return block.getType() == Material.SUSPICIOUS_SAND
                || block.getType() == Material.SUSPICIOUS_GRAVEL;
    }
}
