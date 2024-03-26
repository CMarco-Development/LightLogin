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

package top.cmarco.lightlogin.command.login;

import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import top.cmarco.lightlogin.LightLoginPlugin;
import top.cmarco.lightlogin.api.AuthenticationCause;
import top.cmarco.lightlogin.api.PlayerAuthenticateEvent;
import top.cmarco.lightlogin.api.PlayerWrongPasswordEvent;
import top.cmarco.lightlogin.command.LightLoginCommand;
import top.cmarco.lightlogin.data.AuthenticationManager;
import top.cmarco.lightlogin.database.LightLoginColumn;
import top.cmarco.lightlogin.database.PluginDatabase;
import top.cmarco.lightlogin.encrypt.Argon2Utilities;

import java.util.Base64;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LoginCommand extends LightLoginCommand {
    private static final long HOUR_TICKS = 20L * 60L * 60L;
    private final HashMap<UUID, Long> lastLoginAttempt = new HashMap<>();
    private final ConcurrentHashMap<UUID, Integer> failedAttempts = new ConcurrentHashMap<>();

    public LoginCommand(@NotNull LightLoginPlugin plugin) {
        super(plugin, null, "login", false);
    }

    public void startClearTasks() {
        super.plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                super.plugin,
                (s) -> this.failedAttempts.clear(),
                HOUR_TICKS * 24,
                HOUR_TICKS * 24);
    }

    @Override
    protected void commandLogic(@NotNull CommandSender sender, @NotNull String[] args) {

        if (!(sender instanceof final Player player)) {
            return;
        }

        if (args.length != 1) {
            sendColorPrefixMessages(player, super.configuration.getLoginIncorrectUsage(), super.plugin);
            return;
        }

        final UUID uuid = player.getUniqueId();
        final long timeNow = System.currentTimeMillis();
        if (!this.lastLoginAttempt.containsKey(uuid)) {
            this.lastLoginAttempt.put(uuid, timeNow);
            this.failedAttempts.put(uuid, 1);
        } else {
            final long lastAttempt = this.lastLoginAttempt.get(uuid);
            if ((timeNow - lastAttempt) / 1E3 <= super.configuration.getLoginDelay()) {
                sendColorPrefixMessages(player, super.configuration.getCommandTooFast(), super.plugin);
                return;
            }
            this.lastLoginAttempt.put(uuid, timeNow);
        }


        final AuthenticationManager authManager = super.plugin.getAuthenticationManager();

        if (authManager.isAuthenticated(player)) {
            sendColorPrefixMessages(player, super.configuration.getLoginAlreadyAuthenticated(), super.plugin);
            return;
        }

        final String password = args[0];

        final PluginDatabase database = super.plugin.getDatabase();

        database.searchRowFromPK(player.getUniqueId().toString())
                .whenCompleteAsync((row, throwable) -> {

                    if (throwable != null) {
                        if (player.isOnline()) {
                            sendColorPrefixMessages(player, super.configuration.getLoginError(), super.plugin);
                        }
                        super.plugin.getLogger().warning(throwable.getLocalizedMessage());
                        return;
                    }

                    if (row == null) {
                        if (player.isOnline()) {
                            sendColorPrefixMessages(player, super.configuration.getLoginUnregistered(), super.plugin);
                        }
                        return;
                    }

                    final String hashAttempt = Argon2Utilities.encryptArgon2(password, Base64.getDecoder().decode(row.getPasswordSalt()));
                    final String databaseHash = row.getPasswordHash();

                    if (databaseHash.equals(hashAttempt)) {

                        if (player.isOnline()) {
                            sendColorPrefixMessages(player, super.configuration.getLoginSuccess(), super.plugin);
                        }

                        plugin.getPlaintextPasswordManager().setPassword(player, password);

                        plugin.getServer().getGlobalRegionScheduler().run(plugin, (s) -> {
                            PlayerAuthenticateEvent playerAuthenticateEvent = new PlayerAuthenticateEvent(player, AuthenticationCause.COMMAND);
                            this.plugin.getServer().getPluginManager().callEvent(playerAuthenticateEvent);
                        });

                        authManager.authenticate(player);
                        database.updateRow(uuid.toString(), LightLoginColumn.LAST_LOGIN, System.currentTimeMillis());

                    } else {

                        if (player.isOnline()) {

                            plugin.getServer().getGlobalRegionScheduler()
                                    .run(plugin, (s) -> {
                                        PlayerWrongPasswordEvent event = new PlayerWrongPasswordEvent(player);
                                        plugin.getServer().getPluginManager().callEvent(event);
                                    });

                            sendColorPrefixMessages(player, super.configuration.getLoginWrongPassword(), super.plugin);

                            if (plugin.getLightConfiguration().isSoundsEnabled()) {
                                player.playSound(player.getEyeLocation(), Sound.valueOf(plugin.getLightConfiguration().getWrongPasswordSound()), 1f, 1f);
                            }
                        }

                        int currentAttempts = this.failedAttempts.get(uuid);

                        if (currentAttempts >= super.configuration.getMaxFailedAttempts() + 1) {
                            super.plugin.getServer().getGlobalRegionScheduler().run(super.plugin, (s) -> super.configuration.getBruteforcePunishment().forEach(cmd -> super.plugin.getServer().dispatchCommand(super.plugin.getServer().getConsoleSender(), cmd.replace("{PLAYER}", player.getName()))));
                        } else {
                            this.failedAttempts.put(uuid, currentAttempts + 1);
                        }
                    }

                });

    }
}
