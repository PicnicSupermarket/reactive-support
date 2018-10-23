package tech.picnic.rx.operators;

import static java.util.Objects.requireNonNull;

import io.reactivex.Completable;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.annotation.Nullable;

final class SideEffectTransformerBuilder<T> {

  private Function<T, Completable> sideEffect;
  private @Nullable BiConsumer<Throwable, T> errorHandler;
  private boolean onErrorContinue = false;

  private SideEffectTransformerBuilder() {}

  public static <T> SideEffectTransformerBuilder<T> builder() {
    return new SideEffectTransformerBuilder<>();
  }

  public SideEffectTransformerBuilder<T> withSideEffect(Function<T, Completable> sideEffect) {
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

  public SideEffectFlowableTransformer<T> buildFlowableTransformer() {
    requireNonNull(sideEffect);
    return new SideEffectFlowableTransformer<>(
        sideEffect, Optional.ofNullable(errorHandler), onErrorContinue);
  }

  public SideEffectSingleTransformer<T> buildSingleTransformer() {
    requireNonNull(sideEffect);
    return new SideEffectSingleTransformer<>(
        sideEffect, Optional.ofNullable(errorHandler), onErrorContinue);
  }

  public SideEffectMaybeTransformer<T> buildMaybeTransformer() {
    requireNonNull(sideEffect);
    return new SideEffectMaybeTransformer<>(
        sideEffect, Optional.ofNullable(errorHandler), onErrorContinue);
  }
}
