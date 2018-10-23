package tech.picnic.rx.operators;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.SingleTransformer;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class SideEffectSingleTransformer<T> extends AbstractSideEffectTransformer<T>
    implements SingleTransformer<T, T> {

  /**
   * @param sideEffect
   * @param errorHandler
   * @param onErrorContinue
   */
  SideEffectSingleTransformer(
      Function<T, Completable> sideEffect,
      Optional<BiConsumer<Throwable, T>> errorHandler,
      boolean onErrorContinue) {
    super(sideEffect, errorHandler, onErrorContinue);
  }

  /**
   * @param sideEffect
   * @param <T>
   * @return
   */
  public static <T> SideEffectSingleTransformer<T> of(Function<T, Completable> sideEffect) {
    return SideEffectTransformerBuilder.<T>builder()
        .withSideEffect(sideEffect)
        .buildSingleTransformer();
  }

  public SingleSource<T> apply(Single<T> upstream) {
    return upstream.flatMap(e -> sideEffect(e).andThen(Single.just(e)));
  }
}
