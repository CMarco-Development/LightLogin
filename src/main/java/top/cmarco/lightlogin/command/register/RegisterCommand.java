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

package top.cmarco.lightlogin.command.register;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import top.cmarco.lightlogin.LightLoginPlugin;
import top.cmarco.lightlogin.api.AuthenticationCause;
import top.cmarco.lightlogin.api.PlayerAuthenticateEvent;
import top.cmarco.lightlogin.api.PlayerRegisterEvent;
import top.cmarco.lightlogin.command.LightLoginCommand;
import top.cmarco.lightlogin.command.utils.CommandUtils;
import top.cmarco.lightlogin.data.LightLoginDbRow;
import top.cmarco.lightlogin.database.PluginDatabase;
import top.cmarco.lightlogin.encrypt.Argon2Utilities;
import top.cmarco.lightlogin.network.NetworkUtilities;

import java.util.Base64;

public final class RegisterCommand extends LightLoginCommand {

    public RegisterCommand(@NotNull LightLoginPlugin plugin) {
        super(plugin, null, "register", false);
    }

    @Override
    protected void commandLogic(@NotNull final CommandSender sender, @NotNull final String[] args) {

        if (!(sender instanceof Player)) {
            return;
        }

        final Player player = (Player) sender;

        if (args.length != 2) {
            sendColorPrefixMessages(player, super.configuration.getRegisterIncorrectUsageMessage(), super.plugin);
            return;
        }

        if (!args[0].equals(args[1])) {
            sendColorPrefixMessages(player, super.configuration.getRegisterUnequalPasswordsMessage(), super.plugin);
            return;
        }

        if (super.configuration.isSafePasswordForceEnabled() && !CommandUtils.isPasswordSafe(args[0], super.configuration)) {
            CommandUtils.unsafePasswordMsg(player, super.configuration, super.plugin);
            return;
        }

        PluginDatabase database = super.plugin.getDatabase();

        database.searchRowFromPK(player.getUniqueId().toString())
                .whenCompleteAsync((row, throwable) -> {

                    if (throwable != null) {
                        super.plugin.getLogger().warning(throwable.getLocalizedMessage());
                        if (player.isOnline()) {
                            sendColorPrefixMessages(player, super.configuration.getRegisterError(), super.plugin);
                        }
                        return;
                    }

                    super.plugin.getServer().getGlobalRegionScheduler().run(super.plugin, (s) -> {

                        if (row != null) {
                            sendColorPrefixMessages(player, super.configuration.getAlreadyRegisteredMessage(), super.plugin);
                        } else {
                            final String uuid = player.getUniqueId().toString();
                            final byte[] salt = Argon2Utilities.generateSaltByte(0x10);
                            final String password = Argon2Utilities.encryptArgon2(args[0], salt);
                            final String email = null;
                            final long lastLogin = System.currentTimeMillis();
                            final long lastIpv4 = NetworkUtilities.convertInetSocketAddressToLong(player.getAddress());

                            database.addRow(new LightLoginDbRow(uuid,
                                    password,
                                    Base64.getEncoder().encodeToString(salt),
                                    email,
                                    lastLogin,
                                    lastIpv4)
                            ).whenCompleteAsync((addedRow, throwable2) -> {

                                if (addedRow == null || throwable2 != null) {
                                    if (player.isOnline()) {
                                        sendColorPrefixMessages(player, super.configuration.getRegisterError(), super.plugin);
                                    }
                                    if (addedRow == null) {
                                        this.plugin.getLogger().warning("WARNING! Error registering player: " + player.getName());
                                    }
                                    if (throwable2 == null) {
                                        this.plugin.getLogger().warning("WARNING! Throwable received registering player: " + throwable2.getLocalizedMessage());
                                    }
                                    return;
                                }

                                plugin.getServer().getGlobalRegionScheduler().run(plugin, (t) -> {
                                    PlayerAuthenticateEvent playerAuthenticateEvent = new PlayerAuthenticateEvent(player, AuthenticationCause.COMMAND);
                                    this.plugin.getServer().getPluginManager().callEvent(playerAuthenticateEvent);
                                });

                                super.plugin.getAuthenticationManager().authenticate(player);

                                if (!player.isOnline()) return;

                                super.plugin.getPlaintextPasswordManager().setPassword(player, args[1]);

                                sendColorPrefixMessages(player, super.configuration.getRegisterSuccessMessage(), super.plugin);

                                final PlayerRegisterEvent playerRegisterEvent = new PlayerRegisterEvent(player);
                                super.plugin.getServer().getGlobalRegionScheduler().run(super.plugin, ($s) -> {
                                    super.plugin.getServer().getPluginManager().callEvent(playerRegisterEvent);
                                });
                            });
                        }

                    });
                });

    }
}
