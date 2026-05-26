package com.brushspeed;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

public class BrushSpeedPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {

    private BrushManager brushManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        brushManager = new BrushManager(this);
        getServer().getPluginManager().registerEvents(new BrushListener(this, brushManager), this);

        var cmd = getCommand("brushspeed");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }

        getLogger().info("BrushSpeed v" + getDescription().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        if (brushManager != null) brushManager.cancelAll();
        getLogger().info("BrushSpeed disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                double speed = brushManager.getEffectiveSpeed(player);
                sender.sendMessage(legacyColor(getMessage("speed-get")
                        .replace("{speed}", formatSpeed(speed))));
            } else {
                sender.sendMessage("BrushSpeed v" + getDescription().getVersion());
            }
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "enchant" -> handleEnchant(sender, args);
            case "disenchant" -> handleDisenchant(sender);
            case "set" -> handleSet(sender, args);
            case "setall" -> handleSetAll(sender, args);
            case "reset" -> handleReset(sender);
            case "resetall" -> handleResetAll(sender);
            case "reload" -> handleReload(sender);
            default -> {
                sender.sendMessage(legacyColor("&6Usage: /brushspeed [enchant <speed> | disenchant | set <speed> [player] | setall <speed> | reset | resetall | reload]"));
                yield true;
            }
        };
    }

    private boolean handleEnchant(CommandSender sender, String[] args) {
        if (!sender.hasPermission("brushspeed.enchant")) {
            sender.sendMessage(legacyColor(getMessage("no-permission")));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can enchant a brush.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(legacyColor("&cUsage: /brushspeed enchant <speed>"));
            return true;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() != Material.BRUSH) {
            sender.sendMessage(legacyColor(getMessage("not-a-brush")));
            return true;
        }

        double min = getConfig().getDouble("min-speed", 0.1);
        double max = getConfig().getDouble("max-speed", 10.0);

        try {
            double speed = Double.parseDouble(args[1]);
            if (speed < min || speed > max) {
                sender.sendMessage(legacyColor(getMessage("invalid-speed")
                        .replace("{min}", formatSpeed(min))
                        .replace("{max}", formatSpeed(max))));
                return true;
            }
            brushManager.applySpeedToItem(held, speed);
            player.sendMessage(legacyColor(getMessage("enchant-success").replace("{speed}", formatSpeed(speed))));
        } catch (NumberFormatException e) {
            sender.sendMessage(legacyColor(getMessage("invalid-speed")
                    .replace("{min}", formatSpeed(min))
                    .replace("{max}", formatSpeed(max))));
        }
        return true;
    }

    private boolean handleDisenchant(CommandSender sender) {
        if (!sender.hasPermission("brushspeed.enchant")) {
            sender.sendMessage(legacyColor(getMessage("no-permission")));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can disenchant a brush.");
            return true;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() != Material.BRUSH) {
            sender.sendMessage(legacyColor(getMessage("not-a-brush")));
            return true;
        }

        brushManager.removeSpeedFromItem(held);
        player.sendMessage(legacyColor(getMessage("disenchant-success")));
        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("brushspeed.set")) {
            sender.sendMessage(legacyColor(getMessage("no-permission")));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(legacyColor("&cUsage: /brushspeed set <speed> [player]"));
            return true;
        }

        Player target;
        if (args.length >= 3) {
            if (!sender.hasPermission("brushspeed.set.others")) {
                sender.sendMessage(legacyColor(getMessage("no-permission")));
                return true;
            }
            target = getServer().getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(legacyColor(getMessage("player-not-found")));
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage("Please specify a player name when using from console.");
            return true;
        }

        double min = getConfig().getDouble("min-speed", 0.1);
        double max = getConfig().getDouble("max-speed", 10.0);

        try {
            double speed = Double.parseDouble(args[1]);
            if (speed < min || speed > max) {
                sender.sendMessage(legacyColor(getMessage("invalid-speed")
                        .replace("{min}", formatSpeed(min))
                        .replace("{max}", formatSpeed(max))));
                return true;
            }
            brushManager.setSpeed(target, speed);
            String speedStr = formatSpeed(speed);
            if (target == sender) {
                sender.sendMessage(legacyColor(getMessage("speed-set").replace("{speed}", speedStr)));
            } else {
                sender.sendMessage(legacyColor(getMessage("speed-set-other")
                        .replace("{player}", target.getName())
                        .replace("{speed}", speedStr)));
                target.sendMessage(legacyColor(getMessage("speed-set").replace("{speed}", speedStr)));
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(legacyColor(getMessage("invalid-speed")
                    .replace("{min}", formatSpeed(min))
                    .replace("{max}", formatSpeed(max))));
        }
        return true;
    }

    private boolean handleSetAll(CommandSender sender, String[] args) {
        if (!sender.hasPermission("brushspeed.setall")) {
            sender.sendMessage(legacyColor(getMessage("no-permission")));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(legacyColor("&cUsage: /brushspeed setall <speed>"));
            return true;
        }

        double min = getConfig().getDouble("min-speed", 0.1);
        double max = getConfig().getDouble("max-speed", 10.0);

        try {
            double speed = Double.parseDouble(args[1]);
            if (speed < min || speed > max) {
                sender.sendMessage(legacyColor(getMessage("invalid-speed")
                        .replace("{min}", formatSpeed(min))
                        .replace("{max}", formatSpeed(max))));
                return true;
            }
            brushManager.setGlobalSpeed(speed);
            String speedStr = formatSpeed(speed);
            // Broadcast to all online players
            Component broadcast = legacyColor(getMessage("speed-set-all").replace("{speed}", speedStr));
            getServer().getOnlinePlayers().forEach(p -> p.sendMessage(broadcast));
            if (!(sender instanceof Player)) {
                sender.sendMessage(legacyColor("&aGlobal brush speed set to &f" + speedStr + "x&a."));
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(legacyColor(getMessage("invalid-speed")
                    .replace("{min}", formatSpeed(min))
                    .replace("{max}", formatSpeed(max))));
        }
        return true;
    }

    private boolean handleResetAll(CommandSender sender) {
        if (!sender.hasPermission("brushspeed.setall")) {
            sender.sendMessage(legacyColor(getMessage("no-permission")));
            return true;
        }
        brushManager.resetGlobalSpeed();
        double def = getConfig().getDouble("default-speed", 1.0);
        Component broadcast = legacyColor(getMessage("speed-reset-all").replace("{speed}", formatSpeed(def)));
        getServer().getOnlinePlayers().forEach(p -> p.sendMessage(broadcast));
        if (!(sender instanceof Player)) {
            sender.sendMessage(legacyColor("&aGlobal brush speed reset to default (&f" + formatSpeed(def) + "x&a)."));
        }
        return true;
    }

    private boolean handleReset(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can reset their speed.");
            return true;
        }
        brushManager.resetSpeed(player);
        double def = getConfig().getDouble("default-speed", 1.0);
        player.sendMessage(legacyColor(getMessage("speed-reset").replace("{speed}", formatSpeed(def))));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("brushspeed.reload")) {
            sender.sendMessage(legacyColor(getMessage("no-permission")));
            return true;
        }
        reloadConfig();
        brushManager.reload();
        sender.sendMessage(legacyColor(getMessage("reloaded")));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("enchant", "disenchant", "set", "setall", "reset", "resetall", "reload");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("enchant") || args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("setall"))) {
            return Arrays.asList("0.5", "1.0", "2.0", "5.0", "10.0");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                    .toList();
        }
        return List.of();
    }

    public String getMessage(String key) {
        return getConfig().getString("messages." + key, "&cMissing message: " + key);
    }

    public Component legacyColor(String message) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(message);
    }

    public static String formatSpeed(double speed) {
        if (speed == Math.floor(speed)) return String.valueOf((int) speed);
        return String.format("%.1f", speed);
    }

    public BrushManager getBrushManager() {
        return brushManager;
    }
}
