package tech.picnic.rx;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.annotations.VisibleForTesting;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import java.time.Duration;
import java.util.List;
import java.util.function.LongFunction;
import javax.annotation.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Utility class providing operations to convert RxJava reactive types into asynchronous Spring 4.x
 * web types.
 */
public final class RxSpring4Util {
  /** A constant indicating an indefinite {@link DeferredResult} timeout. */
  public static final Duration NO_TIMEOUT = Duration.ZERO;

  private RxSpring4Util() {}

  /**
   * Provides a function that subscribes to a {@link Single} and exposes its emission as a {@link
   * DeferredResult}.
   *
   * @param <T> the emission and response type
   * @return a function converting a Single to a DeferredResult
   */
  public static <T> Function<Single<T>, DeferredResult<T>> singleToDeferredResult() {
    return singleToDeferredResult(new DeferredResult<T>());
  }

  /**
   * Provides a function that subscribes to a {@link Single} and exposes its emission as a {@link
   * DeferredResult}.
   *
   * @param <T> the emission and response type
   * @param timeout the deferred result's timeout
   * @return a function converting a Single to a DeferredResult
   */
  public static <T> Function<Single<T>, DeferredResult<T>> singleToDeferredResult(
      Duration timeout) {
    return singleToDeferredResult(new DeferredResult<T>(timeout.toMillis()));
  }

  private static <T> Function<Single<T>, DeferredResult<T>> singleToDeferredResult(
      DeferredResult<T> deferredResult) {
    return single -> {
      deferredResult.onTimeout(
          single.subscribe(deferredResult::setResult, deferredResult::setErrorResult)::dispose);
      return deferredResult;
    };
  }

  /**
   * Provides a function that subscribes to a {@link Maybe} and exposes its emission as a {@link
   * DeferredResult}.
   *
   * @param <T> the emission and response type
   * @return function converting a Maybe to a DeferredResult
   */
  public static <T> Function<Maybe<T>, DeferredResult<T>> maybeToDeferredResult() {
    return maybeToDeferredResult(new DeferredResult<T>());
  }

  /**
   * Provides a function that subscribes to a {@link Maybe} and exposes its emission as a {@link
   * DeferredResult}.
   *
   * @param <T> the emission and response type
   * @param timeout the deferred result's timeout
   * @return a function converting a Maybe to a DeferredResult
   */
  public static <T> Function<Maybe<T>, DeferredResult<T>> maybeToDeferredResult(Duration timeout) {
    return maybeToDeferredResult(new DeferredResult<T>(timeout.toMillis()));
  }

  private static <T> Function<Maybe<T>, DeferredResult<T>> maybeToDeferredResult(
      DeferredResult<T> deferredResult) {
    return maybe ->
        singleToDeferredResult(deferredResult)
            .apply(
                maybe.switchIfEmpty(
                    Single.defer(() -> Single.error(new ResourceNotFoundException()))));
  }

  /**
   * Provides a function that subscribes to an {@link Observable} and exposes its emissions as a
   * {@link DeferredResult deferred} iterable.
   *
   * @param <T> the type of elements emitted
   * @param <R> the deferred result type
   * @param collector a function that transforms the list of all emitted items into the final result
   * @return a function converting an Observable to a DeferredResult
   */
  public static <T, R extends Iterable<T>>
      Function<Observable<T>, DeferredResult<R>> observableToDeferredResult(
          Function<? super List<T>, R> collector) {
    return observableToDeferredResult(collector, new DeferredResult<>());
  }

  /**
   * Provides a function that subscribes to an {@link Observable} and exposes its emissions as a
   * {@link DeferredResult deferred} iterable.
   *
   * @param <T> the type of elements emitted
   * @param <R> the deferred result type
   * @param collector a function that transforms the list of all emitted items into the final result
   * @param timeout the deferred result's timeout
   * @return a function converting an Observable to a DeferredResult
   */
  public static <T, R extends Iterable<T>>
      Function<Observable<T>, DeferredResult<R>> observableToDeferredResult(
          Function<? super List<T>, R> collector, Duration timeout) {
    return observableToDeferredResult(collector, new DeferredResult<>(timeout.toMillis()));
  }

  private static <T, R extends Iterable<T>>
      Function<Observable<T>, DeferredResult<R>> observableToDeferredResult(
          Function<? super List<T>, R> collector, DeferredResult<R> deferredResult) {
    return observable -> {
      deferredResult.onTimeout(
          observable
                  .toList()
                  .map(collector)
                  .subscribe(deferredResult::setResult, deferredResult::setErrorResult)
              ::dispose);
      return deferredResult;
    };
  }

  /**
   * Provides a function that subscribes to a {@link Publisher} and exposes its emissions as a
   * {@link DeferredResult deferred} iterable.
   *
   * @param <T> the type of elements emitted
   * @param <R> the deferred result type
   * @param collector a function that transforms the list of all emitted items into the final result
   * @return a function converting a Publisher to a DeferredResult
   */
  public static <T, R extends Iterable<T>>
      Function<Publisher<T>, DeferredResult<R>> publisherToDeferredResult(
          Function<? super List<T>, R> collector) {
    return publisherToDeferredResult(collector, new DeferredResult<>());
  }

  /**
   * Provides a function that subscribes to a {@link Publisher} and exposes its emissions as a
   * {@link DeferredResult deferred} iterable.
   *
   * @param <T> the type of elements emitted
   * @param <R> the deferred result type
   * @param collector a function that transforms the list of all emitted items into the final result
   * @param timeout the deferred result's timeout
   * @return a function converting a Publisher to a DeferredResult
   */
  public static <T, R extends Iterable<T>>
      Function<Publisher<T>, DeferredResult<R>> publisherToDeferredResult(
          Function<? super List<T>, R> collector, Duration timeout) {
    return publisherToDeferredResult(collector, new DeferredResult<>(timeout.toMillis()));
  }

  private static <T, R extends Iterable<T>>
      Function<Publisher<T>, DeferredResult<R>> publisherToDeferredResult(
          Function<? super List<T>, R> collector, DeferredResult<R> deferredResult) {
    return publisher -> {
      deferredResult.onTimeout(
          Flowable.fromPublisher(publisher)
                  .toList()
                  .map(collector)
                  .subscribe(deferredResult::setResult, deferredResult::setErrorResult)
              ::dispose);
      return deferredResult;
    };
  }

  /**
   * Provides a function that subscribes to a {@link Completable} and communicates its completion
   * through a {@link DeferredResult deferred} null value.
   *
   * @return a function converting a Completable to a DeferredResult
   */
  public static Function<Completable, DeferredResult<Void>> completableToDeferredResult() {
    return completableToDeferredResult(new DeferredResult<>());
  }

  /**
   * Provides a function that subscribes to a {@link Completable} and communicates its completion
   * through a {@link DeferredResult deferred} null value.
   *
   * @param timeout the deferred result's timeout
   * @return a function converting a Completable to a DeferredResult
   */
  public static Function<Completable, DeferredResult<Void>> completableToDeferredResult(
      Duration timeout) {
    return completableToDeferredResult(new DeferredResult<>(timeout.toMillis()));
  }

  private static Function<Completable, DeferredResult<Void>> completableToDeferredResult(
      DeferredResult<Void> deferredResult) {
    return completable -> {
      deferredResult.onTimeout(
          completable.subscribe(
                  () -> deferredResult.setResult(null), deferredResult::setErrorResult)
              ::dispose);
      return deferredResult;
    };
  }

  /**
   * Provides a function that subscribes to an {@link Observable} and exposes its emissions as
   * {@link SseEmitter server-sent events}.
   *
   * @return a function converting an Observable to an SseEmitter
   */
  public static Function<Observable<?>, SseEmitter> observableToSse() {
    return observableToSse(null);
  }

  /**
   * Provides a function that subscribes to an {@link Observable} and exposes its emissions as
   * {@link SseEmitter server-sent events}.
   *
   * @param mediaType the media type of individual events; null indicates the context's default
   *     media type
   * @return a function converting an Observable to an SseEmitter
   */
  public static Function<Observable<?>, SseEmitter> observableToSse(@Nullable MediaType mediaType) {
    return observableToSse(mediaType, new SseEmitter());
  }

  /**
   * Provides a function that subscribes to an {@link Observable} and exposes its emissions as
   * {@link SseEmitter server-sent events}.
   *
   * @param mediaType the media type of individual events; null indicates the context's default
   *     media type
   * @param timeout the SseEmitter's timeout
   * @return a function converting an Observable to an SseEmitter
   */
  public static Function<Observable<?>, SseEmitter> observableToSse(
      @Nullable MediaType mediaType, Duration timeout) {
    return observableToSse(mediaType, new SseEmitter(timeout.toMillis()));
  }

  private static Function<Observable<?>, SseEmitter> observableToSse(
      @Nullable MediaType mediaType, SseEmitter sseEmitter) {
    return observable ->
        publisherToSse(mediaType, sseEmitter)
            .apply(observable.toFlowable(BackpressureStrategy.BUFFER));
  }

  /**
   * Provides a function that subscribes to a {@link Publisher} and exposes its emissions as {@link
   * SseEmitter server-sent events}.
   *
   * @return a function converting a Publisher to an SseEmitter
   */
  public static Function<Publisher<?>, SseEmitter> publisherToSse() {
    return publisherToSse((MediaType) null);
  }

  /**
   * Provides a function that subscribes to a {@link Publisher} and exposes its emissions as {@link
   * SseEmitter server-sent events}.
   *
   * <p>This method intersperses the server-sent events corresponding to the Publisher's emissions
   * with keep-alive events, if necessary.
   *
   * @param <T> the type of elements emitted
   * @param heartbeat the delay between "keep alive" events
   * @param heartbeatMapping the function producing keep alive events given their ordinal
   * @return a function converting a Publisher to an SseEmitter
   */
  public static <T> Function<Publisher<? extends T>, SseEmitter> publisherToSse(
      Duration heartbeat, LongFunction<T> heartbeatMapping) {
    return publisherToSse(null, heartbeat, heartbeatMapping);
  }

  /**
   * Provides a function that subscribes to a {@link Publisher} and exposes its emissions as {@link
   * SseEmitter server-sent events}.
   *
   * <p>This method intersperses the server-sent events corresponding to the Publisher's emissions
   * with keep-alive events, if necessary.
   *
   * @param <T> the type of elements emitted
   * @param mediaType the media type of individual events; null indicates the context's default
   *     media type
   * @param heartbeat the delay between "keep alive" events
   * @param heartbeatMapping the function producing keep alive events given their ordinal
   * @return a function converting a Publisher to an SseEmitter
   */
  public static <T> Function<Publisher<? extends T>, SseEmitter> publisherToSse(
      @Nullable MediaType mediaType, Duration heartbeat, LongFunction<T> heartbeatMapping) {
    return publisherToSse(mediaType, heartbeat, heartbeatMapping, Schedulers.computation());
  }

  @VisibleForTesting
  static <T> Function<Publisher<? extends T>, SseEmitter> publisherToSse(
      @Nullable MediaType mediaType,
      Duration heartbeat,
      LongFunction<T> heartbeatMapping,
      Scheduler scheduler) {
    return publisher ->
        Flowable.<T>fromPublisher(publisher)
            .publish(
                flowable ->
                    flowable.mergeWith(
                        Flowable.interval(heartbeat.toMillis(), MILLISECONDS, scheduler)
                            .map(heartbeatMapping::apply)
                            .takeUntil(flowable.ignoreElements().toFlowable())))
            .to(publisherToSse(mediaType, NO_TIMEOUT));
  }

  /**
   * Provides a function that subscribes to a {@link Publisher} and exposes its emissions as {@link
   * SseEmitter server-sent events}.
   *
   * @param mediaType the media type of individual events; null indicates the context's default
   *     media type
   * @return a function converting a Publisher to an SseEmitter
   */
  public static Function<Publisher<?>, SseEmitter> publisherToSse(@Nullable MediaType mediaType) {
    return publisherToSse(mediaType, new SseEmitter());
  }

  /**
   * Provides a function that subscribes to a {@link Publisher} and exposes its emissions as {@link
   * SseEmitter server-sent events}.
   *
   * @param timeout the SseEmitter's timeout
   * @return a function converting a Publisher to an SseEmitter
   */
  public static Function<Publisher<?>, SseEmitter> publisherToSse(Duration timeout) {
    return publisherToSse(null, timeout);
  }

  /**
   * Provides a function that subscribes to a {@link Publisher} and exposes its emissions as {@link
   * SseEmitter server-sent events}.
   *
   * @param mediaType the media type of individual events; null indicates the context's default
   *     media type
   * @param timeout the SseEmitter's timeout
   * @return a function converting a Publisher to an SseEmitter
   */
  public static Function<Publisher<?>, SseEmitter> publisherToSse(
      @Nullable MediaType mediaType, Duration timeout) {
    return publisherToSse(mediaType, new SseEmitter(timeout.toMillis()));
  }

  private static Function<Publisher<?>, SseEmitter> publisherToSse(
      @Nullable MediaType mediaType, SseEmitter sseEmitter) {
    return publisher -> {
      sseEmitter.onTimeout(
          Flowable.fromPublisher(publisher)
                  .subscribe(
                      o -> sseEmitter.send(o, mediaType),
                      sseEmitter::completeWithError,
                      sseEmitter::complete)
              ::dispose);
      return sseEmitter;
    };
  }
}
