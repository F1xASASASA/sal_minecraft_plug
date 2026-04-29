package net.cubo.tag.listener;

import net.cubo.tag.TagPlugin;
import net.cubo.tag.game.GameState;
import net.cubo.tag.game.Role;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

public class DamageListener implements Listener {

    private final TagPlugin plugin;

    public DamageListener(TagPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onHit(EntityDamageByEntityEvent e) {
        if (plugin.game().state() != GameState.PLAYING) return;
        if (!(e.getDamager() instanceof Player damager)) return;
        if (!(e.getEntity() instanceof Player victim)) return;

        boolean dPart = plugin.game().isParticipant(damager);
        boolean vPart = plugin.game().isParticipant(victim);
        if (!dPart && !vPart) return;

        Role dr = plugin.game().roleOf(damager);
        Role vr = plugin.game().roleOf(victim);

        if (dr == Role.HUNTER && vr == Role.RUNNER) {
            e.setCancelled(false);
            e.setDamage(0.0);
            plugin.game().onRunnerHit(victim, damager);
            return;
        }

        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {

        if (e instanceof EntityDamageByEntityEvent) return;
        if (!(e.getEntity() instanceof Player p)) return;
        if (!plugin.game().isParticipant(p)) return;
        if (plugin.game().state() == GameState.IDLE) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (plugin.game().state() != GameState.VOTING) return;

        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getItem() == null) return;
        org.bukkit.Material m = e.getItem().getType();
        if (m != org.bukkit.Material.GRAY_DYE && m != org.bukkit.Material.LIME_DYE) return;
        Player p = e.getPlayer();
        if (!plugin.game().isParticipant(p)) return;
        plugin.game().onVoteClick(p);
        e.setCancelled(true);
    }
}
