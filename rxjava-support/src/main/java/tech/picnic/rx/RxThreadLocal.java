package tech.picnic.rx;

import io.reactivex.Scheduler;
import io.reactivex.functions.Function;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Helper class, used to define the propagation behaviour of thread local contexts to RxJava
 * scheduled threads.
 *
 * @param <T> the type of the to-be propagated object
 * @see PicnicRxPlugins#configureContextPropagation
 */
public final class RxThreadLocal<T> implements Function<Runnable, Runnable> {
  private final Supplier<T> extractor;
  private final Consumer<T> configurer;
  private final Consumer<T> restorer;

  private RxThreadLocal(Supplier<T> extractor, Consumer<T> configurer, Consumer<T> restorer) {
    this.extractor = extractor;
    this.configurer = configurer;
    this.restorer = restorer;
  }

  /**
   * Creates an {@link RxThreadLocal} which propagates a {@link ThreadLocal}'s value.
   *
   * @param <T> the type of thread-local object to be propagated
   * @param threadLocal the thread-local value holder which will be consulted before a {@link
   *     Runnable} is scheduled by an RxJava {@link Scheduler}; and which is updated accordingly
   *     just before the {@link Runnable} is invoked
   * @return an {@link RxThreadLocal} backed by the given {@link ThreadLocal}; to be passed to
   *     {@link PicnicRxPlugins#configureContextPropagation}.
   */
  public static <T> RxThreadLocal<T> from(ThreadLocal<T> threadLocal) {
    return from(threadLocal::get, threadLocal::set);
  }

  /**
   * Creates an {@link RxThreadLocal} with identical set and restore operations.
   *
   * @param <T> the type of the to-be propagated objects
   * @param extractor the operation using which the context to be propagated and the context to be
   *     replaced are extracted before a {@link Runnable} is scheduled by an RxJava {@link
   *     Scheduler}
   * @param setAndRestore the operation that updates and restores the relevant thread local context
   *     before and after the execution of a scheduled {@link Runnable}
   * @return an {@link RxThreadLocal} that defines the propagation behaviour of a thread local
   *     context by an RxJava {@link Scheduler}; to be passed to {@link
   *     PicnicRxPlugins#configureContextPropagation}.
   */
  public static <T> RxThreadLocal<T> from(Supplier<T> extractor, Consumer<T> setAndRestore) {
    return new RxThreadLocal<>(extractor, setAndRestore, setAndRestore);
  }

  /**
   * Creates an {@link RxThreadLocal}.
   *
   * @param <T> the type of the to-be propagated objects
   * @param extractor the operation using which the context to be propagated and the context to be
   *     replaced are extracted before a {@link Runnable} is scheduled by an RxJava {@link
   *     Scheduler}
   * @param configurer the operation that updates the relevant thread local context just before a
   *     scheduled {@link Runnable} is invoked, based on the previously extracted context
   * @param restorer the operation that restores the original thread local context following
   *     completion of the scheduled {@link Runnable}
   * @return an {@link RxThreadLocal} that defines the propagation behaviour of a thread local
   *     context by an RxJava {@link Scheduler}; to be passed to {@link
   *     PicnicRxPlugins#configureContextPropagation}.
   */
  public static <T> RxThreadLocal<T> from(
      Supplier<T> extractor, Consumer<T> configurer, Consumer<T> restorer) {
    return new RxThreadLocal<>(extractor, configurer, restorer);
  }

  @Override
  public Runnable apply(Runnable delegate) {
    T callerContext = extractor.get();
    return () -> {
      T originalContext = extractor.get();
      try {
        configurer.accept(callerContext);
        delegate.run();
      } finally {
        restorer.accept(originalContext);
      }
    };
  }
}
