package com.magicera.coachrename;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class NameCommands implements CommandExecutor {

    private final CoachRenamePlugin plugin;
    private final NicknameManager nicknameManager;
    private final NameplateManager nameplateManager;

    public NameCommands(
            CoachRenamePlugin plugin,
            NicknameManager nicknameManager,
            NameplateManager nameplateManager
    ) {
        this.plugin = plugin;
        this.nicknameManager = nicknameManager;
        this.nameplateManager = nameplateManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();

        return switch (cmd) {
            case "nick" -> handleNick(sender, args);
            case "nickclear" -> handleNickClear(sender);
            case "truename" -> handleTrueName(sender, args);
            default -> false;
        };
    }

    private boolean handleNick(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.prefix() + "Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("magicera.names.nick")) {
            player.sendMessage(plugin.prefix() + "You do not have permission.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(plugin.prefix() + "Use §e/nick <nickname>");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("off")) {
            nicknameManager.clearNickname(player.getUniqueId());
            nameplateManager.refreshAll();
            player.sendMessage(plugin.prefix() + "Your nickname has been turned off.");
            return true;
        }

        String input = String.join(" ", args);

        NicknameManager.NicknameResult result =
                nicknameManager.setNickname(player.getUniqueId(), player.getName(), input);

        if (!result.success()) {
            player.sendMessage(plugin.prefix() + "§c" + result.message());
            return true;
        }

        nameplateManager.refreshAll();
        player.sendMessage(plugin.prefix() + "Your nickname is now §f" + result.record().plainNickname());
        return true;
    }

    private boolean handleNickClear(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.prefix() + "Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("magicera.names.nickclear")) {
            player.sendMessage(plugin.prefix() + "You do not have permission.");
            return true;
        }

        nicknameManager.clearNickname(player.getUniqueId());
        nameplateManager.refreshAll();

        player.sendMessage(plugin.prefix() + "Your nickname has been cleared.");
        return true;
    }

    private boolean handleTrueName(CommandSender sender, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(plugin.prefix() + "Only players can use this command.");
            return true;
        }

        if (!viewer.hasPermission("magicera.names.truename")) {
            viewer.sendMessage(plugin.prefix() + "You do not have permission.");
            return true;
        }

        int seconds = plugin.getConfig().getInt("truename.reveal-seconds", 30);

        if (args.length == 0) {
            nameplateManager.revealAllTrueNames(viewer, seconds);
            viewer.sendMessage(plugin.prefix() + "Showing true names for §e" + seconds + "§c seconds.");
            return true;
        }

        String search = String.join(" ", args);
        UUID targetId = nicknameManager.findByNicknameOrUsername(search);

        if (targetId == null) {
            viewer.sendMessage(plugin.prefix() + "§cNo online player or saved nickname found.");
            return true;
        }

        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            viewer.sendMessage(plugin.prefix() + "§cThat player is not online.");
            return true;
        }

        String nick = nicknameManager.getPlainName(target.getUniqueId(), target.getName());

        viewer.sendMessage(plugin.prefix() + "Nickname §f" + nick + " §7| True Name §e" + target.getName());
        nameplateManager.revealSpecificTrueName(viewer, target, seconds);
        return true;
    }
}
