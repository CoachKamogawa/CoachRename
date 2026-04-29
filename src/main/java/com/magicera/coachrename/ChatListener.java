package com.magicera.names;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class ChatListener implements Listener {

    private final NicknameManager nicknameManager;
    private final LegacyComponentSerializer legacy =
            LegacyComponentSerializer.legacyAmpersand();

    public ChatListener(NicknameManager nicknameManager) {
        this.nicknameManager = nicknameManager;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        event.renderer((source, sourceDisplayName, message, viewer) -> {
            String display = nicknameManager.getDisplayName(source.getUniqueId(), source.getName());

            return Component.empty()
                    .append(legacy.deserialize(display))
                    .append(Component.text("§7: §f"))
                    .append(message);
        });
    }
}
