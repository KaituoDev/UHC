package games.chocola.minazukii.uhc;

import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.BoundingBox;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import static org.bukkit.ChatColor.*;
import static tech.yfshadaow.GameUtils.world;

public class UHCGame extends tech.yfshadaow.Game implements Listener {
    private static UHCGame instance = new UHCGame((UHC) Bukkit.getPluginManager().getPlugin("UHC"));
    Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
    String playersRemainSuffix = "  "+AQUA+"剩余人数："+GREEN;
    String gameTimeSuffix = "  "+GRAY+"游戏时间："+GREEN;
    int gameTime;
    String countdownSuffix1;
    int countdown1;
    String countdownSuffix2;
    int countdown2;
    String borderSuffix = "  "+RED+"边界："+GREEN;
    int borderSize;
    World uhcWorld;
    Location safeSpawn;

    private UHCGame(UHC plugin) {
        this.plugin = plugin;
        initGame(plugin, "UHC", 5, new Location(world, 10000, 41, 10002), BlockFace.NORTH,
                new Location(world, 9998, 41, 10002), BlockFace.EAST,
                new Location(world, 10000, 40, 10000), new BoundingBox(-1044, 45, -25, -983, 70, 27));
        players = UHC.players;
        scoreboard.registerNewObjective("uhc","dummy","UHC").setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    @Override
    public void initGameRunnable() {
        /***
        * Basic Logic:
        * 1. Initialization
        *    1) Add hub players to players list (Get from super method {@link tech.yfshadaow.Game#getStartingPlayers})
        *    2) Remove start button (Super method {@link tech.yfshadaow.Game#removeStartButton})
        *    3) Starting countdown (Super method {@link tech.yfshadaow.Game#startCountdown})
        *    4) Init randomizer
        *    5) Init UHC world (Using {@link #generateRandomWorld})
        *    6) Spread players (Using {@link #spreadPlayers})
        *       Make the center (0,0), spreading 1000 blocks far
        *    7) Init scoreboard (Using {@link #initScoreboard})
        *       I. UHC (title)
        *       II. >---------------<
        *       III. Players remain: 10
        *       IV. (empty line)
        *       V. Game Time: 0s
        *       VI. Grace Period remain: 300s -> Death Match in: 120s -> Death Match!
        *       VII. Final Heal in: 120s -> Border Shrink in: 120s -> Go to (0,0)! -> Live or die!
        *       VIII. (empty line)
        *       IX. Border: 1000 --(gradually in 480s)--> 200 -> 20
        *       X. >---------------<
        *    8) ...
        */
        gameRunnable = () -> {
            this.players.addAll(getStartingPlayers());
            removeStartButton();
            startCountdown();
            spreadPlayers(players);
            initScoreboard(scoreboard.getObjective("uhc"));
            for (Player p : players) {
                p.setScoreboard(scoreboard);
            }
            Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                updateScoreboard(scoreboard.getObjective("uhc"));
            }, 20, 20);
        };
    }

    private void updateScoreboard(Objective o) {

    }

    @EventHandler
    public void onPlayerDies(PlayerDeathEvent pde) {
        if (pde.getEntity() instanceof Player) {
            Player death = (Player) pde.getEntity();
            if (players.contains(death)) {
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                try {
                    SkullMeta headMeta = (SkullMeta) head.getItemMeta();
                    headMeta.setOwningPlayer(death);
                    head.setItemMeta(headMeta);
                } catch (ClassCastException cce) {
                    cce.printStackTrace();
                }
                uhcWorld.dropItem(pde.getEntity().getLocation(), head);
                for (Player p: players) {
                    p.sendMessage(death.getName()+"被"+death.getKiller().getName()+"淘汰了！");
                }
                scoreboard.getObjective("uhc").getScore(playersRemainSuffix+players.size()).setScore(0);
                players.remove(death);
                scoreboard.getObjective("uhc").getScore(playersRemainSuffix+players.size()).setScore(9);
            }
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent pre) {
        if (pre.getPlayer().getWorld().getName().equals("uhc")) {
            pre.getPlayer().setGameMode(GameMode.SPECTATOR);
        }
    }

    @EventHandler
    public void onPlayerSetSpawn(PlayerBedEnterEvent pbee) {
        if (pbee.getPlayer().getWorld().getName().equals("uhc")) {
            pbee.getPlayer().sendMessage(DARK_RED+"NOPE!");
            pbee.getPlayer().playSound(pbee.getPlayer().getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            pbee.setCancelled(true);
        }
    }

    private void spreadPlayers(List<Player> players) {
        for (Player p: players) {
            p.setGameMode(GameMode.SURVIVAL);
            p.setBedSpawnLocation(safeSpawn, true);
            p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 10, 30));
            p.teleport(getSafeSpawn(new Location(uhcWorld, random.nextInt(1000)-500,64,random.nextInt(1000)-500)));
        }
    }

    private void generateRandomWorld() {
        uhcWorld = new WorldCreator("uhc").biomeProvider(new CustomBiomeProvider()).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
        safeSpawn = getSafeSpawn(new Location(uhcWorld, 0, 64, 0));
        uhcWorld.setDifficulty(Difficulty.HARD);
        uhcWorld.setPVP(false);
        uhcWorld.setGameRule(GameRule.NATURAL_REGENERATION, false);
        uhcWorld.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, false);
        uhcWorld.setGameRule(GameRule.KEEP_INVENTORY, false);
        uhcWorld.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
        uhcWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
        uhcWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        uhcWorld.getWorldBorder().setSize(1000);
        uhcWorld.getWorldBorder().setCenter(new Location(uhcWorld, 0, 64, 0));
    }

    private void initScoreboard(Objective o) {
        o.setDisplayName(BOLD.toString()+GOLD+BOLD+"UHC"); //UHC
        o.getScore(DARK_GRAY+">---------------<").setScore(9); //>---------------<
        o.getScore(playersRemainSuffix+players.size()).setScore(8); //>>Players remain:
        o.getScore(" ").setScore(7); //
        gameTime = 0;
        o.getScore(gameTimeSuffix+gameTime).setScore(6); //Game Time:
        countdownSuffix1 = "  "+LIGHT_PURPLE+"和平时间剩余："+GREEN;
        countdown1 = 300;
        o.getScore(countdownSuffix1+countdown1).setScore(5); //Grace Period remain:
        countdownSuffix2 = "  "+YELLOW+"补血倒计时："+GREEN;
        countdown2 = 120;
        o.getScore(countdownSuffix2+countdown2).setScore(4);
        o.getScore("  ").setScore(3);
        borderSize = 1000;
        o.getScore(borderSuffix+borderSize).setScore(2);
        o.getScore(DARK_GRAY+">---------------< ").setScore(1);
    }

    private Location getSafeSpawn(Location l) {
        Location res = l;
        while (!(res.getBlock().isPassable() && new Location(uhcWorld, res.getX(), res.getY()-1, res.getZ()).getBlock().getType().equals(Material.GRASS_BLOCK))) {
            if (new Location(uhcWorld, res.getX(), res.getY()-1, res.getZ()).getBlock().isLiquid()) {
                res = new Location(uhcWorld, res.getX()-1, 64, res.getZ());
            } else if (new Location(uhcWorld, res.getX(), res.getY()-1, res.getZ()).getBlock().isPassable()) {
                res = new Location(uhcWorld, res.getX(), res.getY()-1, res.getZ());
            } else if (res.getY() < 60) {
                res = new Location(uhcWorld, res.getX()+1, 64, res.getZ());
            } else {
                res = new Location(uhcWorld, res.getX(), res.getY()+1, res.getZ());
            }
        }
        return res;
    }

    @Override
    protected void savePlayerQuitData(Player p) throws IOException {

    }

    @Override
    protected void rejoin(Player p) {

    }

    public static UHCGame getInstance() {
        return instance;
    }
}
