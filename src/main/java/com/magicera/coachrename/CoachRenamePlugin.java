package com.magicera.coachrename;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class CoachRenamePlugin extends JavaPlugin {

    private NicknameManager nicknameManager;
    private NameplateManager nameplateManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.nicknameManager = new NicknameManager(this);
        this.nicknameManager.load();

        this.nameplateManager = new NameplateManager(this, nicknameManager);
        this.nameplateManager.start();

        NameCommands commands = new NameCommands(this, nicknameManager, nameplateManager);

        getCommand("nick").setExecutor(commands);
        getCommand("nickclear").setExecutor(commands);
        getCommand("truename").setExecutor(commands);

        Bukkit.getPluginManager().registerEvents(new ChatListener(nicknameManager), this);
        Bukkit.getPluginManager().registerEvents(nameplateManager, this);
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

    public String prefix() {
        return getConfig().getString("prefix", "§7[§dCoach Rename§7] ");
    }
}
