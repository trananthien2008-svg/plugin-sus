package me.susgui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** GUI 4 hàng (36 ô). Mỗi ô là đầu của một người chơi đang bị GrimAC nghi vấn. */
public final class SusGui implements InventoryHolder {

    public static final int SIZE = 36;
    private static final int MAX_CHECKS_SHOWN = 5;
    private static final String SEPARATOR = "&8&m                      ";

    private final SusGuiPlugin plugin;
    private final Inventory inventory;
    private final UUID[] owners = new UUID[SIZE];
    private volatile boolean closed;

    public SusGui(SusGuiPlugin plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, SIZE, SusGuiPlugin.text("&c&lNgười chơi bị nghi vấn"));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Mở GUI cho người xem; phải gọi trên thread của người đó (command/click đều đúng thread). */
    public void open(Player viewer) {
        render();
        viewer.openInventory(inventory);

        int period = plugin.refreshTicks();
        // EntityScheduler của Folia: tự dừng khi người chơi thoát (retired = null)
        viewer.getScheduler().runAtFixedRate(plugin, task -> {
            if (closed) {
                task.cancel();
                return;
            }
            render();
        }, null, period, period);
    }

    public void markClosed() {
        closed = true;
    }

    /** UUID của người chơi ở ô này, hoặc null nếu ô trống. */
    public UUID ownerAt(int slot) {
        return slot >= 0 && slot < SIZE ? owners[slot] : null;
    }

    public void render() {
        inventory.clear();
        Arrays.fill(owners, null);

        int slot = 0;
        for (SuspectManager.Suspect suspect : plugin.suspects().snapshot()) {
            if (slot >= SIZE) {
                break;
            }
            Player player = Bukkit.getPlayer(suspect.uuid());
            if (player == null) {
                continue;
            }
            inventory.setItem(slot, buildHead(player, suspect));
            owners[slot] = suspect.uuid();
            slot++;
        }

        if (slot == 0) {
            ItemStack empty = new ItemStack(Material.EMERALD);
            ItemMeta meta = empty.getItemMeta();
            meta.displayName(SusGuiPlugin.text("&aKhông có ai bị nghi vấn"));
            List<Component> lore = new ArrayList<>();
            lore.add(SusGuiPlugin.text("&7GrimAC chưa flag người chơi nào gần đây."));
            meta.lore(lore);
            empty.setItemMeta(meta);
            inventory.setItem(13, empty);
        }
    }

    private ItemStack buildHead(Player player, SuspectManager.Suspect suspect) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(SusGuiPlugin.text("&c&l" + player.getName()));

        int ping = player.getPing();
        String pingColor = ping <= 100 ? "&a" : ping <= 200 ? "&e" : "&c";

        String version = suspect.version();
        if (version == null) {
            version = Versions.resolve(player, suspect.grimUser());
            if (version != null) {
                suspect.version(version);
            }
        }

        List<Component> lore = new ArrayList<>();
        lore.add(SusGuiPlugin.text(SEPARATOR));
        lore.add(SusGuiPlugin.text("&7Ping: " + pingColor + ping + "ms"));
        lore.add(SusGuiPlugin.text("&7Phiên bản: &f" + (version == null ? "Không rõ" : clean(version))));
        if (plugin.showIp()) {
            lore.add(SusGuiPlugin.text("&7IP: &f" + ipOf(player)));
        }
        lore.add(SusGuiPlugin.text("&7Tổng vi phạm: &c" + suspect.total()));
        lore.add(SusGuiPlugin.text(" "));
        lore.add(SusGuiPlugin.text("&7Nghi vấn về:"));
        for (Map.Entry<String, Integer> entry : suspect.topChecks(MAX_CHECKS_SHOWN)) {
            lore.add(SusGuiPlugin.text("&c ▸ &f" + clean(entry.getKey()) + " &7x" + entry.getValue()));
        }
        int more = suspect.distinctChecks() - MAX_CHECKS_SHOWN;
        if (more > 0) {
            lore.add(SusGuiPlugin.text("&7 ... và " + more + " check khác"));
        }
        lore.add(SusGuiPlugin.text(" "));
        lore.add(SusGuiPlugin.text("&7Lần cuối: &f" + ago(System.currentTimeMillis() - suspect.lastFlag())
                + " &8(" + clean(suspect.lastCheck()) + ")"));
        if (plugin.showVerbose() && !suspect.lastVerbose().isEmpty()) {
            String verbose = clean(suspect.lastVerbose());
            if (verbose.length() > 36) {
                verbose = verbose.substring(0, 36) + "...";
            }
            lore.add(SusGuiPlugin.text("&7Chi tiết: &8" + verbose));
        }
        lore.add(SusGuiPlugin.text(SEPARATOR));
        lore.add(SusGuiPlugin.text("&eChuột trái &7» Dịch chuyển tới"));
        lore.add(SusGuiPlugin.text("&eChuột phải &7» Xoá khỏi danh sách"));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static String ipOf(Player player) {
        InetSocketAddress address = player.getAddress();
        if (address == null || address.getAddress() == null) {
            return "Không rõ";
        }
        return address.getAddress().getHostAddress();
    }

    private static String ago(long millis) {
        long seconds = Math.max(0, millis / 1000);
        if (seconds < 60) {
            return seconds + " giây trước";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + " phút trước";
        }
        return (minutes / 60) + " giờ trước";
    }

    /** Bỏ ký tự màu để dữ liệu từ Grim không phá định dạng lore. */
    private static String clean(String s) {
        return s.replace("&", "").replace("§", "");
    }
}
