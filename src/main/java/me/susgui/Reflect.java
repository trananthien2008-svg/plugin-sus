package me.susgui;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Tiện ích reflection nhỏ để gọi API của GrimAC / ViaVersion mà không cần
 * thêm chúng làm dependency lúc build (tránh vỡ khi Grim đổi phiên bản API).
 */
final class Reflect {

    private Reflect() {
    }

    /** Gọi method public theo tên + tham số. Ném exception nếu không tìm thấy hoặc method lỗi. */
    static Object invoke(Object target, String name, Object... args) throws Exception {
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(name) || !accepts(m.getParameterTypes(), args)) {
                continue;
            }
            try {
                m.setAccessible(true);
            } catch (RuntimeException ignored) {
                // vẫn thử gọi bình thường
            }
            try {
                return m.invoke(target, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception ex) {
                    throw ex;
                }
                throw e;
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + name);
    }

    /** Giống invoke nhưng trả về null thay vì ném exception. */
    static Object call(Object target, String name, Object... args) {
        if (target == null) {
            return null;
        }
        try {
            return invoke(target, name, args);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean accepts(Class<?>[] types, Object[] args) {
        if (types.length != args.length) {
            return false;
        }
        for (int i = 0; i < types.length; i++) {
            if (args[i] == null) {
                if (types[i].isPrimitive()) {
                    return false;
                }
            } else if (!box(types[i]).isInstance(args[i])) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> box(Class<?> c) {
        if (!c.isPrimitive()) {
            return c;
        }
        if (c == int.class) return Integer.class;
        if (c == boolean.class) return Boolean.class;
        if (c == long.class) return Long.class;
        if (c == double.class) return Double.class;
        if (c == float.class) return Float.class;
        if (c == short.class) return Short.class;
        if (c == byte.class) return Byte.class;
        if (c == char.class) return Character.class;
        return c;
    }
}
