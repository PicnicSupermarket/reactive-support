package tech.picnic.rx;

import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import org.testng.annotations.Test;

@Test
public final class AutoConnectedObservableTest {
  public void testObservableAutoConnect() throws InterruptedException {
    test(
        src ->
            AutoConnectUtil.fromObservable(
                Observable.defer(() -> Observable.just(src.get()))));
  }

  public void testFlowableAutoConnect() throws InterruptedException {
    test(
        src ->
            AutoConnectUtil.fromFlowable(Flowable.defer(() -> Flowable.just(src.get())))
                .toObservable());
  }

  public void testMaybeAutoConnect() throws InterruptedException {
    test(src -> AutoConnectUtil.fromMaybe(Maybe.defer(() -> Maybe.just(src.get()))));
  }

  public void testSingleAutoConnect() throws InterruptedException {
    test(src -> AutoConnectUtil.fromSingle(Single.defer(() -> Single.just(src.get()))));
  }

  public void testCallableAutoConnect() throws InterruptedException {
    test(src -> AutoConnectUtil.fromCallable(src::get));
  }

  private static void test(Function<Supplier<Integer>, Observable<Integer>> observableFactory)
      throws InterruptedException {
    Observable<Integer> obs = observableFactory.apply(naturalNumbers());
    obs.mergeWith(obs)
        .test()
        .await()
        .assertNoErrors()
        .assertSubscribed()
        .assertValues(1, 1)
        .assertComplete();
  }

  private static Supplier<Integer> naturalNumbers() {
    AtomicInteger source = new AtomicInteger(0);
    return () -> source.incrementAndGet();
  }
}
