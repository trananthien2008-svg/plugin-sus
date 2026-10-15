package me.susgui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

public final class SusGuiPlugin extends JavaPlugin {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private SuspectManager suspects;
    private GrimHook grimHook;

    private int refreshTicks;
    private boolean showIp;
    private boolean showVerbose;
    private boolean removeOnQuit;
    private boolean debug;

    /** Chuyển chuỗi dùng mã màu '&' thành Component (tắt chữ nghiêng mặc định của lore). */
    public static Component text(String legacy) {
        return LEGACY.deserialize(legacy).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        refreshTicks = Math.max(10, config.getInt("refresh-ticks", 40));
        showIp = config.getBoolean("show-ip", true);
        showVerbose = config.getBoolean("show-verbose", true);
        removeOnQuit = config.getBoolean("remove-on-quit", true);
        debug = config.getBoolean("debug", false);

        long expireMillis = Math.max(1, config.getInt("expire-minutes", 10)) * 60_000L;
        int minFlags = Math.max(1, config.getInt("min-flags", 1));
        suspects = new SuspectManager(expireMillis, minFlags);

        getServer().getPluginManager().registerEvents(new SusListener(this), this);
        PluginCommand command = getCommand("sus");
        if (command != null) {
            command.setExecutor(new SusCommand(this));
        }

        grimHook = new GrimHook(this, suspects);
        if (!grimHook.hook()) {
            // GrimAC có thể chưa sẵn sàng ngay lúc này: thử lại sau 2 giây (global region scheduler của Folia)
            getServer().getGlobalRegionScheduler().runDelayed(this, task -> {
                if (!grimHook.hook()) {
                    getLogger().warning("Không kết nối được GrimAC! Hãy kiểm tra GrimAC đã được cài và bật "
                            + "(plugin tên \"GrimAC\") và phiên bản có API FlagEvent.");
                }
            }, 40L);
        }

        // Dọn người chơi hết hạn mỗi phút (async scheduler, an toàn trên Folia)
        getServer().getAsyncScheduler().runAtFixedRate(this, task -> suspects.cleanup(), 1, 1, TimeUnit.MINUTES);
    }

    @Override
    public void onDisable() {
        if (grimHook != null) {
            grimHook.unhook();
        }
    }

    public SuspectManager suspects() {
        return suspects;
    }

    public int refreshTicks() {
        return refreshTicks;
    }

    public boolean showIp() {
        return showIp;
    }

    public boolean showVerbose() {
        return showVerbose;
    }

    public boolean removeOnQuit() {
        return removeOnQuit;
    }

    public boolean debug() {
        return debug;
    }
}
