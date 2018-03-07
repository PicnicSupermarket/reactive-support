package tech.picnic.rx;

import static java.util.Arrays.asList;

import io.reactivex.functions.Function;
import io.reactivex.plugins.RxJavaPlugins;
import java.util.Collection;
import java.util.stream.Stream;

/** Utility class for configuring global RxJava functionality. */
public final class PicnicRxPlugins {
  private PicnicRxPlugins() {}

  /**
   * Configures all RxJava schedulers to propagate all provided {@link RxThreadLocal}s.
   *
   * <p>Example:
   *
   * <pre>{@code
   * RxThreadLocal<?> mdcPropagation =
   *         RxThreadLocal.from(
   *                 MDC::getCopyOfContextMap,
   *                 m -> {
   *                     if (m != null) {
   *                         MDC.setContextMap(m);
   *                     }
   *                 });
   * PicnicRxPlugins.configureContextPropagation(mdcPropagation);
   * }</pre>
   *
   * @param rxThreadLocals The {@link RxThreadLocal} contexts to be propagated.
   */
  public static void configureContextPropagation(RxThreadLocal<?>... rxThreadLocals) {
    configureContextPropagation(asList(rxThreadLocals));
  }

  /**
   * Configures all RxJava schedulers to propagate all provided {@link RxThreadLocal}s.
   *
   * @param rxThreadLocals the {@link RxThreadLocal} contexts to be propagated
   * @see #configureContextPropagation(RxThreadLocal...)
   */
  public static void configureContextPropagation(Collection<RxThreadLocal<?>> rxThreadLocals) {
    RxJavaPlugins.setScheduleHandler(compose(rxThreadLocals));
  }

  private static Function<Runnable, Runnable> compose(
      Collection<? extends Function<? super Runnable, ? extends Runnable>> functions) {
    @SuppressWarnings("unchecked")
    Stream<Function<Runnable, Runnable>> stream =
        (Stream<Function<Runnable, Runnable>>) functions.stream();
    return stream
        .reduce((r1, r2) -> r -> r2.apply(r1.apply(r)))
        .orElseThrow(
            () -> new IllegalArgumentException("At least one `RxThreadLocal` must be provided"));
  }

  /**
   * Unsets the RxJava schedule handler.
   *
   * <p>This stops further context propagation.
   */
  public static void unsetContextPropagation() {
    if (!RxJavaPlugins.isLockdown()) {
      RxJavaPlugins.setScheduleHandler(null);
    }
  }
}
