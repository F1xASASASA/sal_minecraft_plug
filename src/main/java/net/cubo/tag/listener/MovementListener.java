package net.cubo.tag.listener;

import net.cubo.tag.TagPlugin;
import net.cubo.tag.game.GameState;
import net.cubo.tag.game.Role;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class MovementListener implements Listener {

    private final TagPlugin plugin;

    public MovementListener(TagPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (plugin.game().state() != GameState.PLAYING) return;
        var p = e.getPlayer();
        if (!plugin.game().isParticipant(p)) return;
        Role role = plugin.game().roleOf(p);
        if (role != Role.HUNTER && role != Role.RUNNER) return;
        if (p.getLocation().getY() >= plugin.cfg().voidY()) return;
        plugin.game().onPlayerFell(p);
    }
}
