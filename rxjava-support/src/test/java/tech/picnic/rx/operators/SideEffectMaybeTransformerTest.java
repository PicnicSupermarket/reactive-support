package tech.picnic.rx.operators;

import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.observers.TestObserver;
import org.testng.annotations.Test;

@Test(groups = "unit")
public class SideEffectMaybeTransformerTest {

  private static final Maybe<Integer> TEST_MAYBE = Maybe.just(0);
  private static final IllegalArgumentException EXCEPTION = new IllegalArgumentException("Boom");

  public void testNoErrors() {
    Completable sideEffect = Completable.complete();
    TestObserver<Void> sideEffectObserver = sideEffect.test();

    TEST_MAYBE
        .compose(SideEffectMaybeTransformer.of(v -> sideEffect))
        .test()
        .assertSubscribed()
        .assertComplete();
    sideEffectObserver.assertSubscribed().assertComplete();
  }

  public void testEmpty() {
    Completable sideEffect = Completable.complete();
    Maybe.empty()
        .compose(SideEffectMaybeTransformer.of(v -> sideEffect))
        .test()
        .assertSubscribed()
        .assertNoValues()
        .assertComplete();
  }

  public void testEmptyWithSideEffectError() {
    Completable sideEffect = Completable.error(EXCEPTION);
    Maybe.empty()
        .compose(SideEffectMaybeTransformer.of(v -> sideEffect))
        .test()
        .assertSubscribed()
        .assertNoErrors()
        .assertComplete();
  }

  public void testErrorIsPropagated() {
    Maybe<String> maybe = Maybe.error(EXCEPTION);
    Completable sideEffect = Completable.complete();
    maybe
        .compose(SideEffectMaybeTransformer.of(v -> sideEffect))
        .test()
        .assertSubscribed()
        .assertError(IllegalArgumentException.class);
  }

  public void testSideEffectErrorIsPropagated() {
    Completable sideEffect = Completable.error(EXCEPTION);
    TestObserver<Void> sideEffectObserver = sideEffect.test();

    TEST_MAYBE
        .compose(SideEffectMaybeTransformer.of(v -> sideEffect))
        .test()
        .assertSubscribed()
        .assertError(IllegalArgumentException.class);
    sideEffectObserver.assertSubscribed().assertError(IllegalArgumentException.class);
  }

  public void testSideEffectErrorIsIgnored() {
    Completable sideEffect = Completable.error(EXCEPTION).onErrorComplete();
    TestObserver<Void> sideEffectObserver = sideEffect.test();

    TEST_MAYBE
        .compose(SideEffectMaybeTransformer.of(v -> sideEffect))
        .test()
        .assertComplete()
        .assertNoErrors();
    sideEffectObserver.assertComplete().assertNoErrors();
  }
}
