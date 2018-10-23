package tech.picnic.rx.operators;

import static org.testng.Assert.assertEquals;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.observers.TestObserver;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.annotations.Test;

@Test(groups = "unit")
public class SideEffectFlowableTransformerTest {

  private static final Flowable<Integer> TEST_FLOWABLE = Flowable.just(0);
  private static final IllegalArgumentException EXCEPTION = new IllegalArgumentException("Boom");

  public void testNoErrors() {
    Completable sideEffect = Completable.complete();
    TestObserver<Void> sideEffectObserver = sideEffect.test();

    TEST_FLOWABLE
        .compose(SideEffectFlowableTransformer.of(v -> sideEffect))
        .test()
        .assertSubscribed()
        .assertComplete();
    sideEffectObserver.assertSubscribed().assertComplete();
  }

  public void testEmpty() {
    Completable sideEffect = Completable.complete();
    Flowable.empty()
        .compose(SideEffectFlowableTransformer.of(v -> sideEffect))
        .test()
        .assertSubscribed()
        .assertNoValues()
        .assertComplete();
  }

  public void testEmptyWithSideEffectError() {
    Completable sideEffect = Completable.error(EXCEPTION);
    Flowable.empty()
        .compose(SideEffectFlowableTransformer.of(v -> sideEffect))
        .test()
        .assertSubscribed()
        .assertNoErrors()
        .assertComplete();
  }

  public void testErrorIsPropagated() {
    Flowable<String> flowable = Flowable.error(EXCEPTION);
    Completable sideEffect = Completable.complete();
    flowable
        .compose(SideEffectFlowableTransformer.of(s -> sideEffect))
        .test()
        .assertSubscribed()
        .assertError(IllegalArgumentException.class);
  }

  public void testFlowableError() {
    AtomicInteger counter = new AtomicInteger(0);
    Flowable<Integer> flowable =
        Flowable.just(0, 1).concatWith(Flowable.error(EXCEPTION)).concatWith(Flowable.just(3, 4));
    Completable sideEffect = Completable.fromAction(counter::incrementAndGet);
    flowable
        .compose(SideEffectFlowableTransformer.of(s -> sideEffect))
        .test()
        .assertSubscribed()
        .assertError(IllegalArgumentException.class);
    assertEquals(counter.get(), 2);
  }

  public void testSideEffectError() {
    AtomicInteger counter = new AtomicInteger(0);
    Flowable<Integer> flowable = Flowable.just(0, 1, 2, 3, 4);
    flowable
        .compose(
            SideEffectFlowableTransformer.of(
                (Integer value) ->
                    value > 2
                        ? Completable.error(EXCEPTION)
                        : Completable.fromAction(counter::incrementAndGet)))
        .test()
        .assertError(IllegalArgumentException.class);
    assertEquals(counter.get(), 3);
  }

  public void testSideEffectErrorIsPropagated() {
    Completable sideEffect = Completable.error(EXCEPTION);
    TestObserver<Void> sideEffectObserver = sideEffect.test();

    TEST_FLOWABLE
        .compose(SideEffectFlowableTransformer.of(v -> sideEffect))
        .test()
        .assertSubscribed()
        .assertError(IllegalArgumentException.class);
    sideEffectObserver.assertSubscribed().assertError(IllegalArgumentException.class);
  }

  public void testSideEffectErrorIsIgnored() {
    Completable sideEffect = Completable.error(EXCEPTION).onErrorComplete();
    TestObserver<Void> sideEffectObserver = sideEffect.test();

    TEST_FLOWABLE
        .compose(SideEffectFlowableTransformer.of(v -> sideEffect))
        .test()
        .assertComplete()
        .assertNoErrors();
    sideEffectObserver.assertComplete().assertNoErrors();
  }
}
