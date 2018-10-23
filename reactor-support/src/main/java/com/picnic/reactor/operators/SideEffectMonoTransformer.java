package com.picnic.reactor.operators;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import reactor.core.publisher.Mono;

/** @param <T> */
public final class SideEffectMonoTransformer<T> extends AbstractSideEffectTransformer<T>
    implements Function<Mono<T>, Mono<T>> {

  SideEffectMonoTransformer(
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
  public static <T> SideEffectMonoTransformer<T> of(Function<T, Mono<Void>> sideEffect) {
    return SideEffectTransformerBuilder.<T>builder()
        .withSideEffect(sideEffect)
        .buildMonoTransformer();
  }

  @Override
  public Mono<T> apply(Mono<T> upstream) {
    return upstream.flatMap(sideEffect());
  }
}
