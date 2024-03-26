/*
 * LightLogin - Optimised and Safe SpigotMC Software for Authentication
 *     Copyright © 2024  CMarco
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package top.cmarco.lightlogin.data;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import top.cmarco.lightlogin.LightLoginPlugin;
import top.cmarco.lightlogin.configuration.LightConfiguration;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AutoKickManager {

    private final LightLoginPlugin plugin;
    private final Map<UUID, Long> joinedMap = new HashMap<>();
    private final LoginTimeoutBarManager timeoutBarManager;
    private ScheduledTask foliaTask = null;

    public AutoKickManager(@NotNull final LightLoginPlugin plugin) {
        this.plugin = plugin;
        this.timeoutBarManager = new LoginTimeoutBarManager(plugin.getLightConfiguration());
    }

    public void startAutoKickTask() {
        if (foliaTask != null) {
            stopAutoKickTask();
        }

        final AuthenticationManager authenticationManager = plugin.getAuthenticationManager();
        double ticks = plugin.getLightConfiguration().getKickAfterSeconds() * 1E3;
        this.foliaTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, (s)-> {


            final Collection<? extends Player> onlinePlayers = plugin.getServer().getOnlinePlayers();
            for (final Player onlinePlayer : onlinePlayers) {

                if (!joinedMap.containsKey(onlinePlayer.getUniqueId())) {
                    addEntered(onlinePlayer);
                    continue;
                }

                final long lastEnter = joinedMap.get(onlinePlayer.getUniqueId());
                final boolean compareTo = (System.currentTimeMillis() - lastEnter) >= ticks;

                if (authenticationManager.isAuthenticated(onlinePlayer)) {
                    return;
                }

                if (!compareTo && plugin.getLightConfiguration().isLoginAnimationEnabled()) {
                    timeoutBarManager.sendBar(onlinePlayer, lastEnter);
                    timeoutBarManager.sendSound(onlinePlayer);
                    return;
                }

                LightConfiguration conf = plugin.getLightConfiguration();
                Component kickMsg = LegacyComponentSerializer.legacyAmpersand().deserialize(conf.getLoginTookTooMuchTime().replace("{PREFIX}", conf.getMessagePrefix()).replace("{PLAYER}", onlinePlayer.getName()));
                onlinePlayer.kick(kickMsg);
            }

        }, 1L, 20L);
    }

    public void stopAutoKickTask() {
        if (foliaTask == null) {
            return;
        }

        foliaTask.cancel();
        foliaTask = null;
    }

    public void addEntered(@NotNull final Player player) {
        this.joinedMap.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void cleanPlayerData(@NotNull final Player player) {
        this.joinedMap.remove(player.getUniqueId());
    }
}
