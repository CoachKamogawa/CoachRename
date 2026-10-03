package com.magicera.coachrename;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;

public final class CoachRenamePlugin extends JavaPlugin implements Listener {

    private NicknameManager nicknameManager;
    private NameplateManager nameplateManager;
    private Essentials essentials;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        essentials = (Essentials) Bukkit.getPluginManager()
                .getPlugin("Essentials");

        nicknameManager = new NicknameManager(this);
        nicknameManager.load();

        nameplateManager = new NameplateManager(this, nicknameManager);
        nameplateManager.start();

        NameCommands commands = new NameCommands(
                this,
                nicknameManager,
                nameplateManager
        );

        getCommand("nick").setExecutor(commands);
        getCommand("nickclear").setExecutor(commands);
        getCommand("truename").setExecutor(commands);

        // EssentialsXChat handles chat formatting.
        Bukkit.getPluginManager().registerEvents(nameplateManager, this);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(
                new NicknameCommandListener(this),
                this
        );

        Bukkit.getOnlinePlayers().forEach(
                player -> syncNickname(player.getUniqueId())
        );
    }

    public void syncNickname(UUID uuid) {
        User user = essentials.getUser(uuid);

        if (user == null) {
            return;
        }

        NicknameRecord record = nicknameManager.getRecord(uuid);

        String nickname = record == null
                ? null
                : ChatColor.translateAlternateColorCodes(
                        '&',
                        record.displayNickname()
                );

        if (!Objects.equals(user.getNickname(), nickname)) {
            user.setNickname(nickname);
        }

        if (Bukkit.getPlayer(uuid) != null) {
            user.setDisplayNick();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        syncNickname(uuid);

        Bukkit.getScheduler().runTask(this, () -> {
            if (Bukkit.getPlayer(uuid) != null) {
                syncNickname(uuid);
            }
        });
    }

    @Override
    public void onDisable() {
        if (nameplateManager != null) {
            nameplateManager.stop();
        }

        if (nicknameManager != null) {
            nicknameManager.save();
        }
    }

    public NicknameManager nicknameManager() {
        return nicknameManager;
    }

    public String prefix() {
        return getConfig().getString(
                "prefix",
                "§7[§dCoach Rename§7] "
        );
    }
}
