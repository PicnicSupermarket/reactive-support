package tech.picnic.rx.operators;

import io.reactivex.Completable;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** @param <T> */
abstract class AbstractSideEffectTransformer<T> {

  private final Function<T, Completable> sideEffectTransformer;
  private final Optional<BiConsumer<Throwable, T>> errorHandler;
  private final boolean onErrorContinue;

  /** */
  AbstractSideEffectTransformer(
      Function<T, Completable> sideEffect,
      Optional<BiConsumer<Throwable, T>> errorHandler,
      boolean onErrorContinue) {
    this.sideEffectTransformer = sideEffect;
    this.errorHandler = errorHandler;
    this.onErrorContinue = onErrorContinue;
  }

  /** */
  Completable sideEffect(T element) {
    Completable sideEffect =
        sideEffectTransformer
            .apply(element)
            .doOnError(
                throwable -> errorHandler.ifPresent(handler -> handler.accept(throwable, element)));
    return onErrorContinue ? sideEffect : sideEffect;
  }
}
