package me.susgui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Kết nối với GrimAC bằng reflection để nhận FlagEvent.
 * Thử lần lượt:
 *   1. API mới (Grim 2.3.73+): EventBus -> channel FlagEvent -> onFlag(...)
 *   2. API cũ: FlagEvent là Bukkit Event
 */
final class GrimHook {

    private static final String[] NEW_FLAG_EVENT = {"ac.grim.grimac.api.event.events.FlagEvent"};
    private static final String[] LEGACY_FLAG_EVENT = {
            "ac.grim.grimac.api.events.FlagEvent",
            "ac.grim.grimac.events.FlagEvent"
    };

    private final SusGuiPlugin plugin;
    private final SuspectManager manager;

    private boolean hooked;
    private Object bus;
    private Object grimPluginHandle;

    GrimHook(SusGuiPlugin plugin, SuspectManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    boolean isHooked() {
        return hooked;
    }

    boolean hook() {
        if (hooked) {
            return true;
        }
        Plugin grim = Bukkit.getPluginManager().getPlugin("GrimAC");
        if (grim == null || !grim.isEnabled()) {
            return false;
        }
        ClassLoader loader = grim.getClass().getClassLoader();

        try {
            if (hookEventBus(loader)) {
                hooked = true;
                plugin.getLogger().info("Đã kết nối GrimAC (EventBus API).");
                return true;
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "Hook EventBus thất bại, thử API cũ.", t);
        }

        try {
            if (hookBukkitEvent(loader)) {
                hooked = true;
                plugin.getLogger().info("Đã kết nối GrimAC (Bukkit event API).");
                return true;
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "Hook Bukkit event thất bại.", t);
        }
        return false;
    }

    void unhook() {
        if (bus != null && grimPluginHandle != null) {
            Reflect.call(bus, "unregisterAllListeners", grimPluginHandle);
        }
        hooked = false;
    }

    // ------------------------------------------------------------------ API mới

    private boolean hookEventBus(ClassLoader loader) throws Exception {
        Class<?> flagClass = firstClass(loader, NEW_FLAG_EVENT);
        if (flagClass == null) {
            return false;
        }
        Object api = findApi(loader);
        if (api == null) {
            return false;
        }

        Object eventBus = Reflect.invoke(api, "getEventBus");
        Object channel = Reflect.invoke(eventBus, "get", flagClass);

        Object handle;
        try {
            handle = Reflect.invoke(api, "getGrimPlugin", plugin);
        } catch (Exception e) {
            handle = plugin; // API cũ hơn: dùng overload nhận Object
        }

        Method onFlag = null;
        for (Method m : channel.getClass().getMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (!m.getName().equals("onFlag") || p.length != 2 || !p[0].isInstance(handle) || !p[1].isInterface()) {
                continue;
            }
            // ưu tiên overload nhận GrimPlugin thay vì overload Object (deprecated)
            if (onFlag == null || (onFlag.getParameterTypes()[0] == Object.class && p[0] != Object.class)) {
                onFlag = m;
            }
        }
        if (onFlag == null) {
            return false;
        }

        Class<?> handlerType = onFlag.getParameterTypes()[1];
        Object proxy = Proxy.newProxyInstance(handlerType.getClassLoader(), new Class<?>[]{handlerType},
                (p, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        if (method.getName().equals("equals")) {
                            return p == args[0];
                        }
                        if (method.getName().equals("hashCode")) {
                            return System.identityHashCode(p);
                        }
                        return "SusGuiFlagHandler";
                    }
                    if (args != null && args.length >= 3) {
                        onFlag(args[0], args[1], args[2]);
                    }
                    Class<?> ret = method.getReturnType();
                    if (ret == boolean.class || ret == Boolean.class) {
                        // giữ nguyên trạng thái cancelled mà Grim truyền vào
                        if (args != null) {
                            for (int i = args.length - 1; i >= 0; i--) {
                                if (args[i] instanceof Boolean b) {
                                    return b;
                                }
                            }
                        }
                        return false;
                    }
                    return null;
                });

        onFlag.setAccessible(true);
        onFlag.invoke(channel, handle, proxy);

        this.bus = eventBus;
        this.grimPluginHandle = handle;
        return true;
    }

    private Object findApi(ClassLoader loader) throws Exception {
        Class<?> abstractApi = firstClass(loader, "ac.grim.grimac.api.GrimAbstractAPI");
        if (abstractApi != null) {
            RegisteredServiceProvider<?> reg = Bukkit.getServicesManager().getRegistration(abstractApi);
            if (reg != null) {
                return reg.getProvider();
            }
        }
        Class<?> provider = firstClass(loader, "ac.grim.grimac.api.GrimAPIProvider");
        if (provider != null) {
            return provider.getMethod("get").invoke(null);
        }
        return null;
    }

    // ------------------------------------------------------------------ API cũ

    @SuppressWarnings("unchecked")
    private boolean hookBukkitEvent(ClassLoader loader) {
        Class<?> clazz = firstClass(loader, LEGACY_FLAG_EVENT);
        if (clazz == null || !Event.class.isAssignableFrom(clazz)) {
            return false;
        }
        Class<? extends Event> eventClass = (Class<? extends Event>) clazz;
        Bukkit.getPluginManager().registerEvent(eventClass, new Listener() {
        }, EventPriority.MONITOR, (listener, event) -> {
            if (!eventClass.isInstance(event)) {
                return;
            }
            onFlag(Reflect.call(event, "getPlayer"), Reflect.call(event, "getCheck"), Reflect.call(event, "getVerbose"));
        }, plugin, false);
        return true;
    }

    // ------------------------------------------------------------------ xử lý flag

    private void onFlag(Object user, Object check, Object verbose) {
        try {
            UUID uuid = null;
            String name = null;
            Object grimUser = null;

            if (user instanceof Player player) {
                uuid = player.getUniqueId();
                name = player.getName();
            } else if (user != null) {
                grimUser = user;
                Object id = Reflect.call(user, "getUniqueId");
                if (id instanceof UUID u) {
                    uuid = u;
                }
                Object n = Reflect.call(user, "getName");
                if (n instanceof String s) {
                    name = s;
                }
            }

            if (uuid == null && name != null) {
                Player p = Bukkit.getPlayerExact(name);
                if (p != null) {
                    uuid = p.getUniqueId();
                }
            }
            if (uuid == null) {
                return;
            }
            if (name == null) {
                Player p = Bukkit.getPlayer(uuid);
                name = p != null ? p.getName() : uuid.toString();
            }

            String checkName = checkName(check);
            String verboseText = verbose == null ? "" : verbose.toString();
            manager.addFlag(uuid, name, checkName, verboseText, grimUser);

            if (plugin.debug()) {
                plugin.getLogger().info("[debug] " + name + " flag " + checkName + " " + verboseText);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Lỗi khi xử lý flag từ GrimAC", t);
        }
    }

    private static String checkName(Object check) {
        if (check == null) {
            return "Unknown";
        }
        for (String method : new String[]{"getCheckName", "getDisplayName", "getName"}) {
            Object v = Reflect.call(check, method);
            if (v instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return check.getClass().getSimpleName();
    }

    private static Class<?> firstClass(ClassLoader loader, String... names) {
        for (String name : names) {
            try {
                return Class.forName(name, false, loader);
            } catch (Throwable ignored) {
                // thử tên tiếp theo
            }
        }
        return null;
    }
}
