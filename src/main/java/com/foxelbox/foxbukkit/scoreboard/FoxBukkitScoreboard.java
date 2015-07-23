/**
 * This file is part of FoxBukkitScoreboard.
 *
 * FoxBukkitScoreboard is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FoxBukkitScoreboard is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with FoxBukkitScoreboard.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.foxelbox.foxbukkit.scoreboard;

import com.foxelbox.foxbukkit.permissions.FoxBukkitPermissionHandler;
import com.foxelbox.foxbukkit.permissions.FoxBukkitPermissions;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class FoxBukkitScoreboard extends JavaPlugin implements Listener {
    FoxBukkitPermissions permissions;
    FoxBukkitPermissionHandler permissionHandler;
    private StatsKeeper statsKeeper;

    HashMap<UUID, Scoreboard> playerStatsScoreboards = new HashMap<>();
    private final ArrayList<Scoreboard> registeredScoreboards = new ArrayList<>();
    private boolean mainScoreboardRegistered = false;

    @Override
    public void onDisable() {
        super.onDisable();
        registeredScoreboards.clear();
        playerStatsScoreboards.clear();
        statsKeeper.onDisable();
    }

    @Override
    public void onEnable() {
        permissions = (FoxBukkitPermissions)getServer().getPluginManager().getPlugin("FoxBukkitPermissions");
        permissionHandler = permissions.getHandler();
        statsKeeper = new StatsKeeper(this);

        permissionHandler.addRankChangeHandler(new FoxBukkitPermissionHandler.OnRankChange() {
            @Override
            public void rankChanged(UUID uuid, String rank) {
                Player ply = getServer().getPlayer(uuid);
                if (ply != null) {
                    setPlayerScoreboardTeam(ply, rank);
                }
            }
        });

        getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                statsKeeper.refreshAllStats();
            }
        }, 20, 40);

        getServer().getPluginManager().registerEvents(this, this);
    }

    public void setPlayerScoreboard(Player ply, Scoreboard scoreboard) {
        if(scoreboard == null) {
            scoreboard = playerStatsScoreboards.get(ply.getUniqueId());
        }
        ply.setScoreboard(scoreboard);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        registeredScoreboards.remove(playerStatsScoreboards.remove(event.getPlayer().getUniqueId()));
        statsKeeper.onDisconnect(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        Scoreboard playerScoreboard = playerStatsScoreboards.get(player.getUniqueId());
        if(playerScoreboard == null) {
            playerScoreboard = getServer().getScoreboardManager().getNewScoreboard();
            playerStatsScoreboards.put(player.getUniqueId(), playerScoreboard);
            registeredScoreboards.add(playerScoreboard);
            refreshScoreboards();
        }
        setPlayerScoreboardTeam(player);
        player.setScoreboard(playerScoreboard);
        statsKeeper.refreshStats(player);

    }

    private void setPlayerScoreboardTeam(Player ply) {
        setPlayerScoreboardTeam(ply, permissionHandler.getGroup(ply.getUniqueId()));
    }


    private void setPlayerScoreboardTeam(Player ply, String rank) {
        if(!mainScoreboardRegistered) {
            registeredScoreboards.add(getServer().getScoreboardManager().getMainScoreboard());
            mainScoreboardRegistered = true;
        }
        String sbEntry = ply.getName();
        for(Scoreboard scoreboard : registeredScoreboards) {
            boolean playerAlreadyInTeam = false;
            for(Team oldTeam : scoreboard.getTeams()) {
                if(oldTeam.getName().equals(rank)) {
                    if (oldTeam.hasEntry(sbEntry)) {
                        playerAlreadyInTeam = true;
                        break;
                    }
                } else {
                    oldTeam.removeEntry(sbEntry);
                }
            }
            if(playerAlreadyInTeam) {
                continue;
            }
            Team team = scoreboard.getTeam(rank);
            if(team == null) {
                team = scoreboard.registerNewTeam(rank);
                team.setPrefix(permissionHandler.getGroupTag(rank));
                team.setSuffix("\u00a7r");
            }
            team.addEntry(sbEntry);
        }
    }

    private void refreshScoreboards() {
        for(Player ply : getServer().getOnlinePlayers()) {
            setPlayerScoreboardTeam(ply);
        }
    }

    public Scoreboard createScoreboard() {
        Scoreboard scoreboard = getServer().getScoreboardManager().getNewScoreboard();
        registeredScoreboards.add(scoreboard);
        refreshScoreboards();
        return scoreboard;
    }
}
