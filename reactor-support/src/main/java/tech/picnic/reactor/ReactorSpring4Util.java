package tech.picnic.reactor;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.function.LongFunction;
import javax.annotation.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Utility class providing operations to convert Reactor reactive types into asynchronous Spring 4.x
 * web types.
 */
@SuppressWarnings("NoFunctionalReturnType")
public final class ReactorSpring4Util {
  /** A constant indicating an indefinite {@link DeferredResult} timeout. */
  public static final Duration NO_TIMEOUT = Duration.ZERO;

  private ReactorSpring4Util() {}

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
          Flux.from(publisher)
                  .collectList()
                  .map(collector)
                  .subscribe(deferredResult::setResult, deferredResult::setErrorResult)
              ::dispose);
      return deferredResult;
    };
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
    return publisherToSse(mediaType, heartbeat, heartbeatMapping, Schedulers.elastic());
  }

  @VisibleForTesting
  static <T> Function<Publisher<? extends T>, SseEmitter> publisherToSse(
      @Nullable MediaType mediaType,
      Duration heartbeat,
      LongFunction<T> heartbeatMapping,
      Scheduler scheduler) {
    return publisher ->
        Flux.<T>from(publisher)
            .publish(
                flux ->
                    flux.mergeWith(
                        Flux.interval(Duration.ofMillis(heartbeat.toMillis()), scheduler)
                            .map(heartbeatMapping::apply)
                            .takeUntilOther(flux.ignoreElements())))
            .as(publisherToSse(mediaType, NO_TIMEOUT));
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
          Flux.from(publisher)
                  .subscribe(
                      o -> {
                        try {
                          sseEmitter.send(o, mediaType);
                        } catch (IOException e) {
                          throw new UncheckedIOException(e);
                        }
                      },
                      sseEmitter::completeWithError,
                      sseEmitter::complete)
              ::dispose);
      return sseEmitter;
    };
  }
}
