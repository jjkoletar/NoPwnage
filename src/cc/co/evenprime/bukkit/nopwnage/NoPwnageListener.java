package cc.co.evenprime.bukkit.nopwnage;

import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class NoPwnageListener implements Listener {

    private final NoPwnage plugin;

    private String lastBanCausingMessage;
    private long lastBanCausingMessageTime;
    private String lastGlobalMessage;
    private long lastGlobalMessageTime;
    private int globalRepeated;

    private final Random random = new Random();

    public NoPwnageListener(NoPwnage instance) {
        this.plugin = instance;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void command(PlayerCommandPreprocessEvent event) {
        test(event, true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void chat(PlayerChatEvent event) {
        test(event, false);
    }

    private void test(PlayerChatEvent event, boolean isCommand) {
        Player player = event.getPlayer();
        NoPwnageConfiguration config = plugin.getNPconfig();

        if(!plugin.enabled || player.hasPermission(Permissions.SPAM) || player.hasPermission(Permissions.ADMIN))
            return;

        PlayerData data = plugin.getData(player);

        // Player is supposed to fill out a captcha
        if(config.captcha && data.captchaDone) {
            return;
        }

        if(config.captcha && data.captchaStarted) {
            // Correct answer?
            if(event.getMessage().equals(data.captchaAnswer)) {
                data.captchaDone = true;
            } else {
                player.sendMessage(data.captchaQuestion);

                if(data.captchaTries > config.tries) {
                    if(player.isOnline()) {
                        runCommands(player, "Failed captcha");
                        plugin.log(player.getName() + " from " + player.getAddress().toString().substring(1) + " failed the captcha.");
                    }
                }

                data.captchaTries++;
            }
            event.setCancelled(true);
            return;
        }

        long now = System.currentTimeMillis();
        Location location = player.getLocation();

        double suspicion = 0;

        StringBuilder reasons = new StringBuilder();

        if(data.location == null) {
            data.setLocation(location);
        } else if(!data.compareLocation(location)) {
            data.setLocation(location);
            data.lastMovedTime = now;
        }

        String message = event.getMessage();

        if(!isCommand && config.banned && now - lastBanCausingMessageTime < config.bannedTimeout && similar(message, lastBanCausingMessage)) {
            suspicion += config.bannedWeight;
            addReason(reasons, "banned message", config.bannedWeight);
        }

        if(config.first && now - data.joinTime <= config.firstTimeout) {
            suspicion += config.firstWeight;
            addReason(reasons, "first message", config.firstWeight);
        }

        if(!isCommand && config.global && now - lastGlobalMessageTime < config.globalTimeout && similar(message, lastGlobalMessage)) {
            int added = ((globalRepeated + 2) * config.globalWeight) / 2;
            globalRepeated++;
            suspicion += added;
            addReason(reasons, "global message repeat (" + globalRepeated + ")", added);
        } else {
            globalRepeated = 0;
        }

        if(config.speed && now - data.lastMessageTime <= config.speedTimeout) {
            int added = ((data.speedRepeated + 2) * config.speedWeight) / 2;
            data.speedRepeated++;
            if(isCommand)
                added /= 4;
            suspicion += added;
            addReason(reasons, "message speed (" + data.speedRepeated + ")", added);
        } else {
            data.speedRepeated = 0;
        }

        if(!isCommand && config.repeat && now - data.lastMessageTime <= config.repeatTimeout && similar(message, data.lastMessage)) {

            int added = ((data.messageRepeated + 2) * config.repeatWeight) / 2;
            data.messageRepeated++;
            suspicion += added;
            addReason(reasons, "player message repeat (" + data.messageRepeated + ")", added);
        } else {
            data.messageRepeated = 0;
        }

        boolean warned = false;
        if(config.warnPlayers && now - data.lastWarningTime <= config.warnTimeout) {
            suspicion += 100;
            addReason(reasons, "warned", 100);
            warned = true;
        }

        if(config.move && now - data.lastMovedTime <= config.moveTimeout) {
            suspicion -= config.moveWeightBonus;
            addReason(reasons, "moved", -config.moveWeightBonus);
        } else {
            suspicion += config.moveWeightMalus;
            addReason(reasons, "didn't move", config.moveWeightMalus);

        }

        //plugin.log("Suspicion: " + reasons + ": " + suspicion);
        if(config.warnPlayers && suspicion >= config.warnLevel && !warned) {
            data.lastWarningTime = now;
            warnPlayer(player);
        } else if(suspicion >= config.banLevel) {
            if(config.captcha && !data.captchaStarted) {
                data.captchaStarted = true;
                String captcha = generateCaptcha();
                data.captchaAnswer = captcha;
                data.captchaQuestion = config.question.replace("[captcha]", captcha);
                event.setCancelled(true);
                event.getPlayer().sendMessage(data.captchaQuestion);
            } else {
                lastBanCausingMessage = message;
                lastBanCausingMessageTime = now;
                data.lastWarningTime = now;
                if(config.warnOthers) {
                    warnOthers(player);
                }
                runCommands(player, "Spambotlike behaviour");
                plugin.log(player.getName() + " banned for " + reasons + ": " + suspicion);
                event.setCancelled(true);
                return;
            }
        }

        data.lastMessage = message;
        data.lastMessageTime = now;

        lastGlobalMessage = message;
        lastGlobalMessageTime = now;
    }

    private String generateCaptcha() {
        NoPwnageConfiguration config = plugin.getNPconfig();

        StringBuilder b = new StringBuilder();

        for(int i = 0; i < config.length; i++) {
            b.append(config.characters.charAt(random.nextInt(config.characters.length())));
        }

        return b.toString();
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {

        Player player = event.getPlayer();
        PlayerData data = plugin.getData(player);
        long now = System.currentTimeMillis();
        NoPwnageConfiguration config = plugin.getNPconfig();

        if(plugin.enabled && config.relog && now - data.leaveTime <= config.relogTime && !player.hasPermission(Permissions.LOGIN)) {
            if(now - data.lastRelogWarningTime >= config.relogTimeout) {
                data.relogWarnings = 0;
            }

            if(data.relogWarnings < config.relogWarnings) {
                player.sendMessage("[NoPwnage]: " + ChatColor.DARK_RED + "You relogged really fast! If you keep doing that, you're going to be banned.");
                data.lastRelogWarningTime = now;
                data.relogWarnings++;
            } else if(now - data.lastRelogWarningTime < config.relogTimeout) {
                runCommands(player, "Relogged too fast!");
                plugin.log(player.getName() + " from " + player.getAddress().toString().substring(1) + " handled for relogging too fast.");
            }

        }

        Location l = player.getLocation();
        data.setLocation(l);
        data.joinTime = now;
    }

    /*@EventHandler
    public void leave(PlayerQuitEvent event) {

        Player player = event.getPlayer();
        PlayerData data = plugin.getData(player);

        long now = System.currentTimeMillis();

        if(plugin.enabled) {
            if(now - data.joinTime <= 300) {
                runCommands(player, "");
                plugin.log(player.getName() + " Disconnected in under 300 milliseconds; he's now banned.");
            }
        }

        data.leaveTime = now;

    }*/

    private void addReason(StringBuilder builder, String reason, int value) {
        if(builder.length() > 0) {
            builder.append(", ");
        }
        builder.append(reason).append(" ").append(value);
    }

    private void warnOthers(Player player) {
        plugin.getServer().broadcastMessage(ChatColor.YELLOW + player.getName() + ChatColor.DARK_RED + " has set off the autoban!");
        plugin.getServer().broadcastMessage(ChatColor.DARK_RED + " Please do not say anything similar to what the user said!");
    }

    private void warnPlayer(Player player) {
        player.sendMessage("[NoPwnage]: " + ChatColor.DARK_RED + "Our system has detected unusual bot activities coming from you.");
        player.sendMessage(ChatColor.DARK_RED + "Please be careful with what you say. DON'T repeat what you just said either, unless you want to be banned.");
    }

    private void runCommands(Player player, String reason) {
        NoPwnageConfiguration config = plugin.getNPconfig();
        String name = player.getName();
        String ip = player.getAddress().toString().substring(1).split(":")[0];

        if(player.isOnline()) {
            for(String command : config.commands) {
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("[player]", name).replace("[ip]", ip).replace("[reason]", reason));
                } catch(Exception e) {

                }
            }
        }
    }

    private boolean similar(String message1, String message2) {
        return message1 != null && message2 != null && (stringDifference(message1, message2) < 1 + (message1.length() / 10));
    }

    private int minimum(int a, int b, int c) {
        int mi;

        mi = a;
        if(b < mi) {
            mi = b;
        }
        if(c < mi) {
            mi = c;
        }
        return mi;
    }

    private int stringDifference(String s, String t) {
        int d[][];
        int n;
        int m;
        int i;
        int j;
        char s_i;
        char t_j;
        int cost;

        n = s.length();
        m = t.length();
        if(n == 0) {
            return m;
        }
        if(m == 0) {
            return n;
        }
        d = new int[n + 1][m + 1];
        for(i = 0; i <= n; i++) {
            d[i][0] = i;
        }

        for(j = 0; j <= m; j++) {
            d[0][j] = j;
        }
        for(i = 1; i <= n; i++) {

            s_i = s.charAt(i - 1);

            for(j = 1; j <= m; j++) {

                t_j = t.charAt(j - 1);

                if(s_i == t_j) {
                    cost = 0;
                } else {
                    cost = 1;
                }

                d[i][j] = minimum(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost);

            }

        }
        return d[n][m];

    }
}
