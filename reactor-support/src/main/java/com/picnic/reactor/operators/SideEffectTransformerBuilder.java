package com.picnic.reactor.operators;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import reactor.core.publisher.Mono;

public final class SideEffectTransformerBuilder<T> {

  private Function<T, Mono<Void>> sideEffect;
  private @Nullable BiConsumer<Throwable, T> errorHandler;
  private boolean onErrorContinue = false;

  private SideEffectTransformerBuilder() {}

  public static <T> SideEffectTransformerBuilder<T> builder() {
    return new SideEffectTransformerBuilder<>();
  }

  public SideEffectTransformerBuilder<T> withSideEffect(Function<T, Mono<Void>> sideEffect) {
    this.sideEffect = sideEffect;
    return this;
  }

  public SideEffectTransformerBuilder<T> withErrorHandler(BiConsumer<Throwable, T> errorHandler) {
    this.errorHandler = errorHandler;
    return this;
  }

  public SideEffectTransformerBuilder<T> onErrorContinue(boolean onErrorContinue) {
    this.onErrorContinue = onErrorContinue;
    return this;
  }

  public SideEffectFluxTransformer<T> buildFluxTransformer() {
    requireNonNull(sideEffect);
    return new SideEffectFluxTransformer<>(
        sideEffect, Optional.ofNullable(errorHandler), onErrorContinue);
  }

  public SideEffectMonoTransformer<T> buildMonoTransformer() {
    requireNonNull(sideEffect);
    return new SideEffectMonoTransformer<>(
        sideEffect, Optional.ofNullable(errorHandler), onErrorContinue);
  }
}
