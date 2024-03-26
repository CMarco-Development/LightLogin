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

package top.cmarco.lightlogin.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import top.cmarco.lightlogin.LightLoginPlugin;
import top.cmarco.lightlogin.api.PlayerAuthenticateEvent;
import top.cmarco.lightlogin.api.PlayerRegisterEvent;
import top.cmarco.lightlogin.api.PlayerUnauthenticateEvent;
import top.cmarco.lightlogin.api.UnregisterEvent;
import top.cmarco.lightlogin.data.VoidLoginManager;
import top.cmarco.lightlogin.log.AuthLogs;

public class AuthenticationListener extends NamedListener {

    private final LightLoginPlugin plugin;

    public AuthenticationListener(@NotNull final LightLoginPlugin plugin) {
        super("authentication_listener");
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public final void onAuth(@NotNull final PlayerAuthenticateEvent event) {
        Player player = event.getPlayer();

        plugin.getStartupLoginsManager().addPlayer(player);

        plugin.getServer().getOnlinePlayers().stream()
                .filter(p -> !p.equals(player))
                .forEach(p -> p.showPlayer(plugin, player));

        runSyncEntity(player, plugin, () -> {
            player.removePotionEffect(PotionEffectType.BLINDNESS);

            if (plugin.getLightConfiguration().isSoundsEnabled()) {
                player.playSound(player.getEyeLocation(), Sound.valueOf(plugin.getLightConfiguration().getSuccessfulLoginSound()), 1f, 1f);
            }

        });

        AuthLogs authLogs = plugin.getAuthLogs();
        authLogs.add("Player " + player.getName() + " has been authenticated through " + event.getAuthenticationCause().getFormalName());

    }

    @EventHandler(priority = EventPriority.HIGH)
    public final void onUnauth(@NotNull final PlayerUnauthenticateEvent event) {
        Player player = event.getPlayer();
        runSyncEntity(player, plugin, () -> {
            plugin.getAutoKickManager().cleanPlayerData(player);
            giveBlindness(player, plugin);
        });
        AuthLogs authLogs = plugin.getAuthLogs();
        authLogs.add("Player " + player.getName() + " has been unauthenticated through " + event.getAuthenticationCause().getFormalName());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public final void onRegister(@NotNull final PlayerRegisterEvent event) {
        Player player = event.getPlayer();
        AuthLogs authLogs = plugin.getAuthLogs();
        authLogs.add("Player " + player.getName() + " has successfully registered.");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public final void onUnregister(@NotNull final UnregisterEvent event) {
        AuthLogs authLogs = plugin.getAuthLogs();
        authLogs.add("Player " + event.getName() + " has been unregistered.");

        Player player = Bukkit.getPlayer(event.getUuid());
        if (player != null && player.isOnline()) {
            runSyncEntity(player, plugin, () -> giveBlindness(player, plugin));
        }
    }

}
