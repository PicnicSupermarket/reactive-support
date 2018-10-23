package tech.picnic.rx.operators;

import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.MaybeSource;
import io.reactivex.MaybeTransformer;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class SideEffectMaybeTransformer<T> extends AbstractSideEffectTransformer<T>
    implements MaybeTransformer<T, T> {

  /**
   * @param sideEffect
   * @param errorHandler
   * @param onErrorContinue
   */
  SideEffectMaybeTransformer(
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
  public static <T> SideEffectMaybeTransformer<T> of(Function<T, Completable> sideEffect) {
    return SideEffectTransformerBuilder.<T>builder()
        .withSideEffect(sideEffect)
        .buildMaybeTransformer();
  }

  @Override
  public MaybeSource<T> apply(Maybe<T> upstream) {
    return upstream.flatMap(e -> sideEffect(e).andThen(Maybe.just(e)));
  }
}
