package cc.co.evenprime.bukkit.nopwnage;

import org.bukkit.Location;

public class PlayerData {

    public Loc location;
    public String lastMessage;
    public long lastMessageTime;
    public long joinTime;
    public long leaveTime;
    public long lastWarningTime;
    public int messageRepeated;
    public long lastRelogWarningTime;
    public long lastMovedTime;
    public int relogWarnings;
    public int speedRepeated;
    public String captchaAnswer = "";
    public String captchaQuestion = "";
    public boolean captchaDone = false;
    public boolean captchaStarted = false;
    public int captchaTries;

    private static class Loc {

        private final int x;
        private final int y;
        private final int z;

        private Loc(Location l) {
            this.x = l.getBlockX();
            this.y = l.getBlockY();
            this.z = l.getBlockZ();
        }
    }

    public void setLocation(Location l) {
        location = new Loc(l);
    }

    public boolean compareLocation(Location l) {
        return location != null && location.x == l.getBlockX() && location.y == l.getBlockY() && location.z == l.getBlockZ();
    }
}
