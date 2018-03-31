package tech.picnic.rx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.util.concurrent.MoreExecutors;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.observers.TestObserver;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.schedulers.TestScheduler;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public final class PicnicRxPluginsTest {
  private final ConcurrentMap<Context, AtomicInteger> verificationCounters =
      new ConcurrentHashMap<>();

  @BeforeAll
  static void setUp() {
    PicnicRxPlugins.configureContextPropagation(Context.createRxThreadLocal());
  }

  @AfterAll
  static void tearDown() {
    PicnicRxPlugins.unsetContextPropagation();
  }

  @Test
  public void testPropagate() {
    Observable<Integer> obs = Observable.just(1, 2, 3);

    // Without context propagation
    Context ctx1 = Context.createRandom().applyToCurrentThread();
    RxJavaPlugins.setScheduleHandler(null);
    Context ctx2 = Context.createEmpty();
    obs.subscribeOn(Schedulers.io())
        .doOnNext(i -> verifyActive(ctx2))
        .ignoreElements()
        .blockingAwait();
    verifyVerificationCounter(ctx2, 3);

    // With context propagation
    setUp();
    obs.subscribeOn(Schedulers.io())
        .doOnNext(i -> verifyActive(ctx1))
        .ignoreElements()
        .blockingAwait();
    verifyVerificationCounter(ctx1, 3);
  }

  @Test
  public void testObserveOnAnotherScheduler() {
    Observable<Integer> obs = Observable.just(1, 2, 3);

    Context ctx = Context.createRandom().applyToCurrentThread();
    obs.subscribeOn(Schedulers.io())
        .doOnNext(i -> verifyActive(ctx))
        .observeOn(Schedulers.io())
        .doOnNext(i -> verifyActive(ctx))
        .ignoreElements()
        .blockingAwait();
    verifyVerificationCounter(ctx, 6);
  }

  @Test
  public void testPropagateOnMultipleSchedulers() {
    Observable<Integer> obs = Observable.just(1, 2, 3);

    Context ctx = Context.createRandom().applyToCurrentThread();
    obs.flatMap(i -> Observable.just(i).subscribeOn(Schedulers.io()))
        .doOnNext(i -> verifyActive(ctx))
        .flatMap(i -> Observable.just(i).subscribeOn(Schedulers.computation()))
        .doOnNext(i -> verifyActive(ctx))
        .ignoreElements()
        .blockingAwait();
    verifyVerificationCounter(ctx, 6);
  }

  @Test
  public void testNotLeaking() throws InterruptedException {
    Observable<Integer> obs = Observable.just(1, 2, 3);
    Scheduler singleScheduler = Schedulers.single();

    Context ctx1 = Context.createRandom().applyToCurrentThread();
    obs.subscribeOn(singleScheduler)
        .doOnNext(i -> verifyActive(ctx1))
        .ignoreElements()
        .blockingAwait();
    verifyVerificationCounter(ctx1, 3);

    Context ctx2 = Context.createRandom().applyToCurrentThread();
    obs.subscribeOn(singleScheduler)
        .doOnNext(i -> verifyActive(ctx2))
        .ignoreElements()
        .blockingAwait();
    verifyVerificationCounter(ctx2, 3);

    // Test another calling thread with no context, should see no context
    Context ctx3 = Context.createEmpty();
    Runnable runnable =
        () ->
            obs.subscribeOn(singleScheduler)
                .doOnNext(i -> verifyActive(ctx3))
                .ignoreElements()
                .blockingAwait();
    Thread thread = new Thread(runnable);
    thread.start();
    thread.join();
    verifyVerificationCounter(ctx3, 3);

    Context ctx4 = Context.createEmpty().applyToCurrentThread();
    obs.subscribeOn(singleScheduler)
        .doOnNext(i -> verifyActive(ctx4))
        .ignoreElements()
        .blockingAwait();
    verifyVerificationCounter(ctx4, 3);
  }

  @Test
  public void testContextSwitchBeforeConsumption() {
    Observable<Integer> obs = Observable.just(1, 2, 3);

    // Context is propagated on scheduling, not on observable creation.
    Context ctx1 = Context.createRandom().applyToCurrentThread();
    Observable<Integer> obsOnScheduler = obs.subscribeOn(Schedulers.single());
    Context ctx2 = Context.createRandom().applyToCurrentThread();
    obsOnScheduler.doOnNext(i -> verifyActive(ctx2)).ignoreElements().blockingAwait();
    verifyVerificationCounter(ctx1, 0);
    verifyVerificationCounter(ctx2, 3);
  }

  @Test
  public void testPropagationOnSameThread() {
    Observable<Integer> obs = Observable.just(1, 2, 3);

    Context ctx = Context.createRandom().applyToCurrentThread();
    obs.subscribeOn(Schedulers.from(MoreExecutors.directExecutor()))
        .doOnNext(i -> verifyActive(ctx))
        .ignoreElements()
        .blockingAwait();
    verifyVerificationCounter(ctx, 3);
    verifyActive(ctx);
  }

  @Test
  public void testEmptyContextIsPropagated() {
    Observable<Integer> obs = Observable.just(1, 2, 3);

    ExecutorService es = Executors.newSingleThreadExecutor();
    try {
      es.execute(() -> Context.createRandom().applyToCurrentThread());

      Context ctx = Context.createEmpty().applyToCurrentThread();
      obs.subscribeOn(Schedulers.from(es))
          .doOnNext(i -> verifyActive(ctx))
          .ignoreElements()
          .blockingAwait();
      verifyVerificationCounter(ctx, 3);
      verifyActive(ctx);
    } finally {
      es.shutdownNow();
    }
  }

  @Test
  public void testTestSchedulerContextPropagation() throws InterruptedException {
    Context ctx = Context.createRandom().applyToCurrentThread();

    TestScheduler testScheduler = new TestScheduler();

    Observable<Integer> observable =
        Observable.just(1, 2, 3).subscribeOn(testScheduler).doOnNext(i -> verifyActive(ctx));

    TestObserver<Integer> observer = observable.subscribeWith(TestObserver.create());

    testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);

    observer.await();
    verifyVerificationCounter(ctx, 3);
  }

  @Test
  public void testDirectExecutor() {
    Context ctx = Context.createRandom().applyToCurrentThread();

    TestScheduler io = new TestScheduler();
    io.createWorker().schedule(() -> verifyActive(ctx));

    io.triggerActions();
    verifyVerificationCounter(ctx, 1);
  }

  private void verifyActive(Context ctx) {
    ctx.verifyCurrentThread();
    this.verificationCounters.computeIfAbsent(ctx, v -> new AtomicInteger()).incrementAndGet();
  }

  private void verifyVerificationCounter(Context ctx, int expected) {
    assertEquals(
        expected, this.verificationCounters.getOrDefault(ctx, new AtomicInteger()).intValue());
  }

  static final class Context {
    private static final ThreadLocal<String> threadLocalContext = new ThreadLocal<>();

    private final Optional<String> token;

    private Context(Optional<String> token) {
      this.token = token;
    }

    static Context createEmpty() {
      return new Context(Optional.empty());
    }

    static Context createRandom() {
      return new Context(Optional.of(UUID.randomUUID().toString()));
    }

    Context applyToCurrentThread() {
      if (token.isPresent()) {
        threadLocalContext.set(token.get());
      } else {
        threadLocalContext.remove();
      }

      return this;
    }

    void verifyCurrentThread() {
      assertEquals(token.orElse(null), threadLocalContext.get());
    }

    static RxThreadLocal<String> createRxThreadLocal() {
      return RxThreadLocal.from(threadLocalContext);
    }
  }
}
