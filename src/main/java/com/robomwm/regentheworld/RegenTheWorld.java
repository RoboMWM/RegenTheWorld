package com.robomwm.regentheworld;

import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Created on 10/18/2018.
 *
 * @author RoboMWM
 */
public class RegenTheWorld extends JavaPlugin implements Listener
{
    private DataStore dataStore;
    private Set<World> enabledWorlds = new HashSet<>();
    private Map<World, Set<Long>> regenedChunks = new HashMap<>();
    private Queue<Chunk> chunksToRegen = new ArrayDeque<>();

    public void onEnable()
    {
        getConfig().options().header("autoLoadChunksToRegen: whether to load chunks to regen them automatically, or only perform when chunks are loaded by other means. Chunks are loaded from either the worldborder center or the world spawn, and spiral outwards. This feature _should_ avoid generating chunks.\n" +
                "radiusToLoadInChunks: How far autoLoadChunksToRegen should load chunks automatically. Defaults to vanilla /worldborder if less than 0.\n" +
                "chunkLoadRate: How many chunks to automatically load per second.");
        getConfig().addDefault("autoLoadChunksToRegen", false);
        getConfig().addDefault("radiusToLoadInChunks", -1);
        getConfig().addDefault("chunkLoadRate", 1);
        List<String> worlds = new ArrayList<>();
        for (World world : getServer().getWorlds())
            worlds.add(world.getName());
        getConfig().addDefault("enabledWorlds", worlds);
        getConfig().options().copyDefaults(true);
        saveConfig();
        GriefPrevention gp = (GriefPrevention)getServer().getPluginManager().getPlugin("GriefPrevention");
        dataStore = gp.dataStore;

        getServer().getPluginManager().registerEvents(this, this);

        for (String worldName : getConfig().getStringList("enabledWorlds"))
            enabledWorlds.add(getServer().getWorld(worldName));
        enabledWorlds.remove(null);

        //This got a bit messy
        //This just initializes the "regenedChunks" map with the worlds so no containsKey needs to be called.
        for (World world : enabledWorlds)
            regenedChunks.put(world, new HashSet<>());

        //regen task
        new BukkitRunnable()
        {
            @Override
            public void run()
            {
                Chunk chunk = chunksToRegen.poll();
                if (chunk == null)
                    return;
                if (chunk.getWorld().regenerateChunk(chunk.getX(), chunk.getZ()))
                    getLogger().info("Regenerated chunk " + chunk.getX() + " " + chunk.getZ());
                else
                    getLogger().warning("Failed to regenerate chunk " + chunk.getX() + " " + chunk.getZ());
            }
        }.runTaskTimer(this, 20L, 2L);

        if (!getConfig().getBoolean("autoLoadChunksToRegen"))
            return;

        int radius = getConfig().getInt("chunkLoadRadius");

        for (World world : enabledWorlds)
        {
            Location centerLocation = world.getSpawnLocation();
            if (radius < 0)
            {
                if (world.getWorldBorder() == null || world.getWorldBorder().getCenter() == null)
                {
                    radius = 60000000;
                }
                else
                {
                    centerLocation = world.getWorldBorder().getCenter();
                    radius = (int)(world.getWorldBorder().getSize() / 2); //i.e. world must have a border radius above 1000
                }
            }

            final int finalRadius = radius;
            final Location location = centerLocation;
            new BukkitRunnable()
            {
                final int X = location.getChunk().getX();
                final int Z = location.getChunk().getZ();
                int x = X;
                int z = Z;
                int distance = 0;
                int stage = 0;
                int direction = 0;
                final int viewDistance = finalRadius;
                @Override
                public void run()
                {
                    if (distance > viewDistance)
                    {
                        cancel();
                        return;
                    }
                    for(int i = 0; i < getConfig().getInt("chunkLoadRate"); i++)
                    {

                        if (distance == 0)
                        {
                            location.getWorld().getChunkAtAsync(location, false);
                            getLogger().info(String.valueOf(x) + " " + String.valueOf(z));
                            distance++;
                            x++;
                            z++;
                            return;
                        }

                        if (stage % (distance * 2) == 0) //corner
                        {
                            if (stage >= distance * 8) //done with this radius
                            {
                                distance++;
                                stage = 0;
                                direction = 0;
                                x = X + distance;
                                z = Z + distance;
                                return;
                            }
                            direction++;
                        }

                        getLogger().info(String.valueOf(x) + " " + String.valueOf(z) + " distance: " + distance + " stage: " + stage + " direction: " + direction);

                        switch(direction)
                        {
                            case 1:
                                location.getWorld().getChunkAtAsync(x, z--, false);
                                break;
                            case 2:
                                location.getWorld().getChunkAtAsync(x--, z, false);
                                break;
                            case 3:
                                location.getWorld().getChunkAtAsync(x, z++, false);
                                break;
                            case 4:
                                location.getWorld().getChunkAtAsync(x++, z, false);
                                break;
                            default:
                                return;
                        }
                        stage++;
                    }
                }
            }.runTaskTimerAsynchronously(this, 20L, 20L);
        }



        //TODO: task to load and regen chunks (but not generate new ones...?)
    }

    //If a lot of chunks load on the same tick, space out the
    // regens (don't schedule the regens on the same tick)
    //private int pendingTasks = 0;

    @EventHandler
    private void onChunkLoad(ChunkLoadEvent event)
    {
        if (event.isNewChunk())
            return;
        Chunk chunk = event.getChunk();
        if (!enabledWorlds.contains(chunk.getWorld()))
            return;

        long chunkKey = Chunk.getChunkKey(chunk.getX(), chunk.getZ());
        if (regenedChunks.get(event.getWorld()).contains(chunkKey))
            return;

        if (!dataStore.getClaims(chunk.getX(), chunk.getZ()).isEmpty())
            return;

        chunksToRegen.add(chunk);
        regenedChunks.get(event.getWorld()).add(chunkKey);

//        new BukkitRunnable()
//        {
//            @Override
//            public void run()
//            {
//                chunk.getWorld().regenerateChunk(chunk.getX(), chunk.getZ());
//                //pendingTasks--;
//            }
//        }.runTask(this); // runTaskLater(this, ++pendingTasks);
    }
}
