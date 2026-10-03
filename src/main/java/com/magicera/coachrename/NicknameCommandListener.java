package com.magicera.coachrename;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.*;
import java.util.regex.*;

public final class NicknameCommandListener implements Listener {
    private static final Pattern TOKEN =
            Pattern.compile("\"[^\"]*\"|\\S+");

    // Commands whose first argument is a player.
    private static final Set<String> FIRST_TARGET = Set.of(
            "msg", "tell", "w", "whisper", "m", "pm",
            "tpa", "tpahere", "tphere", "pay"
    );

    private final CoachRenamePlugin plugin;

    public NicknameCommandListener(CoachRenamePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String rewritten = rewrite(
                event.getPlayer(),
                event.getMessage().substring(1)
        );

        if (rewritten == null) {
            event.setCancelled(true);
        } else {
            event.setMessage("/" + rewritten);
        }
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onConsole(ServerCommandEvent event) {
        String rewritten = rewrite(
                event.getSender(), event.getCommand()
        );

        if (rewritten == null) {
            event.setCancelled(true);
        } else {
            event.setCommand(rewritten);
        }
    }

    private String rewrite(CommandSender sender, String command) {
        List<String> tokens = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();

        Matcher matcher = TOKEN.matcher(command);

        while (matcher.find()) {
            tokens.add(matcher.group());
            starts.add(matcher.start());
            ends.add(matcher.end());
        }

        if (tokens.isEmpty()) return command;

        String label = tokens.get(0).toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');

        if (colon >= 0) {
            String namespace = label.substring(0, colon);

            if (!Set.of(
                    "essentials", "luckperms", "coachrename"
            ).contains(namespace)) {
                return command;
            }

            label = label.substring(colon + 1);
        }

        // Route nickname changes through CoachRename.
        if (label.equals("nick") || label.equals("nickname")) {
            return "coachrename:nick"
                    + command.substring(ends.get(0));
        }

        int[] positions;

        if (FIRST_TARGET.contains(label)) {
            positions = new int[]{1};

        } else if (
                (label.equals("tp") || label.equals("teleport"))
                        && tokens.size() <= 3
        ) {
            // Player-to-player teleport forms only.
            positions = new int[]{1, 2};

        } else if (
                (label.equals("lp") || label.equals("luckperms"))
                        && tokens.size() > 2
                        && tokens.get(1).equalsIgnoreCase("user")
        ) {
            positions = new int[]{2};

        } else {
            return command;
        }

        StringBuilder result = new StringBuilder(command);

        // Right-to-left replacements preserve argument offsets.
        // Message bodies and other arguments remain untouched.
        for (int i = positions.length - 1; i >= 0; i--) {
            int position = positions[i];

            if (position >= tokens.size()) continue;

            String input = tokens.get(position);

            if (input.startsWith("\"") && input.endsWith("\"")) {
                input = input.substring(1, input.length() - 1);
            }

            // Leave selectors, coordinates, UUIDs, etc. alone.
            if (!input.matches("[A-Za-z0-9_ ]+")
                    || input.matches("[0-9]+")) {
                continue;
            }

            List<Player> matches = resolve(sender, input);

            if (matches.size() > 1) {
                sender.sendMessage(
                        plugin.prefix()
                                + "§cThat shorthand matches multiple players. "
                                + "Use a full nickname or username."
                );
                return null;
            }

            if (matches.size() == 1) {
                result.replace(
                        starts.get(position),
                        ends.get(position),
                        matches.get(0).getName()
                );
            }
        }

        return result.toString();
    }

    private List<Player> resolve(
            CommandSender sender, String input
    ) {
        // Exact online account names take precedence.
        Player real = Bukkit.getPlayerExact(input);

        if (real != null) {
            return List.of(real);
        }

        String query = compact(input);
        List<Player> exact = new ArrayList<>();
        List<Player> partial = new ArrayList<>();

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (sender instanceof Player viewer
                    && !viewer.canSee(target)) {
                continue;
            }

            String nickname = compact(
                    plugin.nicknameManager().getPlainName(
                            target.getUniqueId(), target.getName()
                    )
            );

            if (nickname.equals(query)) {
                exact.add(target);

            } else if (
                    query.length() >= 2
                            && (nickname.startsWith(query)
                            || target.getName()
                                    .toLowerCase(Locale.ROOT)
                                    .startsWith(query))
            ) {
                partial.add(target);
            }
        }

        return exact.isEmpty() ? partial : exact;
    }

    private String compact(String name) {
        return name.replace(" ", "").toLowerCase(Locale.ROOT);
    }
}
