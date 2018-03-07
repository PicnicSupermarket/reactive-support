package tech.picnic.rx;

import static io.reactivex.internal.functions.Functions.identity;

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
 *              .exponentialBackoff(500, MILLISECONDS))
 * }</pre>
 */
@NotThreadSafe
public final class RetryStrategy implements Function<Flowable<Throwable>, Flowable<?>> {
    private static final Duration STOP_RETRYING = Duration.ofMillis(-1L);
    private static final Logger LOG = LoggerFactory.getLogger(RetryStrategy.class);

    private final Predicate<Throwable> filter;
    private Flowable<Duration> backoffDelays = Flowable.just(Duration.ZERO).repeat();
    private long maxRetries = 0;
    private Scheduler backoffScheduler = Schedulers.computation();
    @Nullable private String operationName = null;

    private RetryStrategy(Predicate<Throwable> filter) {
        this.filter = filter;
    }

    /**
     * Creates a {@link RetryStrategy} which retries upon any exception.
     *
     * @return a {@link RetryStrategy} that unconditionally retries
     */
    public static RetryStrategy always() {
        return new RetryStrategy(t -> true);
    }

    /**
     * Creates a {@link RetryStrategy} which retries only upon exceptions of any of the listed
     * types, including their subtypes.
     *
     * @param classes the exception types for which retrying is desired
     * @return a {@link RetryStrategy} that triggers a retry when a given error matches any of the
     *     provided types
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static RetryStrategy ifInstanceOf(Class<? extends Throwable>... classes) {
        return new RetryStrategy(t -> Arrays.stream(classes).anyMatch(c -> c.isInstance(t)));
    }

    /**
     * Creates a {@link RetryStrategy} which retries only upon exceptions that match the given
     * predicate.
     *
     * @param predicate the predicate matching exceptions for which retrying is desired
     * @return a {@link RetryStrategy} that triggers a retry when a given error matches the
     *     predicate
     */
    public static RetryStrategy onlyIf(Predicate<Throwable> predicate) {
        return new RetryStrategy(predicate);
    }

    /**
     * Configures this {@link RetryStrategy} to use an exponentially increasing backoff delay.
     *
     * <p>The delay between successive retries is doubled each time.
     *
     * @param initialDelay the delay before the first retry
     * @return this {@link RetryStrategy}; useful for method chaining
     */
    public RetryStrategy exponentialBackoff(Duration initialDelay) {
        return customBackoff(
                Flowable.just(2)
                        .repeat()
                        .scan(initialDelay.toMillis(), (i, j) -> i * j)
                        .map(Duration::ofMillis));
    }

    /**
     * Configures this {@link RetryStrategy} to use a fixed backoff delay.
     *
     * @param delay the desired delay between retries
     * @return this {@link RetryStrategy}; useful for method chaining
     */
    public RetryStrategy fixedBackoff(Duration delay) {
        return customBackoff(Flowable.just(delay).repeat());
    }

    /**
     * Configures this {@link RetryStrategy} to use a random, limited backoff delay.
     *
     * @param maxDelay the maximum delay between retries
     * @return this {@link RetryStrategy}; useful for method chaining
     */
    public RetryStrategy randomBackoff(Duration maxDelay) {
        long maxDelayMillis = maxDelay.toMillis() + 1;
        return customBackoff(
                Flowable.fromCallable(
                                () ->
                                        Duration.ofMillis(
                                                ThreadLocalRandom.current()
                                                        .nextLong(maxDelayMillis)))
                        .repeat());
    }

    /**
     * Configures this {@link RetryStrategy} to use a custom backoff delay.
     *
     * @param delays a {@link Flowable} of custom delays; one for each attempted delay
     * @return this {@link RetryStrategy}; useful for method chaining
     */
    public RetryStrategy customBackoff(Flowable<Duration> delays) {
        this.backoffDelays = delays;
        return this;
    }

    /**
     * Configures this {@link RetryStrategy} to schedule its timer on a specific {@link Scheduler}.
     *
     * <p>By default RXJava's {@link Schedulers#computation() computation scheduler} is used.
     *
     * @param scheduler the scheduler on which to schedule the timer
     * @return this {@link RetryStrategy}; useful for method chaining
     */
    public RetryStrategy withBackoffScheduler(Scheduler scheduler) {
        this.backoffScheduler = scheduler;
        return this;
    }

    /**
     * Configures this {@link RetryStrategy} to give up retrying after a certain number of times.
     *
     * @param maxNumRetries the desired retry limit
     * @return this {@link RetryStrategy}; useful for method chaining
     */
    public RetryStrategy times(long maxNumRetries) {
        this.maxRetries = maxNumRetries;
        return this;
    }

    /**
     * Configures this {@link RetryStrategy} to log retries under the specified name.
     *
     * @param name the name to log whenever a retry is attempted
     * @return this {@link RetryStrategy}; useful for method chaining
     */
    public RetryStrategy logAs(String name) {
        this.operationName = name;
        return this;
    }

    private Flowable<Duration> getCappedDelays() {
        return (maxRetries <= 0 ? backoffDelays : backoffDelays.take(maxRetries))
                .concatWith(Flowable.just(STOP_RETRYING));
    }

    @Override
    public Flowable<?> apply(Flowable<Throwable> errors) {
        return errors.zipWith(
                        getCappedDelays(),
                        (e, d) ->
                                !d.isNegative() && filter.test(e) ? retry(e, d) : Flowable.error(e))
                .flatMap(identity());
    }

    private Flowable<?> retry(Throwable error, Duration delay) {
        if (operationName != null) {
            LOG.info("Will retry failed operation '{}': {}", operationName, error.getMessage());
        }

        return Flowable.timer(delay.toMillis(), TimeUnit.MILLISECONDS, backoffScheduler);
    }
}
