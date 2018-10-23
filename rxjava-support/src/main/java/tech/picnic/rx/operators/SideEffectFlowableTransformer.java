package tech.picnic.rx.operators;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.FlowableTransformer;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.reactivestreams.Publisher;

public class SideEffectFlowableTransformer<T> extends AbstractSideEffectTransformer<T>
    implements FlowableTransformer<T, T> {

  /**
   * @param sideEffect
   * @param errorHandler
   * @param onErrorContinue
   */
  SideEffectFlowableTransformer(
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
  public static <T> SideEffectFlowableTransformer<T> of(Function<T, Completable> sideEffect) {
    return SideEffectTransformerBuilder.<T>builder()
        .withSideEffect(sideEffect)
        .buildFlowableTransformer();
  }

  @Override
  public Publisher<T> apply(Flowable<T> upstream) {
    return upstream.flatMap(e -> sideEffect(e).andThen(Flowable.just(e)));
  }
}
