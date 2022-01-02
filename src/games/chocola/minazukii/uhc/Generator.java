package games.chocola.minazukii.uhc;

import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import com.onarandombox.MultiverseCore.api.MultiverseWorld;
import org.bukkit.*;

import java.time.LocalDateTime;
import java.util.Random;

public abstract class Generator {
    public static MultiverseWorld generateRandomWorld(MVWorldManager wm, Random random) {
        wm.addWorld("uhc", World.Environment.NORMAL, random.nextLong()+"", WorldType.NORMAL, false, null, true);
        wm.getMVWorld("uhc").setGameMode(GameMode.SURVIVAL);
        wm.getMVWorld("uhc").setDifficulty(Difficulty.HARD);
        wm.getMVWorld("uhc").setRespawnToWorld("uhc");
        wm.getMVWorld("uhc").setBedRespawn(false);
        wm.getMVWorld("uhc").setPVPMode(true);
        wm.getMVWorld("uhc").getCBWorld().setGameRule(GameRule.NATURAL_REGENERATION, false);
        wm.getMVWorld("uhc").getCBWorld().setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        wm.getMVWorld("uhc").getCBWorld().setGameRule(GameRule.KEEP_INVENTORY, false);
        wm.getMVWorld("uhc").getCBWorld().setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
        return wm.getMVWorld("uhc");
    }
}
