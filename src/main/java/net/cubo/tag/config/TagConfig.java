package net.cubo.tag.config;

import net.cubo.tag.TagPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.List;

public class TagConfig {

    private final TagPlugin plugin;
    private World world;

    private Location lobby, hunter, runner1, runner2, runner3;

    private int voidY;
    private int voteTime, gameTime, endTime, hunterRespawnTime;
    private int minPlayers;
    private List<String> themes = Collections.emptyList();
    private String theme;
    private String countdownTheme;
    private float themeVolume;

    public TagConfig(TagPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        boolean dirty = false;
        if (!cfg.contains("min-players"))    { cfg.set("min-players", 1); dirty = true; }
        if (!cfg.contains("theme"))          { cfg.set("theme", "parkour_tag:theme"); dirty = true; }
        if (!cfg.contains("countdown-theme")){ cfg.set("countdown-theme", "parkour_tag:countdown"); dirty = true; }
        if (!cfg.contains("theme-volume"))   { cfg.set("theme-volume", 1.0); dirty = true; }
        if (!cfg.contains("void-y"))         { cfg.set("void-y", 40); dirty = true; }
        if (!cfg.contains("timings.vote"))   { cfg.set("timings.vote", 17); dirty = true; }
        if (!cfg.contains("timings.game"))   { cfg.set("timings.game", 62); dirty = true; }
        if (!cfg.contains("timings.end"))    { cfg.set("timings.end", 27); dirty = true; }
        if (!cfg.contains("timings.hunter-respawn")) { cfg.set("timings.hunter-respawn", 5); dirty = true; }
        if (dirty) plugin.saveConfig();

        String worldName = cfg.getString("world", "world");
        this.world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Мир '" + worldName + "' не найден! Использую первый доступный.");
            if (!Bukkit.getWorlds().isEmpty()) this.world = Bukkit.getWorlds().get(0);
        }

        this.lobby = loadLoc(cfg, "points.lobby");
        this.hunter = loadLoc(cfg, "points.hunter");
        this.runner1 = loadLoc(cfg, "points.runner1");
        this.runner2 = loadLoc(cfg, "points.runner2");
        this.runner3 = loadLoc(cfg, "points.runner3");

        this.voidY = cfg.getInt("void-y", 40);
        this.voteTime = cfg.getInt("timings.vote", 17);
        this.gameTime = cfg.getInt("timings.game", 62);
        this.endTime = cfg.getInt("timings.end", 27);
        this.hunterRespawnTime = cfg.getInt("timings.hunter-respawn", 5);
        this.minPlayers = cfg.getInt("min-players", 1);
        this.themes = cfg.getStringList("themes");
        this.theme = cfg.getString("theme", "parkour_tag:theme");
        this.countdownTheme = cfg.getString("countdown-theme", "parkour_tag:countdown");
        double v = cfg.getDouble("theme-volume", 1.0);
        this.themeVolume = (float) Math.max(0.0, Math.min(1.0, v));

        plugin.getLogger().info("Конфиг загружен: min-players=" + minPlayers + ", theme='" + theme + "', volume=" + themeVolume);
    }

    private Location loadLoc(FileConfiguration cfg, String path) {
        ConfigurationSection s = cfg.getConfigurationSection(path);
        if (s == null) return new Location(world, 0, 100, 0);
        return new Location(world,
                s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    public void setLocation(String key, Location loc) {
        FileConfiguration cfg = plugin.getConfig();
        cfg.set("points." + key + ".x", loc.getX());
        cfg.set("points." + key + ".y", loc.getY());
        cfg.set("points." + key + ".z", loc.getZ());
        cfg.set("points." + key + ".yaw", loc.getYaw());
        cfg.set("points." + key + ".pitch", loc.getPitch());
        plugin.saveConfig();
        reload();
    }

    public void setVoidY(int y) {
        plugin.getConfig().set("void-y", y);
        plugin.saveConfig();
        reload();
    }

    public World world() { return world; }
    public Location lobby() { return lobby.clone(); }
    public Location hunter() { return hunter.clone(); }
    public Location runner1() { return runner1.clone(); }
    public Location runner2() { return runner2.clone(); }
    public Location runner3() { return runner3.clone(); }
    public int voidY() { return voidY; }
    public int voteTime() { return voteTime; }
    public int gameTime() { return gameTime; }
    public int endTime() { return endTime; }
    public int hunterRespawnTime() { return hunterRespawnTime; }
    public int minPlayers() { return minPlayers; }
    public List<String> themes() { return themes; }
    public String theme() { return theme; }
    public String countdownTheme() { return countdownTheme; }
    public float themeVolume() { return themeVolume; }
}
