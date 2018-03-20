package tech.picnic.rx;

import static java.util.Arrays.asList;

import com.google.common.collect.Lists;
import com.google.errorprone.annotations.Var;
import io.reactivex.functions.Function;
import io.reactivex.plugins.RxJavaPlugins;
import java.util.List;

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
   *                     } else {
   *                         MDC.clear();
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
  public static void configureContextPropagation(List<RxThreadLocal<?>> rxThreadLocals) {
    RxJavaPlugins.setScheduleHandler(compose(rxThreadLocals));
  }

  private static Function<Runnable, Runnable> compose(
      List<? extends Function<? super Runnable, ? extends Runnable>> functions) {
    return r -> {
      @Var Runnable composition = r;
      for (Function<? super Runnable, ? extends Runnable> f : Lists.reverse(functions)) {
        composition = f.apply(composition);
      }
      return composition;
    };
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
