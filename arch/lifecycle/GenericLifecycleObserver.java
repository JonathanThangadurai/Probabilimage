package android.arch.lifecycle;

import android.arch.lifecycle.Lifecycle.Event;

public interface GenericLifecycleObserver extends LifecycleObserver {
    Object getReceiver();

    void onStateChanged(LifecycleOwner lifecycleOwner, Event event);
}
