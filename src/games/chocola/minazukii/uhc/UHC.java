package games.chocola.minazukii.uhc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import tech.yfshadaow.PlayerChangeGameEvent;

import static tech.yfshadaow.GameUtils.*;

import java.io.File;
import java.util.ArrayList;

public class UHC extends JavaPlugin implements Listener {
    static ArrayList<Player> players;

    @EventHandler
    public void onButtonClicked(PlayerInteractEvent pie) {
        if (pie.getAction().equals(Action.RIGHT_CLICK_BLOCK) &&
                pie.getClickedBlock().getType().equals(Material.OAK_BUTTON) &&
                pie.getClickedBlock().getLocation().equals(new Location(world, 10000, 41, 10002))) {
            UHCGame.getInstance().startGame();
        }
    }

    @Override
    public void onEnable() {
        players = new ArrayList<>();
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(UHCGame.getInstance(), this);
        registerGame(UHCGame.getInstance());
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        HandlerList.unregisterAll((Plugin)this);
        Bukkit.unloadWorld("uhc",false);
        new File("uhc").delete();
        if (players.size() > 0) {
            for (Player p : players) {
                p.teleport(new Location(world, 0.5,89.0,0.5));
                Bukkit.getPluginManager().callEvent(new PlayerChangeGameEvent(p, UHCGame.getInstance(), null));
            }
        }
        unregisterGame(UHCGame.getInstance());
    }
}
