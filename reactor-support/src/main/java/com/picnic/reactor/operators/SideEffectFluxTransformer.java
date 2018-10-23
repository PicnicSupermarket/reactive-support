package com.picnic.reactor.operators;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** @param <T> */
public final class SideEffectFluxTransformer<T> extends AbstractSideEffectTransformer<T>
    implements Function<Flux<T>, Flux<T>> {

  SideEffectFluxTransformer(
      Function<T, Mono<Void>> sideEffect,
      Optional<BiConsumer<Throwable, T>> errorHandler,
      boolean onErrorContinue) {
    super(sideEffect, errorHandler, onErrorContinue);
  }

  /**
   * @param sideEffect
   * @param <T>
   * @return
   */
  public static <T> SideEffectFluxTransformer<T> of(Function<T, Mono<Void>> sideEffect) {
    return SideEffectTransformerBuilder.<T>builder()
        .withSideEffect(sideEffect)
        .buildFluxTransformer();
  }

  @Override
  public Flux<T> apply(Flux<T> upstream) {
    return upstream.flatMap(sideEffect());
  }
}
