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
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import top.cmarco.lightlogin.LightLoginPlugin;
import top.cmarco.lightlogin.api.AuthenticationCause;
import top.cmarco.lightlogin.api.PlayerAuthenticateEvent;
import top.cmarco.lightlogin.command.LightLoginCommand;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

public class BasicAuthenticationManager implements AuthenticationManager {

    protected final LightLoginPlugin plugin;

    protected final Set<UUID> authenticatedSet = new CopyOnWriteArraySet<>();
    protected final Set<UUID> unregisteredSet = new CopyOnWriteArraySet<>();
    protected final Set<UUID> unloginnedSet = new CopyOnWriteArraySet<>();

    private ScheduledTask loginMsg = null, registerMsg = null;

    public BasicAuthenticationManager(@NotNull LightLoginPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void startLoginNotifyTask() {
        if (loginMsg != null) {
            loginMsg.cancel();
            loginMsg = null;
        }

        this.loginMsg = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, (s) -> {
            plugin.getServer().getOnlinePlayers().stream()
                    .filter(p -> unloginnedSet.contains(p.getUniqueId()))
                    .forEach(p -> LightLoginCommand.sendColorPrefixMessages(p, plugin.getLightConfiguration().getLoginMessages(), plugin));
            }
        , 1L, 20L*5L);
    }

    @Override
    public void startRegisterNotifyTask() {
        if (registerMsg != null) {
            registerMsg.cancel();
            registerMsg = null;
        }

        this.registerMsg = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, (s) -> {
                    plugin.getServer().getOnlinePlayers().stream()
                            .filter(p -> unregisteredSet.contains(p.getUniqueId()))
                            .forEach(p -> LightLoginCommand.sendColorPrefixMessages(p, plugin.getLightConfiguration().getRegisterMessage(), plugin));
                }
                , 1L, 20L*5L);
    }

    @Override
    public boolean isAuthenticated(@NotNull UUID playerUuid) {
        return this.authenticatedSet.contains(playerUuid);
    }

    @Override
    public void authenticate(@NotNull UUID playerUuid) {
        this.authenticatedSet.add(playerUuid);
        removeUnloginned(playerUuid);
        removeUnregistered(playerUuid);
    }

    @Override
    public void authenticate(@NotNull Player player) {
        this.authenticate(player.getUniqueId());
    }

    @Override
    public void addUnregistered(Player player) {
        addUnregistered(player.getUniqueId());
    }

    @Override
    public void removeUnregistered(Player player) {
        removeUnregistered(player.getUniqueId());
    }

    @Override
    public void addUnloginned(Player player) {
        addUnloginned(player.getUniqueId());
    }

    @Override
    public void removeUnloginned(Player player) {
        removeUnloginned(player.getUniqueId());
    }

    @Override
    public void addUnregistered(UUID uuid) {
        this.unregisteredSet.add(uuid);
        removeUnloginned(uuid);
        unauthenticate(uuid);
    }

    @Override
    public void removeUnregistered(UUID uuid) {
        this.unregisteredSet.remove(uuid);
    }

    @Override
    public void addUnloginned(UUID uuid) {
        this.unloginnedSet.add(uuid);
        removeUnregistered(uuid);
        unauthenticate(uuid);
    }

    @Override
    public void removeUnloginned(UUID uuid) {
        this.unloginnedSet.remove(uuid);
    }

    @Override
    public void unauthenticate(@NotNull UUID playerUuid) {
        this.authenticatedSet.remove(playerUuid);
    }
}
