package games.chocola.minazukii.uhc;

import com.onarandombox.MultiverseCore.MultiverseCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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

import tech.yfshadaow.GameUtils;
import tech.yfshadaow.PlayerChangeGameEvent;

import static tech.yfshadaow.GameUtils.*;

import java.util.ArrayList;

public class UHC extends JavaPlugin implements Listener {
    static ArrayList<Player> players;

    @EventHandler
    public void onButtonClicked(PlayerInteractEvent pie) {
        if (pie.getAction().equals(Action.RIGHT_CLICK_BLOCK) &&
                pie.getClickedBlock().getType().equals(Material.POLISHED_BLACKSTONE_BUTTON) &&
                pie.getClickedBlock().getLocation().equals(new Location(world, 3002, 50, 3000))) {
            UHCGame.getInstance().startGame();
        }
    }

    @Override
    public void onEnable() {
        MultiverseCore core = new MultiverseCore();
        Bukkit.getPluginManager().registerEvents(this, this);
        players = new ArrayList<>();
        core.getMVWorldManager().getMVWorld(world).setGameMode(GameMode.ADVENTURE);
        registerGame(UHCGame.getInstance());
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        HandlerList.unregisterAll((Plugin)this);
        if (players.size() > 0) {
            for (Player p : players) {
                p.teleport(new Location(world, 0.5,89.0,0.5));
                Bukkit.getPluginManager().callEvent(new PlayerChangeGameEvent(p, UHCGame.getInstance(), null));
            }
        }
        unregisterGame(UHCGame.getInstance());
    }
}
