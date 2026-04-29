package net.cubo.tag;

import net.cubo.tag.command.TagCommand;
import net.cubo.tag.config.TagConfig;
import net.cubo.tag.game.GameManager;
import net.cubo.tag.listener.DamageListener;
import net.cubo.tag.listener.JoinQuitListener;
import net.cubo.tag.listener.MovementListener;
import net.cubo.tag.listener.WorldInteractListener;
import org.bukkit.plugin.java.JavaPlugin;

public class TagPlugin extends JavaPlugin {

    private static TagPlugin instance;
    private TagConfig tagConfig;
    private GameManager gameManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.tagConfig = new TagConfig(this);
        this.gameManager = new GameManager(this);

        getCommand("tag").setExecutor(new TagCommand(this));
        getCommand("salochki").setExecutor(new TagCommand(this));

        getServer().getPluginManager().registerEvents(new DamageListener(this), this);
        getServer().getPluginManager().registerEvents(new MovementListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldInteractListener(this), this);

        getLogger().info("Tag (Салочки) загружен. /tag help для команд.");
    }

    @Override
    public void onDisable() {
        if (gameManager != null && gameManager.isRunning()) {
            gameManager.stop();
        }
    }

    public static TagPlugin get() { return instance; }
    public TagConfig cfg() { return tagConfig; }
    public GameManager game() { return gameManager; }
}
