package net.cubo.tag.listener;

import net.cubo.tag.TagPlugin;
import net.cubo.tag.game.GameState;
import net.cubo.tag.game.PlayerData;
import net.cubo.tag.game.Role;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class JoinQuitListener implements Listener {

    private final TagPlugin plugin;

    public JoinQuitListener(TagPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        PlayerData d = plugin.game().data(e.getPlayer());
        if (d == null) return;
        Role role = d.role();
        plugin.game().removePlayer(e.getPlayer().getUniqueId());

        if (plugin.game().state() == GameState.PLAYING && role == Role.HUNTER) {
            plugin.game().broadcastParticipants(Component.text("§cОхотник вышел из игры — раунд остановлен."));
            plugin.game().stop();
        }
    }
}
