package com.robomwm.regentheworld;

import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Created on 10/18/2018.
 *
 * @author RoboMWM
 */
public class RegenTheWorld extends JavaPlugin implements Listener
{
    private DataStore dataStore;
    public void onEnable()
    {
        GriefPrevention gp = (GriefPrevention)getServer().getPluginManager().getPlugin("GriefPrevention");
        dataStore = gp.dataStore;

        getServer().getPluginManager().registerEvents(this, this);

        //TODO: task to load and regen chunks (but not generate new ones...?)
    }

    //If a lot of chunks load on the same tick, space out the
    // regens (don't schedule the regens on the same tick)
    private int pendingTasks = 0;

    @EventHandler
    private void onChunkLoad(ChunkLoadEvent event)
    {
        if (event.isNewChunk())
            return;
        Chunk chunk = event.getChunk();
        if (!dataStore.getClaims(chunk.getX(), chunk.getZ()).isEmpty())
            return;

        new BukkitRunnable()
        {
            @Override
            public void run()
            {
                chunk.getWorld().regenerateChunk(chunk.getX(), chunk.getZ());
                pendingTasks--;
            }
        }.runTaskLater(this, ++pendingTasks);
    }
}
