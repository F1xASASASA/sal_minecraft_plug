package net.cubo.tag.game;

import net.cubo.tag.TagPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;

public class GameManager {

    private final TagPlugin plugin;
    private GameState state = GameState.IDLE;
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private BukkitTask task;
    private int ticksLeft;

    private static final int HUNTER_CAGE_SECONDS = 12;

    private int cageTicksLeft = 0;

    private final Map<Location, BlockData> cageBlocks = new LinkedHashMap<>();

    private boolean soloMode = false;

    public GameManager(TagPlugin plugin) {
        this.plugin = plugin;
    }

    public GameState state() { return state; }
    public boolean isRunning() { return state != GameState.IDLE; }
    public PlayerData data(Player p) { return players.get(p.getUniqueId()); }
    public PlayerData data(UUID uuid) { return players.get(uuid); }
    public Collection<PlayerData> allData() { return players.values(); }

    public Role roleOf(Player p) {
        PlayerData d = data(p);
        return d == null ? Role.NONE : d.role();
    }

    public int aliveRunners() {
        int c = 0;
        for (PlayerData d : players.values()) {
            if (d.role() == Role.RUNNER) c++;
        }
        return c;
    }

    public boolean startVoting(Player admin) {
        if (state != GameState.IDLE) {
            admin.sendMessage(Component.text("Игра уже идёт.").color(NamedTextColor.RED));
            return false;
        }

        Set<Player> participants = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() != GameMode.SPECTATOR) participants.add(p);
        }

        if (participants.size() < plugin.cfg().minPlayers()) {
            admin.sendMessage(Component.text("Нужно минимум " + plugin.cfg().minPlayers() + " игроков.").color(NamedTextColor.RED));
            return false;
        }

        players.clear();
        for (Player p : participants) {
            PlayerData d = new PlayerData(p.getUniqueId());
            players.put(p.getUniqueId(), d);
            prepPlayer(p);
            p.teleport(plugin.cfg().lobby());
            giveVoteItem(p);
        }

        playTheme();

        setState(GameState.VOTING);
        ticksLeft = plugin.cfg().voteTime() * 20;
        startTicker();

        broadcastParticipants(Component.text("§6§l⚡ САЛОЧКИ ⚡"));
        broadcastParticipants(Component.text("§eГолосование за охотника! ПКМ по топору если хочешь быть охотником."));
        for (Player p : participants) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_IN, 1f, 1.2f);
        }
        return true;
    }

    private String currentTheme = null;
    private String currentCountdown = null;

    private void playTheme() {
        stopTheme();
        String pick = plugin.cfg().theme();
        if (pick == null || pick.isBlank()) {
            plugin.getLogger().info("theme в конфиге пуст — основная музыка не запускается.");
            return;
        }
        currentTheme = pick;
        float vol = plugin.cfg().themeVolume();
        plugin.getLogger().info("Запускаю основную тему '" + pick + "' в канал RECORDS, volume=" + vol);
        for (PlayerData d : players.values()) {
            Player p = Bukkit.getPlayer(d.uuid());
            if (p == null) continue;
            try {
                p.playSound(p.getLocation(), pick, org.bukkit.SoundCategory.RECORDS, vol, 1f);
            } catch (Throwable t) {
                plugin.getLogger().warning("Не удалось проиграть '" + pick + "': " + t.getMessage());
            }
        }
    }

    private void playCountdownTheme() {
        String pick = plugin.cfg().countdownTheme();
        if (pick == null || pick.isBlank()) return;
        currentCountdown = pick;
        float vol = plugin.cfg().themeVolume();
        plugin.getLogger().info("Запускаю countdown '" + pick + "' в канал VOICE поверх темы, volume=" + vol);
        for (PlayerData d : players.values()) {
            Player p = Bukkit.getPlayer(d.uuid());
            if (p == null) continue;
            try {
                p.playSound(p.getLocation(), pick, org.bukkit.SoundCategory.VOICE, vol, 1f);
            } catch (Throwable t) {
                plugin.getLogger().warning("Не удалось проиграть countdown '" + pick + "': " + t.getMessage());
            }
        }
    }

    private void stopTheme() {
        if (currentTheme == null) return;
        String key = currentTheme;
        currentTheme = null;
        for (PlayerData d : players.values()) {
            Player p = Bukkit.getPlayer(d.uuid());
            if (p == null) continue;
            try {
                p.stopSound(key, org.bukkit.SoundCategory.RECORDS);
            } catch (Throwable ignored) {}
        }
    }

    private void stopCountdownTheme() {
        if (currentCountdown == null) return;
        String key = currentCountdown;
        currentCountdown = null;
        for (PlayerData d : players.values()) {
            Player p = Bukkit.getPlayer(d.uuid());
            if (p == null) continue;
            try {
                p.stopSound(key, org.bukkit.SoundCategory.VOICE);
            } catch (Throwable ignored) {}
        }
    }

    private void prepPlayer(Player p) {
        p.setGameMode(GameMode.ADVENTURE);
        var maxHp = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (maxHp != null) maxHp.setBaseValue(maxHp.getDefaultValue());
        var atk = p.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(atk.getDefaultValue());
        var kbr = p.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE);
        if (kbr != null) kbr.setBaseValue(kbr.getDefaultValue());
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setExp(0f);
        p.setLevel(0);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.getInventory().clear();
    }

    private void giveVoteItem(Player p) {

        p.getInventory().setItem(4, makeVoteItem(false));
    }

    private ItemStack makeVoteItem(boolean wantsHunter) {
        Material mat = wantsHunter ? Material.LIME_DYE : Material.GRAY_DYE;
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (wantsHunter) {
            meta.displayName(Component.text("§a§l✓ Вы в очереди охотников")
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            meta.lore(java.util.List.of(
                    Component.text("§7ПКМ — выйти из очереди")
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
            ));
        } else {
            meta.displayName(Component.text("§7§l⚔ Стать охотником?")
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            meta.lore(java.util.List.of(
                    Component.text("§7ПКМ — встать в очередь")
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                    Component.text("§7охотников")
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
            ));
        }
        it.setItemMeta(meta);
        return it;
    }

    public void onVoteClick(Player p) {
        if (state != GameState.VOTING) return;
        PlayerData d = data(p);
        if (d == null) return;

        long now = System.currentTimeMillis();
        if (now - d.lastVoteClickMs() < 250) return;
        d.setLastVoteClickMs(now);

        d.setWantsHunter(!d.wantsHunter());

        p.getInventory().setItem(4, makeVoteItem(d.wantsHunter()));
        if (d.wantsHunter()) {
            p.sendMessage(Component.text("§a✓ Ты в списке кандидатов в охотники."));
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.5f);
        } else {
            p.sendMessage(Component.text("§7✗ Ты убрал свой голос."));
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
        }
    }

    private void startGame() {

        List<PlayerData> volunteers = new ArrayList<>();
        for (PlayerData d : players.values()) {
            if (d.wantsHunter()) volunteers.add(d);
        }

        PlayerData hunterData;
        if (!volunteers.isEmpty()) {
            hunterData = volunteers.get(new Random().nextInt(volunteers.size()));
        } else {
            List<PlayerData> all = new ArrayList<>(players.values());
            if (all.isEmpty()) { stop(); return; }
            hunterData = all.get(new Random().nextInt(all.size()));
            broadcastParticipants(Component.text("§7Никто не голосовал — охотник выбран случайно."));
        }
        hunterData.setRole(Role.HUNTER);

        soloMode = (players.size() == 1);

        int runnerIdx = 0;
        for (PlayerData d : players.values()) {
            if (d == hunterData) continue;
            d.setRole(Role.RUNNER);
            Player p = Bukkit.getPlayer(d.uuid());
            if (p == null) continue;

            org.bukkit.Location spawn = switch (runnerIdx % 3) {
                case 0 -> plugin.cfg().runner1();
                case 1 -> plugin.cfg().runner2();
                default -> plugin.cfg().runner3();
            };
            runnerIdx++;
            setupRunner(p, spawn);
        }

        Player hunter = Bukkit.getPlayer(hunterData.uuid());
        if (hunter != null) setupHunter(hunter);

        buildCagesForAll();
        cageTicksLeft = HUNTER_CAGE_SECONDS * 20;

        playCountdownTheme();

        setState(GameState.PLAYING);
        ticksLeft = plugin.cfg().gameTime() * 20;

        plugin.cfg().world().setPVP(true);

        for (PlayerData d : players.values()) {
            Player p = Bukkit.getPlayer(d.uuid());
            if (p == null) continue;
            p.showTitle(Title.title(
                    Component.text("ПРИГОТОВЬТЕСЬ!").color(NamedTextColor.GOLD),
                    Component.text("Старт через " + HUNTER_CAGE_SECONDS + " секунд").color(NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500))
            ));
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
            if (d.role() == Role.HUNTER) {
                p.sendMessage(Component.text("§c§lВы — ОХОТНИК! §r§7Через " + HUNTER_CAGE_SECONDS + " секунд начинайте ловить!"));
            } else {
                p.sendMessage(Component.text("§a§lВы — БЕГУН! §r§7Через " + HUNTER_CAGE_SECONDS + " секунд начинайте убегать!"));
            }
        }
    }

    private void setupRunner(Player p, org.bukkit.Location spawn) {
        prepPlayer(p);
        p.teleport(spawn);
        p.setGameMode(GameMode.ADVENTURE);

        p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, true, false));
    }

    private void setupHunter(Player p) {
        prepPlayer(p);
        p.teleport(plugin.cfg().hunter());
        p.setGameMode(GameMode.ADVENTURE);

    }

    private void buildCageAt(Location center) {
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 2; dy++) {

                    boolean isInside = (dy >= 0 && dy <= 1) && (dx == 0 && dz == 0);
                    if (isInside) continue;
                    Block b = center.getWorld().getBlockAt(cx + dx, cy + dy, cz + dz);

                    if (!b.getType().isAir()) continue;
                    cageBlocks.put(b.getLocation(), b.getBlockData().clone());
                    b.setType(Material.BARRIER, false);
                }
            }
        }
    }

    private void buildCagesForAll() {
        cageBlocks.clear();
        for (PlayerData d : players.values()) {
            Player p = Bukkit.getPlayer(d.uuid());
            if (p == null) continue;
            buildCageAt(p.getLocation());
        }
    }

    private void removeAllCages() {
        for (Map.Entry<Location, BlockData> e : cageBlocks.entrySet()) {
            Block b = e.getKey().getBlock();
            if (b.getType() == Material.BARRIER) {
                b.setBlockData(e.getValue(), false);
            }
        }
        cageBlocks.clear();
    }

    private void startTicker() {
        if (task != null) task.cancel();
        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void tick() {
        if (state == GameState.IDLE) return;

        if (state == GameState.PLAYING && !plugin.cfg().world().getPVP()) {
            plugin.cfg().world().setPVP(true);
        }

        ticksLeft--;
        int sec = Math.max(0, ticksLeft / 20);

        if (state == GameState.VOTING) {
            tickVoting(sec);
            if (ticksLeft <= 0) startGame();
        } else if (state == GameState.PLAYING) {
            tickPlaying(sec);
            if (ticksLeft <= 0) {
                if (soloMode) {

                    endTimeUp();
                } else if (aliveRunners() > 0) {
                    endRunnersWin();
                } else {
                    endHunterWin();
                }
            }
        } else if (state == GameState.ENDING) {
            if (ticksLeft <= 0) backToLobby();
        }
    }

    private void tickVoting(int sec) {
        long votes = players.values().stream().filter(PlayerData::wantsHunter).count();
        for (PlayerData d : players.values()) {
            Player p = Bukkit.getPlayer(d.uuid());
            if (p == null) continue;
            p.sendActionBar(Component.text(
                    "§eГолосование за охотника §7| §6" + sec + " сек §7| §cКандидатов: §f" + votes
            ));
        }
    }

    private void tickPlaying(int sec) {
        int alive = aliveRunners();

        if (cageTicksLeft > 0) {
            cageTicksLeft--;

            if (cageTicksLeft % 20 == 0 && cageTicksLeft > 0) {
                for (PlayerData d : players.values()) {
                    Player p = Bukkit.getPlayer(d.uuid());
                    if (p != null) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.5f);
                }
            }

            if (cageTicksLeft == 0) {
                removeAllCages();

                stopCountdownTheme();
                for (PlayerData d : players.values()) {
                    Player p = Bukkit.getPlayer(d.uuid());
                    if (p == null) continue;
                    p.showTitle(Title.title(
                            Component.text("ВПЕРЁД!").color(NamedTextColor.RED),
                            Component.text(d.role() == Role.HUNTER ? "Лови бегунов!" : "Беги!")
                                    .color(NamedTextColor.YELLOW),
                            Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(1), Duration.ofMillis(300))
                    ));
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
                }
            }
        }

        int cageSec = (cageTicksLeft + 19) / 20;

        if (ticksLeft % 5 == 0) {

            Player hunterPlayer = null;
            for (PlayerData hd : players.values()) {
                if (hd.role() == Role.HUNTER) {
                    hunterPlayer = Bukkit.getPlayer(hd.uuid());
                    break;
                }
            }

            for (PlayerData d : players.values()) {
                Player p = Bukkit.getPlayer(d.uuid());
                if (p == null) continue;
                String msg;
                if (cageTicksLeft > 0) {

                    msg = "§e⏳ Старт через §6" + cageSec + " сек";
                } else {
                    msg = switch (d.role()) {
                        case HUNTER -> "§c🗡 Осталось поймать: §e" + alive + " §7| §6Время: §e" + sec + " сек";
                        case RUNNER -> {
                            String dist;
                            if (hunterPlayer != null && hunterPlayer.getWorld().equals(p.getWorld())) {
                                int blocks = (int) Math.round(hunterPlayer.getLocation().distance(p.getLocation()));
                                dist = "§e" + blocks + " §7блоков";
                            } else {
                                dist = "§7—";
                            }
                            yield "§aДо охотника: " + dist + " §7| §6Время: §e" + sec + " сек";
                        }
                        case SPECTATOR -> "§7Вы выбыли §8| §6Время: §e" + sec + " сек";
                        default -> "";
                    };
                }
                p.sendActionBar(Component.text(msg));
            }
        }

        for (PlayerData d : players.values()) {
            if (d.role() == Role.HUNTER && d.fallingHunter()) {
                int rt = d.respawnTicks() - 1;
                d.setRespawnTicks(rt);
                if (rt <= 0) hunterRespawn(d);
            }
        }

        if (sec == 10 || sec == 5 || sec == 3 || sec == 2 || sec == 1) {
            if (ticksLeft % 20 == 0) {
                for (PlayerData d : players.values()) {
                    Player p = Bukkit.getPlayer(d.uuid());
                    if (p != null) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, sec <= 3 ? 1.5f : 1f);
                }
            }
        }

        if (!soloMode && alive <= 0) endHunterWin();
    }

    public void onRunnerHit(Player runner, Player hunter) {
        if (state != GameState.PLAYING) return;
        PlayerData rd = data(runner);
        PlayerData hd = data(hunter);
        if (rd == null || hd == null) return;
        if (rd.role() != Role.RUNNER || hd.role() != Role.HUNTER) return;

        rd.setRole(Role.SPECTATOR);
        runner.setGameMode(GameMode.SPECTATOR);
        for (PotionEffect e : runner.getActivePotionEffects()) runner.removePotionEffect(e.getType());
        runner.teleport(runner.getLocation().clone().add(0, 3, 0));

        runner.showTitle(Title.title(
                Component.text("ВАС ПОЙМАЛИ!").color(NamedTextColor.RED),
                Component.text("Наблюдайте за оставшимися").color(NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500))
        ));
        runner.playSound(runner.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.5f);

        broadcastParticipants(Component.text("§c✦ §f" + runner.getName() + " §cпойман(а) охотником!"));
    }

    public void onPlayerFell(Player p) {
        if (state != GameState.PLAYING) return;
        PlayerData d = data(p);
        if (d == null) return;
        if (p.getLocation().getY() >= plugin.cfg().voidY()) return;

        if (d.role() == Role.RUNNER) {

            d.setRole(Role.SPECTATOR);
            p.setGameMode(GameMode.SPECTATOR);
            for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
            org.bukkit.Location spec = plugin.cfg().hunter().clone().add(0, 20, 0);
            p.teleport(spec);
            p.showTitle(Title.title(
                    Component.text("ВЫ УПАЛИ!").color(NamedTextColor.RED),
                    Component.text("Наблюдайте за игрой").color(NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500))
            ));
            broadcastParticipants(Component.text("§e" + p.getName() + " §cупал и выбыл!"));
        } else if (d.role() == Role.HUNTER && !d.fallingHunter()) {

            d.setFallingHunter(true);
            d.setRespawnTicks(plugin.cfg().hunterRespawnTime() * 20);
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(plugin.cfg().hunter().clone().add(0, 20, 0));

            int sec = plugin.cfg().hunterRespawnTime();

            Title hunterFellTitle = Title.title(
                    Component.text("ОХОТНИК УПАЛ!").color(NamedTextColor.RED),
                    Component.text("Возвращается через " + sec + " сек").color(NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(400))
            );
            for (PlayerData pd : players.values()) {
                Player pl = Bukkit.getPlayer(pd.uuid());
                if (pl == null) continue;
                pl.showTitle(hunterFellTitle);
                pl.playSound(pl.getLocation(), Sound.ENTITY_WITHER_HURT, 0.6f, 1.5f);
            }
            broadcastParticipants(Component.text("§c☠ §e" + p.getName()
                    + " §c(охотник) упал! §7Респавн через " + sec + " сек."));
        }
    }

    private void hunterRespawn(PlayerData d) {
        Player p = Bukkit.getPlayer(d.uuid());
        if (p == null) return;
        d.setFallingHunter(false);
        setupHunter(p);
        p.sendMessage(Component.text("§aРеспавн!"));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }

    private void endHunterWin() {
        if (state == GameState.ENDING) return;
        setState(GameState.ENDING);
        ticksLeft = plugin.cfg().endTime() * 20;
        for (PlayerData d : players.values()) {
            Player p = Bukkit.getPlayer(d.uuid());
            if (p == null) continue;
            p.showTitle(Title.title(
                    Component.text("ОХОТНИК ПОБЕДИЛ!").color(NamedTextColor.RED),
                    Component.text("Все бегуны пойманы").color(NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofSeconds(1))
            ));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        broadcastParticipants(Component.text("§c§l⚔ ОХОТНИК ПОБЕДИЛ! ⚔"));
    }

    private void endRunnersWin() {
        if (state == GameState.ENDING) return;
        setState(GameState.ENDING);
        ticksLeft = plugin.cfg().endTime() * 20;
        for (PlayerData d : players.values()) {
            Player p = Bukkit.getPlayer(d.uuid());
            if (p == null) continue;
            p.showTitle(Title.title(
                    Component.text("БЕГУНЫ ПОБЕДИЛИ!").color(NamedTextColor.GREEN),
                    Component.text("Время вышло").color(NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofSeconds(1))
            ));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        }
        broadcastParticipants(Component.text("§a§l⚔ БЕГУНЫ ПОБЕДИЛИ! ⚔"));
    }

    private void endTimeUp() {
        if (state == GameState.ENDING) return;
        setState(GameState.ENDING);
        ticksLeft = plugin.cfg().endTime() * 20;
        for (PlayerData d : players.values()) {
            Player p = Bukkit.getPlayer(d.uuid());
            if (p == null) continue;
            p.showTitle(Title.title(
                    Component.text("ВРЕМЯ ВЫШЛО").color(NamedTextColor.YELLOW),
                    Component.text("Соло-сессия завершена").color(NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofSeconds(1))
            ));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
    }

    private void backToLobby() {

        stopTheme();
        stopCountdownTheme();
        for (PlayerData d : players.values()) {
            Player p = Bukkit.getPlayer(d.uuid());
            if (p == null) continue;
            prepPlayer(p);
            p.teleport(plugin.cfg().lobby());
        }
        cleanup();
    }

    public void stop() {
        if (state == GameState.IDLE) return;
        for (PlayerData d : players.values()) {
            Player p = Bukkit.getPlayer(d.uuid());
            if (p == null) continue;
            prepPlayer(p);
            p.teleport(plugin.cfg().lobby());
        }
        cleanup();
        Bukkit.broadcast(Component.text("§6[Салочки] §cИгра остановлена."));
    }

    private void cleanup() {
        if (task != null) { task.cancel(); task = null; }

        if (!cageBlocks.isEmpty()) removeAllCages();
        cageTicksLeft = 0;
        soloMode = false;

        stopTheme();
        stopCountdownTheme();
        players.clear();
        setState(GameState.IDLE);
    }

    private void setState(GameState s) { this.state = s; }

    public void broadcastParticipants(Component c) {
        for (PlayerData d : players.values()) {
            Player p = Bukkit.getPlayer(d.uuid());
            if (p != null) p.sendMessage(c);
        }
    }

    public boolean isParticipant(Player p) {
        return players.containsKey(p.getUniqueId());
    }

    public void removePlayer(UUID uuid) {
        players.remove(uuid);
    }
}
