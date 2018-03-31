package tech.picnic.rx;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.reactivex.Flowable;
import io.reactivex.schedulers.TestScheduler;
import io.reactivex.subscribers.TestSubscriber;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public final class RetryStrategyTest {
  @Test
  public void testOnlyIf() throws Exception {
    errorSource(2)
        .retryWhen(
            RetryStrategy.onlyIf(
                    e -> e instanceof RuntimeException && "Error #1".equals(e.getMessage()))
                .build())
        .test()
        .await()
        .assertError(RuntimeException.class)
        .assertErrorMessage("Error #2");
    errorSource(1)
        .retryWhen(RetryStrategy.onlyIf(e -> e instanceof Error).build())
        .test()
        .await()
        .assertError(RuntimeException.class)
        .assertErrorMessage("Error #1");
  }

  @Test
  public void testExponentialBackoff() {
    AtomicInteger retries = new AtomicInteger();
    TestScheduler scheduler = new TestScheduler();
    TestSubscriber<Integer> test =
        errorSource(10)
            .doOnSubscribe(d -> retries.incrementAndGet())
            .retryWhen(
                RetryStrategy.always()
                    .withBackoffScheduler(scheduler)
                    .exponentialBackoff(Duration.ofMillis(100))
                    .build())
            .test();
    test.assertNotTerminated().assertNoValues();
    for (int i = 1, d = 100, t = 0; i <= 10; ++i, t += d, d *= 2) {
      scheduler.advanceTimeTo(t, MILLISECONDS);
      test.assertNotTerminated().assertNoValues();
      assertEquals(i, retries.get());
      scheduler.advanceTimeBy(d - 1, MILLISECONDS);
      test.assertNotTerminated().assertNoValues();
      assertEquals(i, retries.get());
    }
    scheduler.advanceTimeBy(1, MILLISECONDS);
    test.assertValue(11).assertComplete();
  }

  @Test
  public void testBoundedExponentialBackoff() {
    AtomicInteger retries = new AtomicInteger();
    TestScheduler scheduler = new TestScheduler();
    TestSubscriber<Integer> test =
        errorSource(10)
            .doOnSubscribe(d -> retries.incrementAndGet())
            .retryWhen(
                RetryStrategy.always()
                    .withBackoffScheduler(scheduler)
                    .boundedExponentialBackoff(Duration.ofMillis(100), Duration.ofMillis(3200))
                    .build())
            .test();
    test.assertNotTerminated().assertNoValues();
    for (int i = 1, d = 100, t = 0; i <= 5; ++i, t += d, d *= 2) {
      scheduler.advanceTimeTo(t, MILLISECONDS);
      test.assertNotTerminated().assertNoValues();
      assertEquals(retries.get(), i);
      scheduler.advanceTimeBy(d - 1, MILLISECONDS);
      test.assertNotTerminated().assertNoValues();
      assertEquals(retries.get(), i);
    }

    for (int i = 6; i <= 10; i++) {
      scheduler.advanceTimeBy(3200, MILLISECONDS);
      test.assertNotTerminated().assertNoValues();
      assertEquals(retries.get(), i);
    }

    scheduler.advanceTimeBy(1, MILLISECONDS);
    test.assertValue(11).assertComplete();
  }

  @Test
  public void testFixedBackoff() {
    TestScheduler scheduler = new TestScheduler();
    TestSubscriber<Integer> test =
        errorSource(10)
            .retryWhen(
                RetryStrategy.always()
                    .withBackoffScheduler(scheduler)
                    .fixedBackoff(Duration.ofMillis(500))
                    .build())
            .test();
    scheduler.advanceTimeTo(4999, MILLISECONDS);
    test.assertNotTerminated().assertNoValues();
    scheduler.advanceTimeTo(5000, MILLISECONDS);
    test.assertValue(11).assertComplete();
  }

  @Test
  public void testCustomBackoff() {
    TestScheduler scheduler = new TestScheduler();
    TestSubscriber<Integer> test =
        errorSource(10)
            .retryWhen(
                RetryStrategy.always()
                    .withBackoffScheduler(scheduler)
                    .customBackoff(Flowable.just(Duration.ofMillis(10), Duration.ofMillis(20)))
                    .build())
            .test();
    scheduler.advanceTimeTo(29, MILLISECONDS);
    test.assertNotTerminated().assertNoValues();
    scheduler.advanceTimeTo(30, MILLISECONDS);
    test.assertError(RuntimeException.class).assertErrorMessage("Error #3");
  }

  @Test
  public void testTimes() throws Exception {
    errorSource(10)
        .retryWhen(RetryStrategy.always().times(5).build())
        .test()
        .await()
        .assertError(RuntimeException.class)
        .assertErrorMessage("Error #6");
    errorSource(10)
        .retryWhen(RetryStrategy.always().times(10).build())
        .test()
        .await()
        .assertValue(11)
        .assertComplete();
  }
  /**
   * Returns a {@link Flowable} which yields either an error or an integer when subscribed to.
   *
   * @param errors the number of initial subscriptions for which an error should be thrown
   * @return the described {@link Flowable}
   */
  private static Flowable<Integer> errorSource(int errors) {
    AtomicInteger count = new AtomicInteger();
    return Flowable.fromCallable(count::incrementAndGet)
        .map(
            i -> {
              if (i <= errors) {
                throw new RuntimeException("Error #" + i);
              }
              return i;
            });
  }
}
