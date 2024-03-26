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

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import top.cmarco.lightlogin.LightLoginPlugin;
import top.cmarco.lightlogin.data.AuthenticationManager;

import java.util.List;
import java.util.function.Supplier;


public final class PlayerUnloggedListener extends NamedListener {

    private final LightLoginPlugin plugin;

    public PlayerUnloggedListener(@NotNull final LightLoginPlugin plugin) {
        super("player_unlogged_listener");
        this.plugin = plugin;
    }

    private <K extends Cancellable> void cancelIfPlayerUnauthenticated(@NotNull Player player, @NotNull K cancellable) {
        final AuthenticationManager authManager = plugin.getAuthenticationManager();

        if (!authManager.isAuthenticated(player)) {
            cancellable.setCancelled(true);
        }
    }

    private  <K extends Event & Cancellable> void cancelIfUnauthenticated(@NotNull K cancellableEntityEvent,
                                                                         @NotNull Supplier<Entity> playerSupplier) {
        final Entity entity = playerSupplier.get();
        if (entity instanceof Player) {
            this.cancelIfPlayerUnauthenticated((Player) entity, cancellableEntityEvent);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(final InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        this.cancelIfPlayerUnauthenticated(player, event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryCreative(final InventoryCreativeEvent event) {
        InventoryHolder inventoryHolder = event.getInventory().getHolder();
        if (inventoryHolder instanceof Player) {
            this.cancelIfPlayerUnauthenticated((Player) inventoryHolder, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandPreprocess(final PlayerCommandPreprocessEvent event) {
        final String message = event.getMessage();
        final Player player = event.getPlayer();

        if (plugin.getAuthenticationManager().isAuthenticated(player)) {
            return;
        }

        final List<String> allowedCommands = this.plugin.getLightConfiguration().getAllowedCommands();

        final boolean isAllowed = allowedCommands.stream().anyMatch(message::startsWith);
        if (!isAllowed) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVelocity(final PlayerVelocityEvent event) {
        this.cancelIfPlayerUnauthenticated(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTeleport(final PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            // TODO: check this!
        } else {
            this.cancelIfPlayerUnauthenticated(player, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(final PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }

        Player player = event.getPlayer();

        Location from = event.getFrom();
        Location to = event.getTo();

        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }

        this.cancelIfPlayerUnauthenticated(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(final AsyncPlayerChatEvent event) {
        this.cancelIfPlayerUnauthenticated(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(final PlayerInteractEvent event) {
        this.cancelIfPlayerUnauthenticated(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(final PlayerDropItemEvent event) {
        this.cancelIfPlayerUnauthenticated(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArrowPickup(final PlayerPickupArrowEvent event) {
        this.cancelIfPlayerUnauthenticated(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemPickup(final EntityPickupItemEvent event) {
        this.cancelIfUnauthenticated(event, event::getEntity);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageByEntity(final EntityDamageByEntityEvent event) {
        this.cancelIfUnauthenticated(event, event::getDamager);
        this.cancelIfUnauthenticated(event, event::getEntity);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBow(final EntityShootBowEvent event) {
        this.cancelIfUnauthenticated(event, event::getEntity);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHealthGain(final EntityRegainHealthEvent event) {
        this.cancelIfUnauthenticated(event, event::getEntity);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(final BlockBreakEvent event) {
        this.cancelIfUnauthenticated(event, event::getPlayer);
    }

}
