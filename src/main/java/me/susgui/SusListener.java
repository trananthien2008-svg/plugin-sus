package me.susgui;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.UUID;

public final class SusListener implements Listener {

    private final SusGuiPlugin plugin;

    public SusListener(SusGuiPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SusGui gui)) {
            return;
        }
        event.setCancelled(true); // không cho lấy / đặt item

        if (!(event.getWhoClicked() instanceof Player admin)) {
            return;
        }
        if (!admin.isOp()) {
            admin.closeInventory();
            return;
        }
        if (event.getClickedInventory() != event.getInventory()) {
            return; // click vào túi đồ của chính mình
        }

        UUID targetId = gui.ownerAt(event.getSlot());
        if (targetId == null) {
            return;
        }

        if (event.isRightClick()) {
            plugin.suspects().remove(targetId);
            gui.render();
            return;
        }
        if (event.isLeftClick()) {
            teleport(admin, gui, targetId);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof SusGui) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof SusGui gui) {
            gui.markClosed();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.removeOnQuit()) {
            plugin.suspects().remove(event.getPlayer().getUniqueId());
        }
    }

    /**
     * Folia: không được đọc vị trí của người chơi khác từ thread của mình.
     * Đọc vị trí trên EntityScheduler của mục tiêu rồi teleportAsync cho admin.
     */
    private void teleport(Player admin, SusGui gui, UUID targetId) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            plugin.suspects().remove(targetId);
            admin.sendMessage(SusGuiPlugin.text("&cNgười chơi này đã offline."));
            gui.render();
            return;
        }

        String targetName = target.getName();
        admin.closeInventory();

        Runnable retired = () -> admin.getScheduler().run(plugin,
                t -> admin.sendMessage(SusGuiPlugin.text("&cKhông thể dịch chuyển: người chơi đã rời đi.")), null);

        target.getScheduler().run(plugin, task -> {
            Location location = target.getLocation();
            admin.teleportAsync(location, PlayerTeleportEvent.TeleportCause.COMMAND).thenAccept(success ->
                    admin.getScheduler().run(plugin, t -> admin.sendMessage(SusGuiPlugin.text(success
                            ? "&aĐã dịch chuyển tới &f" + targetName
                            : "&cDịch chuyển thất bại.")), null));
        }, retired);
    }
}
