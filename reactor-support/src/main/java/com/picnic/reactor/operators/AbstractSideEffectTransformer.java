package com.picnic.reactor.operators;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import reactor.core.publisher.Mono;

/** @param <T> */
abstract class AbstractSideEffectTransformer<T> {

  private final Function<T, Mono<Void>> sideEffectTransformer;
  private final Optional<BiConsumer<Throwable, T>> errorHandler;
  private final boolean onErrorContinue;

  /** */
  AbstractSideEffectTransformer(
      Function<T, Mono<Void>> sideEffect,
      Optional<BiConsumer<Throwable, T>> errorHandler,
      boolean onErrorContinue) {
    this.sideEffectTransformer = sideEffect;
    this.errorHandler = errorHandler;
    this.onErrorContinue = onErrorContinue;
  }

  /** */
  Function<T, Mono<? extends T>> sideEffect() {
    return element -> {
      Mono<T> sideEffect =
          sideEffectTransformer
              .apply(element)
              .thenReturn(element)
              .doOnError(
                  throwable ->
                      errorHandler.ifPresent(handler -> handler.accept(throwable, element)));
      return onErrorContinue ? sideEffect.onErrorReturn(element) : sideEffect;
    };
  }
}
