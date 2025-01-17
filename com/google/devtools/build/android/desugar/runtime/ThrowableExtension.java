package com.google.devtools.build.android.desugar.runtime;

import java.io.Closeable;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

public final class ThrowableExtension {
    private static final String ANDROID_OS_BUILD_VERSION = "android.os.Build$VERSION";
    static final int API_LEVEL;
    static final AbstractDesugaringStrategy STRATEGY;
    public static final String SYSTEM_PROPERTY_TWR_DISABLE_MIMIC = "com.google.devtools.build.android.desugar.runtime.twr_disable_mimic";

    static abstract class AbstractDesugaringStrategy {
        protected static final Throwable[] EMPTY_THROWABLE_ARRAY = new Throwable[0];

        public abstract void addSuppressed(Throwable th, Throwable th2);

        public abstract Throwable[] getSuppressed(Throwable th);

        public abstract void printStackTrace(Throwable th);

        public abstract void printStackTrace(Throwable th, PrintStream printStream);

        public abstract void printStackTrace(Throwable th, PrintWriter printWriter);

        AbstractDesugaringStrategy() {
        }
    }

    static final class ConcurrentWeakIdentityHashMap {
        private final ConcurrentHashMap<WeakKey, List<Throwable>> map = new ConcurrentHashMap<>(16, 0.75f, 10);
        private final ReferenceQueue<Throwable> referenceQueue = new ReferenceQueue<>();

        private static final class WeakKey extends WeakReference<Throwable> {
            private final int hash;

            public WeakKey(Throwable referent, ReferenceQueue<Throwable> q) {
                super(referent, q);
                if (referent == null) {
                    throw new NullPointerException("The referent cannot be null");
                }
                this.hash = System.identityHashCode(referent);
            }

            public int hashCode() {
                return this.hash;
            }

            public boolean equals(Object obj) {
                if (obj == null || obj.getClass() != getClass()) {
                    return false;
                }
                if (this == obj) {
                    return true;
                }
                WeakKey other = (WeakKey) obj;
                if (this.hash == other.hash && get() == other.get()) {
                    return true;
                }
                return false;
            }
        }

        ConcurrentWeakIdentityHashMap() {
        }

        public List<Throwable> get(Throwable throwable, boolean createOnAbsence) {
            deleteEmptyKeys();
            List<Throwable> list = (List) this.map.get(new WeakKey(throwable, null));
            if (!createOnAbsence) {
                return list;
            }
            if (list != null) {
                return list;
            }
            List<Throwable> newValue = new Vector<>(2);
            List<Throwable> list2 = (List) this.map.putIfAbsent(new WeakKey(throwable, this.referenceQueue), newValue);
            return list2 != null ? list2 : newValue;
        }

        /* access modifiers changed from: 0000 */
        public int size() {
            return this.map.size();
        }

        /* access modifiers changed from: 0000 */
        public void deleteEmptyKeys() {
            Reference<?> key = this.referenceQueue.poll();
            while (key != null) {
                this.map.remove(key);
                key = this.referenceQueue.poll();
            }
        }
    }

    static final class MimicDesugaringStrategy extends AbstractDesugaringStrategy {
        static final String SUPPRESSED_PREFIX = "Suppressed: ";
        private final ConcurrentWeakIdentityHashMap map = new ConcurrentWeakIdentityHashMap();

        MimicDesugaringStrategy() {
        }

        public void addSuppressed(Throwable receiver, Throwable suppressed) {
            if (suppressed == receiver) {
                throw new IllegalArgumentException("Self suppression is not allowed.", suppressed);
            } else if (suppressed == null) {
                throw new NullPointerException("The suppressed exception cannot be null.");
            } else {
                this.map.get(receiver, true).add(suppressed);
            }
        }

        public Throwable[] getSuppressed(Throwable receiver) {
            List<Throwable> list = this.map.get(receiver, false);
            if (list == null || list.isEmpty()) {
                return EMPTY_THROWABLE_ARRAY;
            }
            return (Throwable[]) list.toArray(EMPTY_THROWABLE_ARRAY);
        }

        public void printStackTrace(Throwable receiver) {
            receiver.printStackTrace();
            List<Throwable> suppressedList = this.map.get(receiver, false);
            if (suppressedList != null) {
                synchronized (suppressedList) {
                    for (Throwable suppressed : suppressedList) {
                        System.err.print(SUPPRESSED_PREFIX);
                        suppressed.printStackTrace();
                    }
                }
            }
        }

        public void printStackTrace(Throwable receiver, PrintStream stream) {
            receiver.printStackTrace(stream);
            List<Throwable> suppressedList = this.map.get(receiver, false);
            if (suppressedList != null) {
                synchronized (suppressedList) {
                    for (Throwable suppressed : suppressedList) {
                        stream.print(SUPPRESSED_PREFIX);
                        suppressed.printStackTrace(stream);
                    }
                }
            }
        }

        public void printStackTrace(Throwable receiver, PrintWriter writer) {
            receiver.printStackTrace(writer);
            List<Throwable> suppressedList = this.map.get(receiver, false);
            if (suppressedList != null) {
                synchronized (suppressedList) {
                    for (Throwable suppressed : suppressedList) {
                        writer.print(SUPPRESSED_PREFIX);
                        suppressed.printStackTrace(writer);
                    }
                }
            }
        }
    }

    static final class NullDesugaringStrategy extends AbstractDesugaringStrategy {
        NullDesugaringStrategy() {
        }

        public void addSuppressed(Throwable receiver, Throwable suppressed) {
        }

        public Throwable[] getSuppressed(Throwable receiver) {
            return EMPTY_THROWABLE_ARRAY;
        }

        public void printStackTrace(Throwable receiver) {
            receiver.printStackTrace();
        }

        public void printStackTrace(Throwable receiver, PrintStream stream) {
            receiver.printStackTrace(stream);
        }

        public void printStackTrace(Throwable receiver, PrintWriter writer) {
            receiver.printStackTrace(writer);
        }
    }

    static final class ReuseDesugaringStrategy extends AbstractDesugaringStrategy {
        ReuseDesugaringStrategy() {
        }

        public void addSuppressed(Throwable receiver, Throwable suppressed) {
            receiver.addSuppressed(suppressed);
        }

        public Throwable[] getSuppressed(Throwable receiver) {
            return receiver.getSuppressed();
        }

        public void printStackTrace(Throwable receiver) {
            receiver.printStackTrace();
        }

        public void printStackTrace(Throwable receiver, PrintStream stream) {
            receiver.printStackTrace(stream);
        }

        public void printStackTrace(Throwable receiver, PrintWriter writer) {
            receiver.printStackTrace(writer);
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:19:0x0068  */
    /* JADX WARNING: Removed duplicated region for block: B:9:0x0018  */
    static {
        AbstractDesugaringStrategy strategy;
        Integer apiLevel = null;
        try {
            apiLevel = readApiLevelFromBuildVersion();
            if (apiLevel == null || apiLevel.intValue() < 19) {
                if (useMimicStrategy()) {
                    strategy = new MimicDesugaringStrategy();
                } else {
                    strategy = new NullDesugaringStrategy();
                }
                STRATEGY = strategy;
                API_LEVEL = apiLevel != null ? 1 : apiLevel.intValue();
            }
            strategy = new ReuseDesugaringStrategy();
            STRATEGY = strategy;
            API_LEVEL = apiLevel != null ? 1 : apiLevel.intValue();
        } catch (Throwable e) {
            PrintStream printStream = System.err;
            String name = NullDesugaringStrategy.class.getName();
            printStream.println(new StringBuilder(String.valueOf(name).length() + 132).append("An error has occured when initializing the try-with-resources desuguring strategy. The default strategy ").append(name).append("will be used. The error is: ").toString());
            e.printStackTrace(System.err);
            strategy = new NullDesugaringStrategy();
        }
    }

    public static AbstractDesugaringStrategy getStrategy() {
        return STRATEGY;
    }

    public static void addSuppressed(Throwable receiver, Throwable suppressed) {
        STRATEGY.addSuppressed(receiver, suppressed);
    }

    public static Throwable[] getSuppressed(Throwable receiver) {
        return STRATEGY.getSuppressed(receiver);
    }

    public static void printStackTrace(Throwable receiver) {
        STRATEGY.printStackTrace(receiver);
    }

    public static void printStackTrace(Throwable receiver, PrintWriter writer) {
        STRATEGY.printStackTrace(receiver, writer);
    }

    public static void printStackTrace(Throwable receiver, PrintStream stream) {
        STRATEGY.printStackTrace(receiver, stream);
    }

    public static void closeResource(Throwable throwable, Object resource) throws Throwable {
        Throwable e;
        Throwable th;
        if (resource != null) {
            try {
                if (API_LEVEL >= 19) {
                    ((AutoCloseable) resource).close();
                    return;
                } else if (resource instanceof Closeable) {
                    ((Closeable) resource).close();
                    return;
                } else {
                    resource.getClass().getMethod("close", new Class[0]).invoke(resource, new Object[0]);
                    return;
                }
            } catch (NoSuchMethodException e2) {
                th = e2;
            } catch (SecurityException e3) {
                th = e3;
            } catch (IllegalAccessException e4) {
                e = e4;
                String valueOf = String.valueOf(resource.getClass());
                throw new AssertionError(new StringBuilder(String.valueOf(valueOf).length() + 24).append("Fail to call close() on ").append(valueOf).toString(), e);
            } catch (IllegalArgumentException e5) {
                e = e5;
                String valueOf2 = String.valueOf(resource.getClass());
                throw new AssertionError(new StringBuilder(String.valueOf(valueOf2).length() + 24).append("Fail to call close() on ").append(valueOf2).toString(), e);
            } catch (ExceptionInInitializerError e6) {
                e = e6;
                String valueOf22 = String.valueOf(resource.getClass());
                throw new AssertionError(new StringBuilder(String.valueOf(valueOf22).length() + 24).append("Fail to call close() on ").append(valueOf22).toString(), e);
            } catch (InvocationTargetException e7) {
                throw e7.getCause();
            } catch (Throwable e8) {
                if (throwable != null) {
                    addSuppressed(throwable, e8);
                    throw throwable;
                }
                throw e8;
            }
        } else {
            return;
        }
        String valueOf3 = String.valueOf(resource.getClass());
        throw new AssertionError(new StringBuilder(String.valueOf(valueOf3).length() + 32).append(valueOf3).append(" does not have a close() method.").toString(), th);
    }

    private static boolean useMimicStrategy() {
        return !Boolean.getBoolean(SYSTEM_PROPERTY_TWR_DISABLE_MIMIC);
    }

    private static Integer readApiLevelFromBuildVersion() {
        try {
            return (Integer) Class.forName(ANDROID_OS_BUILD_VERSION).getField("SDK_INT").get(null);
        } catch (Exception e) {
            System.err.println("Failed to retrieve value from android.os.Build$VERSION.SDK_INT due to the following exception.");
            e.printStackTrace(System.err);
            return null;
        }
    }
}
