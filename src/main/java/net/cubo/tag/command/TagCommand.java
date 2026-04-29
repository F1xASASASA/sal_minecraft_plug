package net.cubo.tag.command;

import net.cubo.tag.TagPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TagCommand implements CommandExecutor {

    private final TagPlugin plugin;

    public TagCommand(TagPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (args.length == 0) { help(s); return true; }

        switch (args[0].toLowerCase()) {
            case "help" -> help(s);
            case "start" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Только игрок."); return true; }
                plugin.game().startVoting(p);
            }
            case "stop" -> plugin.game().stop();
            case "reload" -> {
                plugin.cfg().reload();
                s.sendMessage(Component.text("Конфиг перезагружен.").color(NamedTextColor.GREEN));
            }
            case "setpoint" -> setpoint(s, args);
            case "setvoid" -> setvoid(s);
            case "show" -> show(s);
            default -> help(s);
        }
        return true;
    }

    private void setpoint(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) { s.sendMessage("Только игрок."); return; }
        if (args.length < 2) {
            s.sendMessage("Укажи: lobby | hunter | runner1 | runner2 | runner3");
            return;
        }
        String key = args[1].toLowerCase();
        if (!java.util.Set.of("lobby", "hunter", "runner1", "runner2", "runner3").contains(key)) {
            s.sendMessage("Неизвестная точка: " + key);
            return;
        }
        plugin.cfg().setLocation(key, p.getLocation());
        p.sendMessage(Component.text("Точка " + key + " установлена.").color(NamedTextColor.GREEN));
    }

    private void setvoid(CommandSender s) {
        if (!(s instanceof Player p)) { s.sendMessage("Только игрок."); return; }
        int y = p.getLocation().getBlockY();
        plugin.cfg().setVoidY(y);
        p.sendMessage(Component.text("void-y = " + y).color(NamedTextColor.GREEN));
    }

    private void show(CommandSender s) {
        s.sendMessage(Component.text("=== САЛОЧКИ ===").color(NamedTextColor.GOLD));
        s.sendMessage("Лобби: " + fmt(plugin.cfg().lobby()));
        s.sendMessage("Охотник: " + fmt(plugin.cfg().hunter()));
        s.sendMessage("Бегун 1: " + fmt(plugin.cfg().runner1()));
        s.sendMessage("Бегун 2: " + fmt(plugin.cfg().runner2()));
        s.sendMessage("Бегун 3: " + fmt(plugin.cfg().runner3()));
        s.sendMessage("Воид Y: " + plugin.cfg().voidY());
        s.sendMessage("Тайминги: vote=" + plugin.cfg().voteTime() + ", game=" + plugin.cfg().gameTime() + ", end=" + plugin.cfg().endTime());
        s.sendMessage("Состояние: " + plugin.game().state());
    }

    private String fmt(org.bukkit.Location l) {
        return String.format("%.1f, %.1f, %.1f", l.getX(), l.getY(), l.getZ());
    }

    private void help(CommandSender s) {
        s.sendMessage(Component.text("=== Салочки ===").color(NamedTextColor.GOLD));
        s.sendMessage("/tag start - запустить игру (начнётся голосование)");
        s.sendMessage("/tag stop - остановить");
        s.sendMessage("/tag reload - перезагрузить конфиг");
        s.sendMessage("/tag show - показать конфиг");
        s.sendMessage("/tag setpoint <lobby|hunter|runner1|runner2|runner3>");
        s.sendMessage("/tag setvoid - установить Y-уровень воида (по позиции)");
    }
}
