package com.magicera.coachrename;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

public final class NameplateManager implements Listener {

    private static final String HIDDEN_TEAM = "crename_hidden";

    private final CoachRenamePlugin plugin;
    private final NicknameManager nicknameManager;

    private final Map<NameplateKey, TextDisplay> displays = new HashMap<>();
    private final Map<UUID, Long> globalTrueNameRevealUntil = new HashMap<>();
    private final Map<NameplateKey, Long> specificTrueNameRevealUntil = new HashMap<>();

    private BukkitTask task;

    public NameplateManager(CoachRenamePlugin plugin, NicknameManager nicknameManager) {
        this.plugin = plugin;
        this.nicknameManager = nicknameManager;
    }

    public void start() {
        setupHiddenNameTeam();

        long updateTicks = plugin.getConfig().getLong("nameplate.update-ticks", 2L);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, updateTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        for (TextDisplay display : displays.values()) {
            if (display != null && !display.isDead()) {
                display.remove();
            }
        }

        displays.clear();
    }

    public void refreshAll() {
        for (TextDisplay display : displays.values()) {
            if (display != null && !display.isDead()) {
                display.remove();
            }
        }

        displays.clear();
        tick();
    }

    public void revealAllTrueNames(Player viewer, int seconds) {
        globalTrueNameRevealUntil.put(
                viewer.getUniqueId(),
                System.currentTimeMillis() + (seconds * 1000L)
        );

        updateViewer(viewer);
    }

    public void revealSpecificTrueName(Player viewer, Player target, int seconds) {
        NameplateKey key = new NameplateKey(viewer.getUniqueId(), target.getUniqueId());

        specificTrueNameRevealUntil.put(
                key,
                System.currentTimeMillis() + (seconds * 1000L)
        );

        updatePair(viewer, target);
    }

    private void tick() {
        setupHiddenNameTeam();
        cleanupExpiredReveals();

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            updateViewer(viewer);
        }
    }

    private void updateViewer(Player viewer) {
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target)) {
                continue;
            }

            updatePair(viewer, target);
        }
    }

    private void updatePair(Player viewer, Player target) {
        NameplateKey key = new NameplateKey(viewer.getUniqueId(), target.getUniqueId());

        if (!shouldShowNameplate(viewer, target)) {
            removeDisplay(key);
            return;
        }

        TextDisplay display = displays.get(key);

        if (display == null || display.isDead() || !target.getPassengers().contains(display)) {
            display = spawnDisplayFor(viewer, target);
            displays.put(key, display);
        }

        display.text(Component.text(getVisibleName(viewer, target), NamedTextColor.WHITE));

        viewer.showEntity(plugin, display);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(viewer)) {
                online.hideEntity(plugin, display);
            }
        }
    }

    private boolean shouldShowNameplate(Player viewer, Player target) {
        if (!viewer.getWorld().equals(target.getWorld())) {
            return false;
        }

        if (!viewer.canSee(target)) {
            return false;
        }

        if (target.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            return false;
        }

        if (target.isInvisible()) {
            return false;
        }

        return true;
    }

    private TextDisplay spawnDisplayFor(Player viewer, Player target) {
        double yOffset = plugin.getConfig().getDouble("nameplate.y-offset", 2.35);

        TextDisplay display = target.getWorld().spawn(
                target.getLocation(),
                TextDisplay.class,
                entity -> {
                    entity.setPersistent(false);
                    entity.setInvulnerable(true);
                    entity.setGravity(false);
                    entity.setBillboard(Display.Billboard.CENTER);
                    entity.setShadowed(false);
                    entity.setSeeThrough(false);
                    entity.setDefaultBackground(false);
                    entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                    entity.setTeleportDuration(1);
                    entity.setTransformation(entity.getTransformation());
                    entity.text(Component.text(getVisibleName(viewer, target), NamedTextColor.WHITE));
                }
        );

        target.addPassenger(display);

        display.setTransformation(new org.bukkit.util.Transformation(
                new org.joml.Vector3f(0.0F, (float) yOffset, 0.0F),
                new org.joml.AxisAngle4f(),
                new org.joml.Vector3f(1.0F, 1.0F, 1.0F),
                new org.joml.AxisAngle4f()
        ));

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(viewer)) {
                online.hideEntity(plugin, display);
            }
        }

        viewer.showEntity(plugin, display);
        return display;
    }

    private String getVisibleName(Player viewer, Player target) {
        long now = System.currentTimeMillis();

        Long globalUntil = globalTrueNameRevealUntil.get(viewer.getUniqueId());
        if (globalUntil != null && globalUntil > now) {
            return target.getName();
        }

        NameplateKey key = new NameplateKey(viewer.getUniqueId(), target.getUniqueId());
        Long specificUntil = specificTrueNameRevealUntil.get(key);

        if (specificUntil != null && specificUntil > now) {
            return target.getName();
        }

        return nicknameManager.getPlainName(target.getUniqueId(), target.getName());
    }

    private void cleanupExpiredReveals() {
        long now = System.currentTimeMillis();

        globalTrueNameRevealUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
        specificTrueNameRevealUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private void removeDisplay(NameplateKey key) {
        TextDisplay display = displays.remove(key);

        if (display != null && !display.isDead()) {
            display.remove();
        }
    }

    private void setupHiddenNameTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        Team team = scoreboard.getTeam(HIDDEN_TEAM);
        if (team == null) {
            team = scoreboard.registerNewTeam(HIDDEN_TEAM);
        }

        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!team.hasEntry(player.getName())) {
                team.addEntry(player.getName());
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();

        for (TextDisplay display : displays.values()) {
            joined.hideEntity(plugin, display);
        }

        setupHiddenNameTeam();
        Bukkit.getScheduler().runTaskLater(plugin, this::refreshAll, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID leaving = event.getPlayer().getUniqueId();

        Iterator<Map.Entry<NameplateKey, TextDisplay>> iterator = displays.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<NameplateKey, TextDisplay> entry = iterator.next();
            NameplateKey key = entry.getKey();

            if (key.viewerId().equals(leaving) || key.targetId().equals(leaving)) {
                TextDisplay display = entry.getValue();

                if (display != null && !display.isDead()) {
                    display.remove();
                }

                iterator.remove();
            }
        }

        globalTrueNameRevealUntil.remove(leaving);
        specificTrueNameRevealUntil.keySet().removeIf(key ->
                key.viewerId().equals(leaving) || key.targetId().equals(leaving)
        );
    }

    private record NameplateKey(UUID viewerId, UUID targetId) {
    }
}
