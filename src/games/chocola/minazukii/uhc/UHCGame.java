package games.chocola.minazukii.uhc;

import org.apache.commons.io.FileUtils;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Powerable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import tech.yfshadaow.PlayerEndGameEvent;
import tech.yfshadaow.PlayerQuitData;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.bukkit.ChatColor.*;
import static tech.yfshadaow.GameUtils.*;

/**
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
             VI. Grace Period remain: 600s -> Death Match in: 2000s -> Death Match! [5]
             VII. Final Heal in: 300s -> Border Shrink in: 300s -> Go to (0,0)! [4]
             VIII. (empty line) [3]
             IX. Border: 1000 --(gradually in 180s)--> 64 [2]
             X. >---------------< [1]
     2. Countdowns (Using {@link #updateScoreboard})
     3. Check if end (Using {@code @EventHandler{@link #onPlayerDies}} and time limit)
 */

public class UHCGame extends tech.yfshadaow.Game implements Listener, CommandExecutor {
    private static final UHCGame instance = new UHCGame((UHC) Bukkit.getPluginManager().getPlugin("UHC"));
    private final Scoreboard scoreboard;
    private final ArrayList<Player> alive = new ArrayList<>();
    private final ArrayList<Team> teams = new ArrayList<>();
    private final HashMap<NamespacedKey, Recipe> customRecipes = new HashMap<>();
    private final String PLAYERS_REMAIN_SUFFIX = "  " + AQUA + "剩余人数：" + GREEN;
    private final String TEAMS_REMAIN_SUFFIX = "  " + AQUA + "剩余队伍：" + GREEN;
    private final String GAME_TIME_PREFIX = "  " + GRAY + "游戏时间：" + GREEN;
    private final String GRACE_PERIOD_PREFIX = "  " + LIGHT_PURPLE + "和平时间剩余：" + GREEN;
    private final String DEATH_MATCH_PREFIX = "  " + LIGHT_PURPLE + "最终决战倒计时：" + GREEN;
    private final String FINAL_HEAL_PREFIX = "  " + YELLOW + "补血倒计时：" + GREEN;
    private final String BORDER_SHRINK_PREFIX = "  " + YELLOW + "边界缩小倒计时：" + GREEN;
    private final String BORDER_PREFIX = "  " + RED + "边界大小：" + GREEN;
    private final long GRACE_PERIOD = 600; //in seconds
    private final long FINAL_HEAL = 300;
    private final long BORDER_SHRINK_IN = 600;
    private final int SINGLE_PLAYER_MODE = 0;
    private final int TEAM_MODE = 1;
    private long gameTime;
    private long countdown1;
    private long countdown2;
    private long borderSize;
    private World uhcWorld;
    private Location worldSpawn;
    private boolean running = false;
    private int mode;

    private UHCGame(UHC plugin) {
        this.plugin = plugin;
        players = UHC.players;
        this.scoreboard = Objects.requireNonNull(Bukkit.getScoreboardManager()).getNewScoreboard();
        initGame(plugin, "UHC", AQUA+"UHC", 5, new Location(world, 10000, 41, 10002), BlockFace.NORTH,
                new Location(world, 9998, 41, 10002), BlockFace.EAST,
                new Location(world, 10000, 40, 10000), new BoundingBox());
        initCustomRecipe();
        registerScoreboard();
    }

    public static UHCGame getInstance() {
        return instance;
    }

    @Override
    public void initGameRunnable() {
        gameRunnable = () -> {
            this.players.addAll(getStartingPlayers());
            if (players.size() < 2) {
                players.forEach(p -> p.sendMessage(RED+"人数少于2人，无法开始游戏！"));
                players.clear();
                alive.clear();
            } else {
                if (!((Powerable)new Location(world, 10000, 41, 9998).getBlock().getBlockData()).isPowered()) {
                    mode = SINGLE_PLAYER_MODE;
                    mainLogic();
                } else {
                    mode = TEAM_MODE;
                    if (!((Powerable)new Location(world, 9989, 38, 10000).getBlock().getBlockData()).isPowered()) {
                        if (players.size() < 6) {
                            players.forEach((p) -> p.sendMessage(RED+"人数少于6人，无法随机分队！"));
                        } else {
                            for (int i = 0; i < players.size() / 3.0; i++) {
                                teams.add(randomNewTeam());
                            }
                            List<Player> playersClone = new ArrayList<>(players);
                            for (Team t: teams) {
                                for (int i = 0; i < 3; i++) {
                                    Player entry = playersClone.get(random.nextInt(playersClone.size()));
                                    playersClone.remove(entry);
                                    t.addEntry(entry.getName());
                                }
                            }
                            mainLogic();
                        }
                    } else {
                        if (teams.size() < 2) {
                            players.forEach(p -> p.sendMessage(RED + "没有选择超过2个队伍！"));
                        } else {
                            mainLogic();
                        }
                    }
                }

            }
        };
    }

    private void mainLogic() {
        alive.addAll(players);
        removeStartButton();
        generateRandomWorld();
        startCountdown();
        switch (mode) {
            case SINGLE_PLAYER_MODE:
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    initScoreboard(Objects.requireNonNull(scoreboard.getObjective("uhcMain")));
                    for (Player p : players) {
                        spreadPlayers(p);
                        p.setScoreboard(scoreboard);
                    }
                    running = true;
                    gameUUID = UUID.randomUUID();
                }, 100);
            case TEAM_MODE:
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    initScoreboard(Objects.requireNonNull(scoreboard.getObjective("uhcMain")));
                    teams.forEach(this::spreadPlayers);
                    players.forEach(p -> p.setScoreboard(scoreboard));
                    running = true;
                    gameUUID = UUID.randomUUID();
                }, 100);
        }
        taskIds.add(Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            for (Player p : alive) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.HEAL, 1, 5));
            }
        }, FINAL_HEAL*20));
        taskIds.add(Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> uhcWorld.setPVP(true), GRACE_PERIOD*20));
        taskIds.add(Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> updateScoreboard(Objects.requireNonNull(scoreboard.getObjective("uhcMain"))), 120, 20));
        taskIds.add(Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (uhcWorld.getWorldBorder().getSize() > 64) {
                uhcWorld.getWorldBorder().setSize(uhcWorld.getWorldBorder().getSize() - 0.0235);
                scoreboard.resetScores(BORDER_PREFIX + borderSize);
                borderSize = Math.round(uhcWorld.getWorldBorder().getSize());
                Objects.requireNonNull(scoreboard.getObjective("uhcMain")).getScore(BORDER_PREFIX + borderSize).setScore(2);
            }
        }, FINAL_HEAL*20+ BORDER_SHRINK_IN*20, 1));
        taskIds.add(Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> players.forEach((p) -> p.sendMessage(" " + YELLOW + "5分钟后将强制结束游戏！届时所有存活玩家都会成为胜利者！")), 66000));
        taskIds.add(Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, this::endGame, 72000));
    }

    private Team randomNewTeam() {
        int index = random.nextInt(14);
        if (getTeam(index, false) == null) {
            return getTeam(index, true);
        } else {
            return randomNewTeam();
        }
    }
    
    private Team getTeam(int i, boolean create) {
        Team res;
        if (create) {
            switch (i) {
                case 0:
                    if (scoreboard.getTeam("uhcAQUA") != null) {return scoreboard.getTeam("uhcAQUA");}
                    res = scoreboard.registerNewTeam("uhcAQUA");
                    res.setColor(AQUA);
                case 1:
                    if (scoreboard.getTeam("uhcBLUE") != null) {return scoreboard.getTeam("uhcBLUE");}
                    res = scoreboard.registerNewTeam("uhcBLUE");
                    res.setColor(BLUE);
                case 2:
                    if (scoreboard.getTeam("uhcDARK_AQUA") != null) {return scoreboard.getTeam("uhcDARK_AQUA");}
                    res = scoreboard.registerNewTeam("uhcDARK_AQUA");
                    res.setColor(DARK_AQUA);
                case 3:
                    if (scoreboard.getTeam("uhcDARK_BLUE") != null) {return scoreboard.getTeam("uhcDARK_BLUE");}
                    res = scoreboard.registerNewTeam("uhcDARK_BLUE");
                    res.setColor(DARK_BLUE);
                case 4:
                    if (scoreboard.getTeam("uhcDARK_GRAY") != null) {return scoreboard.getTeam("uhcDARK_GRAY");}
                    res = scoreboard.registerNewTeam("uhcDARK_GRAY");
                    res.setColor(DARK_GRAY);
                case 5:
                    if (scoreboard.getTeam("uhcDARK_GREEN") != null) {return scoreboard.getTeam("uhcDARK_GREEN");}
                    res = scoreboard.registerNewTeam("uhcDARK_GREEN");
                    res.setColor(DARK_GREEN);
                case 6:
                    if (scoreboard.getTeam("uhcDARK_PURPLE") != null) {return scoreboard.getTeam("uhcDARK_PURPLE");}
                    res = scoreboard.registerNewTeam("uhcDARK_PURPLE");
                    res.setColor(DARK_PURPLE);
                case 7:
                    if (scoreboard.getTeam("uhcDARK_RED") != null) {return scoreboard.getTeam("uhcDARK_RED");}
                    res = scoreboard.registerNewTeam("uhcDARK_RED");
                    res.setColor(DARK_RED);
                case 8:
                    if (scoreboard.getTeam("uhcGOLD") != null) {return scoreboard.getTeam("uhcGOLD");}
                    res = scoreboard.registerNewTeam("uhcGOLD");
                    res.setColor(GOLD);
                case 9:
                    if (scoreboard.getTeam("uhcGRAY") != null) {return scoreboard.getTeam("uhcGRAY");}
                    res = scoreboard.registerNewTeam("uhcGRAY");
                    res.setColor(GRAY);
                case 10:
                    if (scoreboard.getTeam("uhcGREEN") != null) {return scoreboard.getTeam("uhcGREEN");}
                    res = scoreboard.registerNewTeam("uhcGREEN");
                    res.setColor(GREEN);
                case 11:
                    if (scoreboard.getTeam("uhcLIGHT_PURPLE") != null) {return scoreboard.getTeam("uhcLIGHT_PURPLE");}
                    res = scoreboard.registerNewTeam("uhcLIGHT_PURPLE");
                    res.setColor(LIGHT_PURPLE);
                case 12:
                    if (scoreboard.getTeam("uhcRED") != null) {return scoreboard.getTeam("uhcRED");}
                    res = scoreboard.registerNewTeam("uhcRED");
                    res.setColor(RED);
                case 13:
                    if (scoreboard.getTeam("uhcYELLOW") != null) {return scoreboard.getTeam("uhcYELLOW");}
                    res = scoreboard.registerNewTeam("uhcYELLOW");
                    res.setColor(YELLOW);
                default:
                    if (scoreboard.getTeam("uhcWHITE") != null) {return scoreboard.getTeam("uhcWHITE");}
                    res = scoreboard.registerNewTeam("uhcWHITE");
                    res.setColor(WHITE);
            }
        } else {
            res = switch (i) {
                case 0 -> scoreboard.getTeam("uhcAQUA");
                case 1 -> scoreboard.getTeam("uhcBLUE");
                case 2 -> scoreboard.getTeam("uhcDARK_AQUA");
                case 3 -> scoreboard.getTeam("uhcDARK_BLUE");
                case 4 -> scoreboard.getTeam("uhcDARK_GRAY");
                case 5 -> scoreboard.getTeam("uhcDARK_GREEN");
                case 6 -> scoreboard.getTeam("uhcDARK_PURPLE");
                case 7 -> scoreboard.getTeam("uhcDARK_RED");
                case 8 -> scoreboard.getTeam("uhcGOLD");
                case 9 -> scoreboard.getTeam("uhcGRAY");
                case 10 -> scoreboard.getTeam("uhcGREEN");
                case 11 -> scoreboard.getTeam("uhcLIGHT_PURPLE");
                case 12 -> scoreboard.getTeam("uhcRED");
                case 13 -> scoreboard.getTeam("uhcYELLOW");
                default -> scoreboard.getTeam("uhcWHITE");
            };
        }
        return res;
    }

    private void initScoreboard(Objective o) {
        o.setDisplayName(BOLD.toString() + GOLD + BOLD + "UHC"); //UHC
        o.getScore(DARK_GRAY + ">---------------<").setScore(9); //>---------------<
        switch (mode) {
            case SINGLE_PLAYER_MODE:
                o.getScore(PLAYERS_REMAIN_SUFFIX + alive.size()).setScore(8); //Players remain:
            case TEAM_MODE:
                o.getScore(TEAMS_REMAIN_SUFFIX + alive.size()).setScore(8); //Teams remain:
        }
        o.getScore(" ").setScore(7); //
        gameTime = 0;
        o.getScore(GAME_TIME_PREFIX + gameTime).setScore(6); //Game Time:
        countdown1 = GRACE_PERIOD;
        o.getScore(GRACE_PERIOD_PREFIX + countdown1).setScore(5); //Grace Period remain:
        countdown2 = FINAL_HEAL;
        o.getScore(FINAL_HEAL_PREFIX + countdown2).setScore(4);
        o.getScore("  ").setScore(3);
        borderSize = 1000;
        o.getScore(BORDER_PREFIX + borderSize).setScore(2);
        o.getScore(DARK_GRAY + ">---------------< ").setScore(1);
    }

    private void updateScoreboard(Objective o) {
        Objects.requireNonNull(o.getScoreboard()).resetScores(GAME_TIME_PREFIX + gameTime);
        o.getScore(GAME_TIME_PREFIX + ++gameTime).setScore(6);
        if (o.getScore(GRACE_PERIOD_PREFIX + countdown1).isScoreSet()) {
            o.getScoreboard().resetScores(GRACE_PERIOD_PREFIX + countdown1);
            if (--countdown1 == 0) {
                countdown1 = 1500;
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
                countdown2 = BORDER_SHRINK_IN;
                o.getScore(BORDER_SHRINK_PREFIX + countdown2).setScore(4);
            } else {
                o.getScore(FINAL_HEAL_PREFIX + countdown2).setScore(4);
            }
        } else if (o.getScore(BORDER_SHRINK_PREFIX + countdown2).isScoreSet()) {
            o.getScoreboard().resetScores(BORDER_SHRINK_PREFIX + countdown2);
            if (--countdown2 == 0) {
                o.getScore("  " + YELLOW + "快去中心点x=0,z=0！").setScore(4);
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
            
            if (mode == TEAM_MODE) {
                scoreboard.resetScores(TEAMS_REMAIN_SUFFIX + teams.size());
                alive.remove(death);
                for (Team t: teams) {
                    if (t.hasEntry(death.getName())) {
                        t.removeEntry(death.getName());
                        if (t.getEntries().size() <= 0) {
                            teams.remove(t);
                        }
                    }
                }
                Objects.requireNonNull(scoreboard.getObjective("uhcMain")).getScore(TEAMS_REMAIN_SUFFIX + teams.size()).setScore(8);
                if (teams.size() <= 1) {
                    endGame();
                }
            } else if (mode == SINGLE_PLAYER_MODE) {
                scoreboard.resetScores(PLAYERS_REMAIN_SUFFIX + alive.size());
                alive.remove(death);
                Objects.requireNonNull(scoreboard.getObjective("uhcMain")).getScore(PLAYERS_REMAIN_SUFFIX + alive.size()).setScore(8);
                if (alive.size() <= 1) {
                    endGame();
                }
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
            if (cse.getEntity() instanceof Monster) {
                if (cse.getEntity() instanceof Skeleton) {
                    if (random.nextInt(100) < 90) {
                        cse.setCancelled(true);
                    }
                } else {
                    if (random.nextInt(100) < 50) {
                        cse.setCancelled(true);
                    }
                }
            } else {
                if (random.nextInt(100) < 10) {
                    cse.setCancelled(true);
                }
            }
        }
    }

    private void spreadPlayers(Player p) {
        p.setGameMode(GameMode.SURVIVAL);
        p.setBedSpawnLocation(worldSpawn, true);
        p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 1200, 30));
        p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 1200, 30));
        p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 1200, 30));
        p.teleport(new Location(uhcWorld, random.nextInt(1000) - 500, 100, random.nextInt(1000) - 500));
    }

    private void spreadPlayers(Team t) {
        Player p;
        Location destination = new Location(uhcWorld, random.nextInt(1000) - 500, 100, random.nextInt(1000) - 500);
        for (String s: t.getEntries()) {
            p = Bukkit.getPlayer(s);
            Objects.requireNonNull(p).setGameMode(GameMode.SURVIVAL);
            p.setBedSpawnLocation(worldSpawn, true);
            p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 1200, 30));
            p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 1200, 30));
            p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 1200, 30));
            p.teleport(destination);
        }
    }

    private void generateRandomWorld() {
        uhcWorld = Objects.requireNonNull(new WorldCreator("uhc").generator("Terra:DEFAULT").createWorld());
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
        //golden head
        temp = new ItemStack(Material.GOLDEN_APPLE);
        ItemMeta meta = temp.getItemMeta();
        Objects.requireNonNull(meta).setDisplayName(BOLD.toString()+GOLD+BOLD+"Golden Head");
        temp.setItemMeta(meta);
        key = new NamespacedKey(plugin, "golden_head");
        recipe = new ShapedRecipe(key, temp).
                shape("xxx", "xyx", "xxx").
                setIngredient('x', Material.GOLD_INGOT).
                setIngredient('y', Material.PLAYER_HEAD);
        customRecipes.put(key, recipe);
        if (Bukkit.getRecipe(key) == null) {
            Bukkit.addRecipe(recipe);
        }
    }

    @Override
    public void savePlayerQuitData(Player p) {
        PlayerQuitData quitData = new PlayerQuitData(p, this, gameUUID);
        setPlayerQuitData(p.getUniqueId(), quitData);
        players.remove(p);
        scoreboard.resetScores(PLAYERS_REMAIN_SUFFIX + alive.size());
        alive.remove(p);
        Objects.requireNonNull(scoreboard.getObjective("uhcMain")).getScore(PLAYERS_REMAIN_SUFFIX + alive.size()).setScore(8);
        if (alive.size() <= 1) {
            endGame();
        }
    }

    @Override
    public void rejoin(Player p) {
        if (!running) {
            p.sendMessage("§c游戏已经结束！");
            return;
        }
        if (!getPlayerQuitData(p.getUniqueId()).getGameUUID().equals(gameUUID)) {
            p.sendMessage("§c游戏已经结束！");
            return;
        }
        PlayerQuitData pqd = getPlayerQuitData(p.getUniqueId());
        pqd.restoreBasicData(p);
        players.add(p);
        p.setScoreboard(scoreboard);
        if (p.getGameMode().equals(GameMode.SURVIVAL)) {
            scoreboard.resetScores(PLAYERS_REMAIN_SUFFIX + alive.size());
            alive.add(p);
            Objects.requireNonNull(scoreboard.getObjective("uhcMain")).getScore(PLAYERS_REMAIN_SUFFIX + alive.size()).setScore(8);
        }
        setPlayerQuitData(p.getUniqueId(), null);
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
            p.setBedSpawnLocation(new Location(world, 0, 89, 0), true);
            Bukkit.getPluginManager().callEvent(new PlayerEndGameEvent(p, this));
        }
        unregisterTeams();
        players.clear();
        alive.clear();
        teams.clear();
        Bukkit.unloadWorld("uhc", false);
        refreshScoreboard();
        gameUUID = null;
        placeStartButton();
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
            if (args.length != 1) {
                return false;
            }
            if (args[0].equals("UHC")) {
                endGame();
                return true;
            }
        } else if (command.getName().equals("uhcteam")) {
            if (!(sender instanceof Player)) {
                return false;
            }
            if (!new BoundingBox(9986, 33, 9996, 10006, 45, 10006).contains(((Player) sender).getLocation().toVector())) {
                return false;
            }
            if (!((Powerable)new Location(world, 10000, 41, 9998).getBlock().getBlockData()).isPowered()) {
                sender.sendMessage(RED+"游戏模式为单人模式，无法选择队伍！");
                return false;
            }
            if (!((Powerable)new Location(world, 9989, 38, 10000).getBlock().getBlockData()).isPowered()) {
                sender.sendMessage(RED+"分队模式为随机分队，无法选择队伍！");
                return false;
            }
            if (args.length != 1) {
                sender.sendMessage(RED+"1 argument expected but found "+args.length);
                return false;
            }
            if (args[0].equals("quit")) {
                teams.forEach(t -> t.removeEntry(sender.getName()));
                return true;
            }
            try {
                teams.forEach(t -> t.removeEntry(sender.getName()));
                getTeam(Integer.parseInt(args[0]), true).addEntry(sender.getName());
                StringBuilder sb = new StringBuilder();
                sb.append("你加入了");
                switch (Integer.parseInt(args[0])) {
                    case 0 -> {
                        sb.append(AQUA);
                        sb.append("浅蓝");
                    }
                    case 1 -> {
                        sb.append(BLUE);
                        sb.append("蓝");
                    }
                    case 2 -> {
                        sb.append(DARK_AQUA);
                        sb.append("青");
                    }
                    case 3 -> {
                        sb.append(DARK_BLUE);
                        sb.append("深蓝");
                    }
                    case 4 -> {
                        sb.append(DARK_GRAY);
                        sb.append("深灰");
                    }
                    case 5 -> {
                        sb.append(DARK_GREEN);
                        sb.append("深绿");
                    }
                    case 6 -> {
                        sb.append(DARK_PURPLE);
                        sb.append("紫");
                    }
                    case 7 -> {
                        sb.append(DARK_RED);
                        sb.append("深红");
                    }
                    case 8 -> {
                        sb.append(GOLD);
                        sb.append("橙");
                    }
                    case 9 -> {
                        sb.append(GRAY);
                        sb.append("灰");
                    }
                    case 10 -> {
                        sb.append(GREEN);
                        sb.append("绿");
                    }
                    case 11 -> {
                        sb.append(LIGHT_PURPLE);
                        sb.append("洋红");
                    }
                    case 12 -> {
                        sb.append(RED);
                        sb.append("红");
                    }
                    case 13 -> {
                        sb.append(YELLOW);
                        sb.append("黄");
                    }
                    default -> {
                        sb.append(WHITE);
                        sb.append("白");
                    }
                }
                sb.append(RESET);
                sb.append("队！");
                sender.sendMessage(sb.toString());
                return true;
            } catch (NumberFormatException e) {
                sender.sendMessage(RED+"参数必须为0-14中的一个整数！");
                return false;
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
    
    private void unregisterTeams() {
        for (Team t: teams) {
            t.unregister();
        }
    }

    public void onDisable() {
        unregisterScoreboard();
        unloadRecipes();
        placeStartButton();
    }

    private void unloadRecipes() {
        for (NamespacedKey key : customRecipes.keySet()) {
            Bukkit.removeRecipe(key);
        }
    }
}
