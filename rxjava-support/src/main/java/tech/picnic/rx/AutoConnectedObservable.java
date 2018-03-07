package tech.picnic.rx;

import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Single;
import java.util.concurrent.Callable;

/**
 * A custom {@link Observable} that reduces {@link Observable#replay() replay()}.{@link
 * io.reactivex.observables.ConnectableObservable#autoConnect() autoConnect()} boilerplate.
 *
 * @param <T> The type of the items emitted by this observable
 */
public final class AutoConnectedObservable<T> extends Observable<T> {
  private final Observable<T> source;

  private AutoConnectedObservable(Observable<T> source) {
    this.source = source.compose(upstream -> upstream.replay().autoConnect());
  }

  /**
   * Transforms an Observable into an auto-connected replay Observable.
   *
   * @param <T> the Observable emission type
   * @param source the source Observable
   * @return an auto-connected replay Observable
   */
  public static <T> Observable<T> fromObservable(Observable<T> source) {
    return new AutoConnectedObservable<>(source);
  }

  /**
   * Transforms a Flowable into an auto-connected replay Observable.
   *
   * @param <T> the Flowable emission type
   * @param source the source Flowable
   * @return an auto-connected replay Observable
   */
  public static <T> Observable<T> fromFlowable(Flowable<T> source) {
    return new AutoConnectedObservable<>(source.toObservable());
  }

  /**
   * Transforms a Single into an auto-connected replay Observable.
   *
   * @param <T> the Single emission type
   * @param source the source Single
   * @return an auto-connected replay Observable
   */
  public static <T> Observable<T> fromSingle(Single<T> source) {
    return new AutoConnectedObservable<>(source.toObservable());
  }

  /**
   * Transforms a Maybe into an auto-connected replay Observable.
   *
   * @param <T> the Maybe emission type
   * @param source the source Maybe
   * @return an auto-connected replay Observable
   */
  public static <T> Observable<T> fromMaybe(Maybe<T> source) {
    return new AutoConnectedObservable<>(source.toObservable());
  }

  /**
   * Transforms a Callable into an auto-connected replay Observable
   *
   * @param <T> the Callable return type
   * @param source the source Callable
   * @return an auto-connected replay Observable
   */
  public static <T> AutoConnectedObservable<T> fromCallable(Callable<? extends T> source) {
    return new AutoConnectedObservable<>(Observable.fromCallable(source));
  }

  /**
   * Transforms a single object into an auto-connected replay Observable of that object.
   *
   * @param <T> the type of the item to emit
   * @param item the item to emit
   * @return an auto-connected replay Observable
   */
  public static <T> Observable<T> just(T item) {
    return new AutoConnectedObservable<>(Observable.just(item));
  }

  @Override
  protected void subscribeActual(Observer<? super T> observer) {
    source.subscribe(observer);
  }
}
