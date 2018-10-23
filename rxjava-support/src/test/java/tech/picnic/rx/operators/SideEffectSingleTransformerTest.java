package tech.picnic.rx.operators;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.testng.annotations.Test;

@Test(groups = "unit")
public class SideEffectSingleTransformerTest {

  private static final Single<Integer> TEST_SINGLE = Single.just(0);
  private static final IllegalArgumentException EXCEPTION = new IllegalArgumentException("Boom");

  public void testNoErrors() {
    Completable sideEffect = Completable.complete();
    TestObserver<Void> sideEffectObserver = sideEffect.test();

    TEST_SINGLE
        .compose(SideEffectSingleTransformer.of(v -> sideEffect))
        .test()
        .assertSubscribed()
        .assertComplete();
    sideEffectObserver.assertSubscribed().assertComplete();
  }

  public void testErrorIsPropagated() {
    Single<String> single = Single.error(EXCEPTION);
    Completable sideEffect = Completable.complete();
    single
        .compose(SideEffectSingleTransformer.of(s -> sideEffect))
        .test()
        .assertSubscribed()
        .assertError(IllegalArgumentException.class);
  }

  public void testSideEffectErrorIsPropagated() {
    Completable sideEffect = Completable.error(EXCEPTION);
    TestObserver<Void> sideEffectObserver = sideEffect.test();

    TEST_SINGLE
        .compose(SideEffectSingleTransformer.of(v -> sideEffect))
        .test()
        .assertSubscribed()
        .assertError(IllegalArgumentException.class);
    sideEffectObserver.assertSubscribed().assertError(IllegalArgumentException.class);
  }

  public void testSideEffectErrorIsIgnored() {
    Completable sideEffect = Completable.error(EXCEPTION).onErrorComplete();
    TestObserver<Void> sideEffectObserver = sideEffect.test();

    TEST_SINGLE
        .compose(SideEffectSingleTransformer.of(v -> sideEffect))
        .test()
        .assertComplete()
        .assertNoErrors();
    sideEffectObserver.assertComplete().assertNoErrors();
  }
}
