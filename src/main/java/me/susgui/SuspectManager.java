package me.susgui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lưu danh sách người chơi bị GrimAC flag. Thread-safe vì trên Folia
 * event của Grim có thể đến từ nhiều thread khác nhau.
 */
public final class SuspectManager {

    private final Map<UUID, Suspect> suspects = new ConcurrentHashMap<>();
    private final long expireMillis;
    private final int minFlags;

    public SuspectManager(long expireMillis, int minFlags) {
        this.expireMillis = expireMillis;
        this.minFlags = minFlags;
    }

    public void addFlag(UUID uuid, String name, String check, String verbose, Object grimUser) {
        suspects.computeIfAbsent(uuid, id -> new Suspect(id, name)).add(name, check, verbose, grimUser);
    }

    public void remove(UUID uuid) {
        suspects.remove(uuid);
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        suspects.values().removeIf(s -> now - s.lastFlag() > expireMillis);
    }

    /** Danh sách đã lọc hết hạn, sắp xếp theo tổng vi phạm giảm dần, rồi theo lần flag gần nhất. */
    public List<Suspect> snapshot() {
        cleanup();
        List<Suspect> list = new ArrayList<>();
        for (Suspect s : suspects.values()) {
            if (s.total() >= minFlags) {
                list.add(s);
            }
        }
        Comparator<Suspect> byTotal = Comparator.comparingInt(Suspect::total);
        Comparator<Suspect> cmp = byTotal.thenComparingLong(Suspect::lastFlag);
        list.sort(cmp.reversed());
        return list;
    }

    public static final class Suspect {
        private final UUID uuid;
        private final Map<String, Integer> checks = new HashMap<>();
        private String name;
        private int total;
        private long lastFlag;
        private String lastCheck = "";
        private String lastVerbose = "";
        private Object grimUser;
        private String version;

        Suspect(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
            this.lastFlag = System.currentTimeMillis();
        }

        synchronized void add(String name, String check, String verbose, Object grimUser) {
            if (name != null) {
                this.name = name;
            }
            checks.merge(check, 1, Integer::sum);
            total++;
            lastFlag = System.currentTimeMillis();
            lastCheck = check;
            lastVerbose = verbose == null ? "" : verbose;
            if (grimUser != null) {
                this.grimUser = grimUser;
            }
        }

        public UUID uuid() {
            return uuid;
        }

        public synchronized String name() {
            return name;
        }

        public synchronized int total() {
            return total;
        }

        public synchronized long lastFlag() {
            return lastFlag;
        }

        public synchronized String lastCheck() {
            return lastCheck;
        }

        public synchronized String lastVerbose() {
            return lastVerbose;
        }

        public synchronized Object grimUser() {
            return grimUser;
        }

        public synchronized String version() {
            return version;
        }

        public synchronized void version(String version) {
            this.version = version;
        }

        public synchronized int distinctChecks() {
            return checks.size();
        }

        /** Các check bị flag nhiều nhất, giảm dần. */
        public synchronized List<Map.Entry<String, Integer>> topChecks(int limit) {
            List<Map.Entry<String, Integer>> list = new ArrayList<>(checks.entrySet());
            list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            if (list.size() > limit) {
                return new ArrayList<>(list.subList(0, limit));
            }
            return list;
        }
    }
}
