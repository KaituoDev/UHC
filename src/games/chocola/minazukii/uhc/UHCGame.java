package games.chocola.minazukii.uhc;

import com.onarandombox.MultiverseCore.MVWorld;
import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.BoundingBox;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import static tech.yfshadaow.GameUtils.world;

public class UHCGame extends tech.yfshadaow.Game implements Listener {
    private static UHCGame instance = new UHCGame((UHC) Bukkit.getPluginManager().getPlugin("UHC"));
    Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
    String playersRemainSuffix = "  "+ChatColor.AQUA+"剩余人数：";
    String countdownSuffix = "abc";
    Random random;
    MultiverseCore core = new MultiverseCore();
    MVWorldManager wm = core.getMVWorldManager();

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
        gameRunnable = () -> {
            this.players.addAll(getStartingPlayers());
            removeStartButton();
            startCountdown();
            random = new Random();
            MVWorld uhcWorld = (MVWorld) Generator.generateRandomWorld(wm, random);
            uhcWorld.getCBWorld().getWorldBorder().setSize(1000);
            uhcWorld.getCBWorld().getWorldBorder().setCenter(new Location(uhcWorld.getCBWorld(), 0, 64, 0));
            spreadPlayers(uhcWorld, players);
            scoreboard.getObjective("uhc").setDisplayName(ChatColor.BOLD.toString()+ChatColor.GOLD+ChatColor.BOLD+"UHC");
            scoreboard.getObjective("uhc").getScore(" ").setScore(10);
            scoreboard.getObjective("uhc").getScore(playersRemainSuffix+players.size()).setScore(9);
            scoreboard.getObjective("uhc").getScore(countdownSuffix+"60");
            for (Player p: players) {
                p.setScoreboard(scoreboard);
            }
        };
    }

    @EventHandler
    public void onPlayerDies(PlayerDeathEvent pde) {
        if (pde.getEntity() instanceof Player) {
            Player death = (Player) pde.getEntity();
            if (players.contains(death)) {
                for (Player p: players) {
                    p.sendMessage(death.getName()+"被"+death.getKiller().getName()+"淘汰了！");
                }
                players.remove(death);
            }
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent pre) {
        if (pre.getPlayer().getWorld().getName().equals("uhc")) {
            pre.getPlayer().setGameMode(GameMode.SPECTATOR);
        }
    }

    private void spreadPlayers(MVWorld world, List<Player> players) {
        for (Player p: players) {
            p.teleport(core.getSafeTTeleporter().getSafeLocation(new Location(world.getCBWorld(), random.nextInt(1000)-500,64,random.nextInt(1000)-500)));
        }
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
