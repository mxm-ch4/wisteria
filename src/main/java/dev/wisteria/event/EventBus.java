package dev.wisteria.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Typed publish/subscribe. Events are dispatched by their exact class, in subscription order.
 * Safe to post from any thread (packet events arrive on the network thread).
 */
public final class EventBus {
	private final Map<Class<?>, List<Subscription<?>>> subscriptions = new ConcurrentHashMap<>();
	private volatile BiConsumer<Subscription<?>, Throwable> errorHandler = (subscription, error) -> {
	};

	/** A handler that throws must not take the game down with it; this is told about it instead. */
	public void onError(BiConsumer<Subscription<?>, Throwable> handler) {
		this.errorHandler = handler;
	}

	public <E> Subscription<E> subscribe(Class<E> type, Consumer<? super E> handler) {
		return this.subscribe(type, handler, null);
	}

	/** @param owner whoever the subscription belongs to, e.g. a module; passed back to the error handler */
	public <E> Subscription<E> subscribe(Class<E> type, Consumer<? super E> handler, @Nullable Object owner) {
		Subscription<E> subscription = new Subscription<>(type, handler, owner);
		this.subscriptions.computeIfAbsent(type, key -> new CopyOnWriteArrayList<>()).add(subscription);
		return subscription;
	}

	public void unsubscribe(Subscription<?> subscription) {
		List<Subscription<?>> list = this.subscriptions.get(subscription.type());
		if (list != null) {
			list.remove(subscription);
		}
	}

	/** Delivers the event to every subscriber of its class and returns it, so callers can read back a result. */
	@SuppressWarnings("unchecked")
	public <E> E post(E event) {
		List<Subscription<?>> list = this.subscriptions.get(event.getClass());
		if (list == null) {
			return event;
		}

		for (Subscription<?> subscription : list) {
			try {
				((Subscription<E>)subscription).handler().accept(event);
			} catch (Throwable error) {
				this.errorHandler.accept(subscription, error);
			}
		}

		return event;
	}

	public record Subscription<E>(Class<E> type, Consumer<? super E> handler, @Nullable Object owner) {
	}
}
