package com.magicera.coachrename;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class NicknameManager {

    private final CoachRenamePlugin plugin;
    private final File file;

    private YamlConfiguration data;

    private final Map<UUID, NicknameRecord> records = new HashMap<>();
    private final Map<String, UUID> rawNicknameIndex = new HashMap<>();
    private final Map<String, UUID> knownUsernameIndex = new HashMap<>();

    public NicknameManager(CoachRenamePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "nicknames.yml");
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        data = YamlConfiguration.loadConfiguration(file);
        records.clear();
        rawNicknameIndex.clear();
        knownUsernameIndex.clear();

        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) {
            return;
        }

        for (String key : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);

                String display = players.getString(key + ".displayNickname", "");
                String plain = players.getString(key + ".plainNickname", "");
                String raw = players.getString(key + ".rawNickname", "");
                String username = players.getString(key + ".lastKnownUsername", "");

                if (plain.isBlank() || raw.isBlank()) {
                    continue;
                }

                NicknameRecord record = new NicknameRecord(display, plain, raw, username);
                records.put(uuid, record);
                rawNicknameIndex.put(raw, uuid);

                if (!username.isBlank()) {
                    knownUsernameIndex.put(normalizePlain(username), uuid);
                }

            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        if (data == null) {
            data = new YamlConfiguration();
        }

        data.set("players", null);

        for (Map.Entry<UUID, NicknameRecord> entry : records.entrySet()) {
            String path = "players." + entry.getKey();
            NicknameRecord record = entry.getValue();

            data.set(path + ".displayNickname", record.displayNickname());
            data.set(path + ".plainNickname", record.plainNickname());
            data.set(path + ".rawNickname", record.rawNickname());
            data.set(path + ".lastKnownUsername", record.lastKnownUsername());
        }

        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save nicknames.yml");
            e.printStackTrace();
        }
    }

    public NicknameResult setNickname(UUID uuid, String realUsername, String input) {
        String display = input.trim();
        String plain = toPlainNickname(display);
        String raw = normalizePlain(plain);

        int min = plugin.getConfig().getInt("nickname.min-raw-length", 3);
        int max = plugin.getConfig().getInt("nickname.max-plain-length", 24);

        if (raw.length() < min) {
            return NicknameResult.fail("That nickname is too short.");
        }

        if (plain.length() > max) {
            return NicknameResult.fail("That nickname is too long.");
        }

        if (!plain.matches("[A-Za-z0-9_ ]+")) {
            return NicknameResult.fail("Nicknames can only use letters, numbers, spaces, and underscores.");
        }

        UUID nicknameOwner = rawNicknameIndex.get(raw);
        if (nicknameOwner != null && !nicknameOwner.equals(uuid)) {
            return NicknameResult.fail("That nickname is already taken.");
        }

        UUID usernameOwner = findKnownUsernameOwner(raw);
        if (usernameOwner != null && !usernameOwner.equals(uuid)) {
            return NicknameResult.fail("That nickname is reserved because it matches a real username.");
        }

        NicknameRecord old = records.get(uuid);
        if (old != null) {
            rawNicknameIndex.remove(old.rawNickname());
        }

        String realRaw = normalizePlain(realUsername);
        knownUsernameIndex.put(realRaw, uuid);

        NicknameRecord record = new NicknameRecord(display, plain, raw, realUsername);
        records.put(uuid, record);
        rawNicknameIndex.put(raw, uuid);

        save();
        return NicknameResult.success(record);
    }

    public void clearNickname(UUID uuid) {
        NicknameRecord old = records.remove(uuid);

        if (old != null) {
            rawNicknameIndex.remove(old.rawNickname());
        }

        save();
    }

    public NicknameRecord getRecord(UUID uuid) {
        return records.get(uuid);
    }

    public String getPlainName(UUID uuid, String realUsername) {
        NicknameRecord record = records.get(uuid);
        return record == null ? realUsername : record.plainNickname();
    }

    public String getDisplayName(UUID uuid, String realUsername) {
        NicknameRecord record = records.get(uuid);
        return record == null ? realUsername : record.displayNickname();
    }

    public UUID findByNicknameOrUsername(String input) {
        String raw = normalizePlain(toPlainNickname(input));

        UUID byNick = rawNicknameIndex.get(raw);
        if (byNick != null) {
            return byNick;
        }

        for (var player : Bukkit.getOnlinePlayers()) {
            if (normalizePlain(player.getName()).equals(raw)) {
                return player.getUniqueId();
            }
        }

        UUID known = knownUsernameIndex.get(raw);
        if (known != null) {
            return known;
        }

        return null;
    }

    private UUID findKnownUsernameOwner(String raw) {
        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            String name = offlinePlayer.getName();
            if (name != null && normalizePlain(name).equals(raw)) {
                return offlinePlayer.getUniqueId();
            }
        }

        return knownUsernameIndex.get(raw);
    }

    public String toPlainNickname(String input) {
        String noColors = input
                .replaceAll("(?i)&x(&[0-9A-F]){6}", "")
                .replaceAll("(?i)§x(§[0-9A-F]){6}", "")
                .replaceAll("(?i)&[0-9A-FK-OR]", "")
                .replaceAll("(?i)§[0-9A-FK-OR]", "");

        return noColors.trim().replaceAll("\\s+", " ");
    }

    public String normalizePlain(String input) {
        return input.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public record NicknameResult(boolean success, String message, NicknameRecord record) {
        public static NicknameResult success(NicknameRecord record) {
            return new NicknameResult(true, "", record);
        }

        public static NicknameResult fail(String message) {
            return new NicknameResult(false, message, null);
        }
    }
}
