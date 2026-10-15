package me.susgui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Lấy phiên bản Minecraft của client: ưu tiên GrimAC, sau đó ViaVersion. */
final class Versions {

    private Versions() {
    }

    /** @return tên phiên bản (vd "1.21.4") hoặc null nếu không xác định được. */
    static String resolve(Player player, Object grimUser) {
        String v = fromGrim(grimUser);
        if (v != null) {
            return v;
        }
        return fromViaVersion(player);
    }

    private static String fromGrim(Object grimUser) {
        if (grimUser == null) {
            return null;
        }
        Object name = Reflect.call(grimUser, "getVersionName");
        if (name instanceof String s && !s.isBlank()) {
            return s;
        }
        Object client = Reflect.call(grimUser, "getClientVersion");
        if (client != null) {
            Object release = Reflect.call(client, "getReleaseName");
            if (release instanceof String s && !s.isBlank()) {
                return s;
            }
            return client.toString();
        }
        return null;
    }

    private static String fromViaVersion(Player player) {
        try {
            Plugin via = Bukkit.getPluginManager().getPlugin("ViaVersion");
            if (via == null || !via.isEnabled()) {
                return null;
            }
            ClassLoader loader = via.getClass().getClassLoader();
            Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via", false, loader);
            Object api = viaClass.getMethod("getAPI").invoke(null);
            Object protocol = Reflect.invoke(api, "getPlayerVersion", player.getUniqueId());

            Class<?> versionClass = Class.forName("com.viaversion.viaversion.api.protocol.version.ProtocolVersion",
                    false, loader);
            Object version = versionClass.getMethod("getProtocol", int.class).invoke(null, protocol);
            Object name = Reflect.call(version, "getName");
            return name instanceof String s && !s.isBlank() ? s : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
