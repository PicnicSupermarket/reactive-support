package tech.picnic.rx;

import static io.reactivex.internal.functions.Functions.identity;
import static java.util.Arrays.asList;
import static java.util.Collections.min;

import com.google.common.math.LongMath;
import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A custom RxJava retry utility to be used in conjunction with {@link
 * Flowable#retryWhen(Function)}.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * someFlowable
 *     .retryWhen(
 *          RetryStrategy.ifInstanceOf(TooManyRequestsException.class)
 *              .withBackoffScheduler(scheduler)
 *              .exponentialBackoff(500, MILLISECONDS)
 *              .build())
 * }</pre>
 */
public final class RetryStrategy implements Function<Flowable<Throwable>, Flowable<?>> {
  private static final Duration STOP_RETRYING = Duration.ofMillis(-1L);
  private static final Logger LOG = LoggerFactory.getLogger(RetryStrategy.class);

  private final Predicate<Throwable> filter;
  private final Flowable<Duration> backoffDelays;
  private final long maxRetries;
  private final Scheduler backoffScheduler;
  @Nullable private final String operationName;

  RetryStrategy(Builder builder) {
    this.filter = builder.filter;
    this.backoffDelays = builder.backoffDelays;
    this.maxRetries = builder.maxRetries;
    this.backoffScheduler = builder.backoffScheduler;
    this.operationName = builder.operationName;
  }

  @Override
  public Flowable<?> apply(Flowable<Throwable> errors) {
    return errors
        .zipWith(
            getCappedDelays(),
            (e, d) -> !d.isNegative() && filter.test(e) ? retry(e, d) : Flowable.error(e))
        .flatMap(identity());
  }

  private Flowable<Duration> getCappedDelays() {
    return (maxRetries <= 0 ? backoffDelays : backoffDelays.take(maxRetries))
        .concatWith(Flowable.just(STOP_RETRYING));
  }

  private Flowable<?> retry(Throwable error, Duration delay) {
    if (operationName != null) {
      LOG.info("Will retry failed operation '{}': {}", operationName, error.getMessage());
    }

    return Flowable.timer(delay.toMillis(), TimeUnit.MILLISECONDS, backoffScheduler);
  }

  /**
   * Creates a builder for the construction of a {@link RetryStrategy} which retries upon any
   * exception.
   *
   * @return a {@link RetryStrategy.Builder} for a strategy which unconditionally retries
   */
  public static Builder always() {
    return new Builder(t -> true);
  }

  /**
   * Creates a builder for the construction of a {@link RetryStrategy} which retries only upon
   * exceptions of any of the listed types, including their subtypes.
   *
   * @param classes the exception types for which retrying is desired
   * @return a {@link RetryStrategy.Builder} for a strategy which triggers a retry when a given
   *     error matches any of the provided types
   */
  @SafeVarargs
  @SuppressWarnings("varargs")
  public static Builder ifInstanceOf(Class<? extends Throwable>... classes) {
    return new Builder(t -> Arrays.stream(classes).anyMatch(c -> c.isInstance(t)));
  }

  /**
   * Creates a builder for the construction of a {@link RetryStrategy} which retries only upon
   * exceptions that match the given predicate.
   *
   * @param predicate the predicate matching exceptions for which retrying is desired
   * @return a {@link RetryStrategy.Builder} for a strategy which triggers a retry when a given
   *     error matches the predicate
   */
  public static Builder onlyIf(Predicate<Throwable> predicate) {
    return new Builder(predicate);
  }

  /** A {@link RetryStrategy} builder. */
  @NotThreadSafe
  public static final class Builder {
    private final Predicate<Throwable> filter;
    private Flowable<Duration> backoffDelays = Flowable.just(Duration.ZERO).repeat();
    private long maxRetries = 0;
    private Scheduler backoffScheduler = Schedulers.computation();
    @Nullable private String operationName = null;

    Builder(Predicate<Throwable> filter) {
      this.filter = filter;
    }

    /**
     * Configures the use of an exponentially increasing backoff delay.
     *
     * <p>The delay between successive retries is doubled each time.
     *
     * @param initialDelay the delay before the first retry
     * @return this {@link RetryStrategy.Builder}; useful for method chaining
     */
    public Builder exponentialBackoff(Duration initialDelay) {
      return customBackoff(getExponentialDelays(initialDelay));
    }

    /**
     * Configures the use of an exponentially increasing backoff delay up to a maximum.
     *
     * @param initialDelay the delay before the first retry
     * @param maxBackoffDelay the maximum delay between retries
     * @return this {@link RetryStrategy.Builder}; useful for method chaining
     */
    public Builder boundedExponentialBackoff(Duration initialDelay, Duration maxBackoffDelay) {
      return customBackoff(
          getExponentialDelays(initialDelay).map(delay -> min(asList(delay, maxBackoffDelay))));
    }

    private static Flowable<Duration> getExponentialDelays(Duration initialDelay) {
      return Flowable.just(2)
          .repeat()
          .scan(initialDelay.toMillis(), LongMath::saturatedMultiply)
          .map(Duration::ofMillis);
    }

    /**
     * Configures the use a fixed backoff delay.
     *
     * @param delay the desired delay between retries
     * @return this {@link RetryStrategy.Builder}; useful for method chaining
     */
    public Builder fixedBackoff(Duration delay) {
      return customBackoff(Flowable.just(delay).repeat());
    }

    /**
     * Configures the use of a random, limited backoff delay.
     *
     * @param maxDelay the maximum delay between retries
     * @return this {@link RetryStrategy.Builder}; useful for method chaining
     */
    public Builder randomBackoff(Duration maxDelay) {
      long maxDelayMillis = maxDelay.toMillis() + 1;
      return customBackoff(
          Flowable.fromCallable(
                  () -> Duration.ofMillis(ThreadLocalRandom.current().nextLong(maxDelayMillis)))
              .repeat());
    }

    /**
     * Configures the use of a custom backoff delay.
     *
     * @param delays a {@link Flowable} of custom delays; one for each attempted delay
     * @return this {@link RetryStrategy.Builder}; useful for method chaining
     */
    public Builder customBackoff(Flowable<Duration> delays) {
      this.backoffDelays = delays;
      return this;
    }

    /**
     * Configures retry attempts to be scheduled on a specific {@link Scheduler}.
     *
     * <p>By default RXJava's {@link Schedulers#computation() computation scheduler} is used.
     *
     * @param scheduler the scheduler on which to schedule the timer
     * @return this {@link RetryStrategy.Builder}; useful for method chaining
     */
    public Builder withBackoffScheduler(Scheduler scheduler) {
      this.backoffScheduler = scheduler;
      return this;
    }

    /**
     * Configures a bound on the number of retry attempts.
     *
     * @param maxNumRetries the desired retry limit
     * @return this {@link RetryStrategy.Builder}; useful for method chaining
     */
    public Builder times(long maxNumRetries) {
      this.maxRetries = maxNumRetries;
      return this;
    }

    /**
     * Configures retries to be logged under the specified name.
     *
     * @param name the name to log whenever a retry is attempted
     * @return this {@link RetryStrategy.Builder}; useful for method chaining
     */
    public Builder logAs(String name) {
      this.operationName = name;
      return this;
    }

    /**
     * Returns a new {@link RetryStrategy} based on the provided configuration.
     *
     * @return a {@link RetryStrategy} matching this builder's settings
     */
    public RetryStrategy build() {
      return new RetryStrategy(this);
    }
  }
}
