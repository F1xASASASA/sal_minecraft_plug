package net.cubo.tag.game;

import java.util.UUID;

public class PlayerData {
    private final UUID uuid;
    private Role role = Role.NONE;
    private boolean wantsHunter = false;
    private boolean fallingHunter = false;
    private int respawnTicks = 0;
    private long lastVoteClickMs = 0;

    public PlayerData(UUID uuid) { this.uuid = uuid; }

    public UUID uuid() { return uuid; }
    public Role role() { return role; }
    public void setRole(Role r) { this.role = r; }
    public boolean wantsHunter() { return wantsHunter; }
    public void setWantsHunter(boolean v) { this.wantsHunter = v; }
    public boolean fallingHunter() { return fallingHunter; }
    public void setFallingHunter(boolean v) { this.fallingHunter = v; }
    public int respawnTicks() { return respawnTicks; }
    public void setRespawnTicks(int t) { this.respawnTicks = t; }
    public long lastVoteClickMs() { return lastVoteClickMs; }
    public void setLastVoteClickMs(long t) { this.lastVoteClickMs = t; }
}
