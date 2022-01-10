package games.chocola.minazukii.uhc;

import org.apache.commons.io.FileUtils;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import tech.yfshadaow.PlayerEndGameEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import static org.bukkit.ChatColor.*;
import static tech.yfshadaow.GameUtils.world;

public class UHCGame extends tech.yfshadaow.Game implements Listener, CommandExecutor {
    private static final UHCGame instance = new UHCGame((UHC) Bukkit.getPluginManager().getPlugin("UHC"));
    private final Scoreboard scoreboard;
    private final ArrayList<Player> alive = new ArrayList<>();
    private final HashMap<NamespacedKey, Recipe> customRecipes = new HashMap<>();
    private final String PLAYERS_REMAIN_SUFFIX = "  " + AQUA + "剩余人数：" + GREEN;
    private final String GAME_TIME_PREFIX = "  " + GRAY + "游戏时间：" + GREEN;
    private final String GRACE_PERIOD_PREFIX = "  " + LIGHT_PURPLE + "和平时间剩余：" + GREEN;
    private final String DEATH_MATCH_PREFIX = "  " + LIGHT_PURPLE + "最终决战倒计时：" + GREEN;
    private final String FINAL_HEAL_PREFIX = "  " + YELLOW + "补血倒计时：" + GREEN;
    private final String BORDER_SHRINK_PREFIX = "  " + YELLOW + "边界缩小倒计时：" + GREEN;
    private final String BORDER_PREFIX = "  " + RED + "边界：" + GREEN;
    private int gameTime;
    private int countdown1;
    private int countdown2;
    private long borderSize;
    private World uhcWorld;
    private Location worldSpawn;

    private UHCGame(UHC plugin) {
        this.plugin = plugin;
        players = UHC.players;
        this.scoreboard = Objects.requireNonNull(Bukkit.getScoreboardManager()).getNewScoreboard();
        initGame(plugin, "UHC", 5, new Location(world, 10000, 41, 10002), BlockFace.NORTH,
                new Location(world, 9998, 41, 10002), BlockFace.EAST,
                new Location(world, 10000, 40, 10000), new BoundingBox(-1044, 45, -25, -983, 70, 27));
        initCustomRecipe();
        registerScoreboard();
    }

    public static UHCGame getInstance() {
        return instance;
    }

    @Override
    public void initGameRunnable() {
        /*
         Basic Logic:
         1. Initialization
         1) Add hub players to players list (Get from super method {@link tech.yfshadaow.Game#getStartingPlayers})
         2) Remove start button (Super method {@link tech.yfshadaow.Game#removeStartButton})
         3) Starting countdown (Super method {@link tech.yfshadaow.Game#startCountdown})
         4) Init randomizer
         5) Init UHC world (Using {@link #generateRandomWorld})
         6) Spread players (Using {@link #spreadPlayers})
         Make the center (0,0), spreading 1000 blocks far
         7) Init scoreboard (Using {@link #initScoreboard})
         I. UHC (title)
         II. >---------------< [9]
         III. Players remain: 10 [8]
         IV. (empty line) [7]
         V. Game Time: 0s [6]
         VI. Grace Period remain: 300s -> Death Match in: 120s -> Death Match! [5]
         VII. Final Heal in: 120s -> Border Shrink in: 120s -> Go to (0,0)! [4]
         VIII. (empty line) [3]
         IX. Border: 1000 --(gradually in 180s)--> 64 [2]
         X. >---------------< [1]
         8) Countdowns (Using {@link #updateScoreboard})
         9) Check if end (Using {@code @EventHandler{@link #onPlayerDies}} and time limit)
         */
        gameRunnable = () -> {
            this.players.addAll(getStartingPlayers());
            alive.addAll(players);
            removeStartButton();
            generateRandomWorld();
            startCountdown();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                spreadPlayers(players);
                initScoreboard(Objects.requireNonNull(scoreboard.getObjective("uhcMain")));
                for (Player p : players) {
                    p.setScoreboard(scoreboard);
                }
            }, 100);
            taskIds.add(Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                for (Player p : alive) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.HEAL, 1, 5));
                }
            }, 2400));
            taskIds.add(Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> uhcWorld.setPVP(true), 6000));
            taskIds.add(Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> updateScoreboard(Objects.requireNonNull(scoreboard.getObjective("uhcMain"))), 120, 20));
            taskIds.add(Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                if (uhcWorld.getWorldBorder().getSize() > 64) {
                    uhcWorld.getWorldBorder().setSize(uhcWorld.getWorldBorder().getSize() - 0.26);
                    scoreboard.resetScores(BORDER_PREFIX + borderSize);
                    borderSize = Math.round(uhcWorld.getWorldBorder().getSize());
                    Objects.requireNonNull(scoreboard.getObjective("uhcMain")).getScore(BORDER_PREFIX + borderSize).setScore(2);
                }
            }, 4920, 1));
            taskIds.add(Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> players.forEach((p) -> p.sendMessage(" " + YELLOW + "5分钟后将强制结束游戏！届时所有存活玩家都会成为胜利者！")), 18000));
            taskIds.add(Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, this::endGame, 24000));
        };
    }

    private void initScoreboard(Objective o) {
        o.setDisplayName(BOLD.toString() + GOLD + BOLD + "UHC"); //UHC
        o.getScore(DARK_GRAY + ">---------------<").setScore(9); //>---------------<
        o.getScore(PLAYERS_REMAIN_SUFFIX + alive.size()).setScore(8); //>>Players remain:
        o.getScore(" ").setScore(7); //
        gameTime = 0;
        o.getScore(GAME_TIME_PREFIX + gameTime).setScore(6); //Game Time:
        countdown1 = 300;
        o.getScore(GRACE_PERIOD_PREFIX + countdown1).setScore(5); //Grace Period remain:
        countdown2 = 120;
        o.getScore(FINAL_HEAL_PREFIX + countdown2).setScore(4);
        o.getScore("  ").setScore(3);
        borderSize = 1000;
        o.getScore(BORDER_PREFIX + borderSize).setScore(2);
        o.getScore(DARK_GRAY + ">---------------< ").setScore(1);
    }

    private void updateScoreboard(Objective o) {
        Objects.requireNonNull(o.getScoreboard()).resetScores(GAME_TIME_PREFIX + gameTime);
        gameTime++;
        o.getScore(GAME_TIME_PREFIX + gameTime).setScore(6);
        if (o.getScore(GRACE_PERIOD_PREFIX + countdown1).isScoreSet()) {
            o.getScoreboard().resetScores(GRACE_PERIOD_PREFIX + countdown1);
            if (--countdown1 == 0) {
                countdown1 = 120;
                o.getScore(DEATH_MATCH_PREFIX + countdown1).setScore(5);
            } else {
                o.getScore(GRACE_PERIOD_PREFIX + countdown1).setScore(5);
            }
        } else if (o.getScore(DEATH_MATCH_PREFIX + countdown1).isScoreSet()) {
            o.getScoreboard().resetScores(DEATH_MATCH_PREFIX + countdown1);
            if (--countdown1 == 0) {
                o.getScore("  " + LIGHT_PURPLE + "最终决战！").setScore(5);
            } else {
                o.getScore(DEATH_MATCH_PREFIX + countdown1).setScore(5);
            }
        }
        if (o.getScore(FINAL_HEAL_PREFIX + countdown2).isScoreSet()) {
            o.getScoreboard().resetScores(FINAL_HEAL_PREFIX + countdown2);
            if (--countdown2 == 0) {
                countdown2 = 120;
                o.getScore(BORDER_SHRINK_PREFIX + countdown2).setScore(4);
            } else {
                o.getScore(FINAL_HEAL_PREFIX + countdown2).setScore(4);
            }
        } else if (o.getScore(BORDER_SHRINK_PREFIX + countdown2).isScoreSet()) {
            o.getScoreboard().resetScores(BORDER_SHRINK_PREFIX + countdown2);
            if (--countdown2 == 0) {
                o.getScore("  " + YELLOW + "快去中心点！").setScore(4);
            } else {
                o.getScore(BORDER_SHRINK_PREFIX + countdown2).setScore(4);
            }
        }
    }

    @EventHandler
    public void onPlayerDies(PlayerDeathEvent pde) {
        Player death = pde.getEntity();
        if (alive.contains(death)) {
            death.setGameMode(GameMode.SPECTATOR);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            try {
                SkullMeta headMeta = (SkullMeta) head.getItemMeta();
                Objects.requireNonNull(headMeta).setOwningPlayer(death);
                head.setItemMeta(headMeta);
            } catch (ClassCastException cce) {
                cce.printStackTrace();
            }
            uhcWorld.dropItem(pde.getEntity().getLocation(), head);
            for (Player p : players) {
                p.sendMessage(death.getName() + "被" + (death.getKiller() == null ? "" : death.getKiller().getName()) + "淘汰了！");
            }
            scoreboard.resetScores(PLAYERS_REMAIN_SUFFIX + alive.size());
            alive.remove(death);
            Objects.requireNonNull(scoreboard.getObjective("uhcMain")).getScore(PLAYERS_REMAIN_SUFFIX + alive.size()).setScore(8);
            if (alive.size() <= 1) {
                endGame();
            }
        }
    }

    @EventHandler
    public void onPlayerSetSpawn(PlayerBedEnterEvent pbee) {
        if (pbee.getPlayer().getWorld().getName().equals("uhc")) {
            pbee.getPlayer().sendMessage(DARK_RED + "NOPE!");
            pbee.getPlayer().playSound(pbee.getPlayer().getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            pbee.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent bbe) {
        if (Objects.requireNonNull(bbe.getBlock().getLocation().getWorld()).getName().equals("uhc")) {
            switch (bbe.getBlock().getType()) {
                case IRON_ORE:
                case DEEPSLATE_IRON_ORE:
                    bbe.setDropItems(false);
                    uhcWorld.dropItem(bbe.getBlock().getLocation(), new ItemStack(Material.IRON_INGOT));
                    bbe.setExpToDrop(3);
                    break;
                case GOLD_ORE:
                case DEEPSLATE_GOLD_ORE:
                    bbe.setDropItems(false);
                    uhcWorld.dropItem(bbe.getBlock().getLocation(), new ItemStack(Material.GOLD_INGOT));
                    bbe.setExpToDrop(3);
                    break;
                case OAK_LEAVES:
                case DARK_OAK_LEAVES:
                    if (!bbe.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.SHEARS)) {
                        if (random.nextInt(100) < 3) {
                            uhcWorld.dropItem(bbe.getBlock().getLocation(), new ItemStack(Material.APPLE));
                        }
                    }
                    if (random.nextInt(100) < 5) {
                        uhcWorld.dropItem(bbe.getBlock().getLocation(), new ItemStack(Material.APPLE));
                    }
                    break;
                case BIRCH_LEAVES:
                case JUNGLE_LEAVES:
                case SPRUCE_LEAVES:
                case ACACIA_LEAVES:
                case AZALEA_LEAVES:
                case FLOWERING_AZALEA_LEAVES:
                    if (random.nextInt(100) < 3) {
                        uhcWorld.dropItem(bbe.getBlock().getLocation(), new ItemStack(Material.APPLE));
                    }
                    break;
            }
        }
    }

    @EventHandler
    public void onEntitySpawn(CreatureSpawnEvent cse) {
        if (Objects.requireNonNull(cse.getEntity().getLocation().getWorld()).getName().equals("uhc") && cse.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.NATURAL)) {
            if (random.nextInt(100) < 25) {
                cse.setCancelled(true);
            }
        }
    }

    private void spreadPlayers(List<Player> players) {
        for (Player p : players) {
            p.setGameMode(GameMode.SURVIVAL);
            p.setBedSpawnLocation(worldSpawn, true);
            p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 200, 30));
            p.teleport(new Location(uhcWorld, random.nextInt(1000) - 500, 100, random.nextInt(1000) - 500));
        }
    }

    private void generateRandomWorld() {
        uhcWorld = Objects.requireNonNull(new WorldCreator("uhc").generator("Terra:DEFAULT").biomeProvider(new CustomBiomeProvider()).environment(World.Environment.NORMAL).generateStructures(false).createWorld());
        worldSpawn = new Location(uhcWorld, 0, 100, 0);
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

    private void initCustomRecipe() {
        ItemStack temp;
        NamespacedKey key;
        Recipe recipe;
        //wooden axe
        temp = new ItemStack(Material.WOODEN_AXE);
        temp.addEnchantment(Enchantment.DIG_SPEED, 1);
        temp.addEnchantment(Enchantment.DURABILITY, 1);
        key = new NamespacedKey(plugin, "wooden_axe");
        recipe = new ShapedRecipe(key, temp).
                shape("xx", "xy", " y").
                setIngredient('x', new RecipeChoice.MaterialChoice(Tag.PLANKS)).
                setIngredient('y', Material.STICK);
        customRecipes.put(key, recipe);
        if (Bukkit.getRecipe(key) == null) {
            Bukkit.addRecipe(recipe);
        }
        //wooden pickaxe
        temp = new ItemStack(Material.WOODEN_PICKAXE);
        temp.addEnchantment(Enchantment.DIG_SPEED, 1);
        temp.addEnchantment(Enchantment.DURABILITY, 1);
        key = new NamespacedKey(plugin, "wooden_pickaxe");
        recipe = new ShapedRecipe(key, temp).
                shape("xxx", " y ", " y ").
                setIngredient('x', new RecipeChoice.MaterialChoice(Tag.PLANKS)).
                setIngredient('y', Material.STICK);
        customRecipes.put(key, recipe);
        if (Bukkit.getRecipe(key) == null) {
            Bukkit.addRecipe(recipe);
        }
        //wooden shovel
        temp = new ItemStack(Material.WOODEN_SHOVEL);
        temp.addEnchantment(Enchantment.DIG_SPEED, 1);
        temp.addEnchantment(Enchantment.DURABILITY, 1);
        key = new NamespacedKey(plugin, "wooden_shovel");
        recipe = new ShapedRecipe(key, temp).
                shape("x", "y", "y").
                setIngredient('x', new RecipeChoice.MaterialChoice(Tag.PLANKS)).
                setIngredient('y', Material.STICK);
        customRecipes.put(key, recipe);
        if (Bukkit.getRecipe(key) == null) {
            Bukkit.addRecipe(recipe);
        }
        //wooden sword
        temp = new ItemStack(Material.WOODEN_SWORD);
        temp.addEnchantment(Enchantment.DAMAGE_ALL, 1);
        temp.addEnchantment(Enchantment.DURABILITY, 1);
        key = new NamespacedKey(plugin, "wooden_axe");
        recipe = new ShapedRecipe(key, temp).
                shape("x", "x", "y").
                setIngredient('x', new RecipeChoice.MaterialChoice(Tag.PLANKS)).
                setIngredient('y', Material.STICK);
        customRecipes.put(key, recipe);
        if (Bukkit.getRecipe(key) == null) {
            Bukkit.addRecipe(recipe);
        }
        //string
        temp = new ItemStack(Material.STRING);
        key = new NamespacedKey(plugin, "string");
        recipe = new ShapedRecipe(key, temp).
                shape("xx", "xx").
                setIngredient('x', new RecipeChoice.MaterialChoice(Tag.WOOL));
        customRecipes.put(key, recipe);
        if (Bukkit.getRecipe(key) == null) {
            Bukkit.addRecipe(recipe);
        }
    }

    @Override
    protected void savePlayerQuitData(Player p) {

    }

    @Override
    protected void rejoin(Player p) {

    }

    private void endGame() {
        StringBuilder winner = new StringBuilder();
        winner.append(" ").append(GOLD);
        for (Player p : alive) {
            winner.append(p.getName()).append(WHITE);
            if (alive.indexOf(p) != alive.size() - 1) {
                winner.append(", ").append(GOLD);
            }
        }
        winner.append("获得胜利！");
        StringBuilder mvp = new StringBuilder();
        mvp.append(" ").append(AQUA);
        mvp.append(players.get(0).getName()).append(WHITE);
        int maxKill = Objects.requireNonNull(scoreboard.getObjective("uhcKills")).getScore(players.get(0).getName()).getScore();
        for (Player p : players) {
            if (Objects.requireNonNull(scoreboard.getObjective("uhcKills")).getScore(p.getName()).getScore() > maxKill) {
                maxKill = Objects.requireNonNull(scoreboard.getObjective("uhcKills")).getScore(p.getName()).getScore();
                mvp = new StringBuilder();
                mvp.append(" ").append(AQUA);
                mvp.append(p.getName()).append(WHITE);
            } else if (Objects.requireNonNull(scoreboard.getObjective("uhcKills")).getScore(p.getName()).getScore() == maxKill) {
                if (players.indexOf(p) != 0) {
                    mvp.append(", ").append(AQUA).append(p.getName()).append(WHITE);
                }
            }
        }
        mvp.append("以");
        mvp.append(maxKill);
        mvp.append("杀拿下最多人头！");
        for (Player p : players) {
            p.sendMessage(winner.toString());
            p.sendMessage(mvp.toString());
            p.teleport(hubLocation);
            Bukkit.getPluginManager().callEvent(new PlayerEndGameEvent(p, this));
        }
        players.clear();
        alive.clear();
        Bukkit.unloadWorld("uhc", false);
        refreshScoreboard();
        try {
            FileUtils.deleteDirectory(new File("uhc"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        List<Integer> taskIdsCopy = new ArrayList<>(taskIds);
        taskIds.clear();
        for (int i : taskIdsCopy) {
            Bukkit.getScheduler().cancelTask(i);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (command.getName().equals("forceend")) {
            if (!sender.isOp()) {
                return false;
            }
            if (!(sender instanceof Player)) {
                return false;
            }
            if (args.length != 1) {
                return false;
            }
            if (args[0].equals("UHC")) {
                endGame();
                return true;
            }
        }
        return false;
    }

    private void refreshScoreboard() {
        unregisterScoreboard();
        registerScoreboard();
    }


    private void registerScoreboard() {
        scoreboard.registerNewObjective("uhcMain", "dummy", "UHC").setDisplaySlot(DisplaySlot.SIDEBAR);
        scoreboard.registerNewObjective("uhcKills", "playerKillCount", "UHC Kills").setDisplaySlot(DisplaySlot.PLAYER_LIST);
    }

    private void unregisterScoreboard() {
        for (Objective o : scoreboard.getObjectives()) {
            o.unregister();
        }
    }

    public void onDisable() {
        unregisterScoreboard();
        unloadRecipes();
    }

    private void unloadRecipes() {
        for (NamespacedKey key : customRecipes.keySet()) {
            Bukkit.removeRecipe(key);
        }
    }
}
