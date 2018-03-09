package tech.picnic.rx;

import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import java.util.concurrent.Callable;

/**
 * A factory that reduces sources to {@link Observable#replay() replay()}.{@link
 * io.reactivex.observables.ConnectableObservable#autoConnect() autoConnect()} boilerplate.
 */
public final class AutoConnectUtil {

  AutoConnectUtil() {}

  /**
   * Transforms an Observable into an auto-connected replay Observable.
   *
   * @param <T> the Observable emission type
   * @param source the source Observable
   * @return an auto-connected replay Observable
   */
  public static <T> Observable<T> fromObservable(Observable<T> source) {
    return autoConnect(source);
  }

  /**
   * Transforms a Flowable into an auto-connected replay Flowable.
   *
   * @param <T> the Flowable emission type
   * @param source the source Flowable
   * @return an auto-connected replay Flowable
   */
  public static <T> Flowable<T> fromFlowable(Flowable<T> source) {
    return autoConnect(source);
  }

  /**
   * Transforms a Single into an auto-connected replay Observable.
   *
   * @param <T> the Single emission type
   * @param source the source Single
   * @return an auto-connected replay Observable
   */
  public static <T> Observable<T> fromSingle(Single<T> source) {
    return autoConnect(source.toObservable());
  }

  /**
   * Transforms a Maybe into an auto-connected replay Observable.
   *
   * @param <T> the Maybe emission type
   * @param source the source Maybe
   * @return an auto-connected replay Observable
   */
  public static <T> Observable<T> fromMaybe(Maybe<T> source) {
    return autoConnect(source.toObservable());
  }

  /**
   * Transforms a Callable into an auto-connected replay Observable
   *
   * @param <T> the Callable return type
   * @param source the source Callable
   * @return an auto-connected replay Observable
   */
  public static <T> Observable<T> fromCallable(Callable<? extends T> source) {
    return autoConnect(Observable.fromCallable(source));
  }

  /**
   * Transforms a single object into an auto-connected replay Observable of that object.
   *
   * @param <T> the type of the item to emit
   * @param item the item to emit
   * @return an auto-connected replay Observable
   */
  public static <T> Observable<T> just(T item) {
    return autoConnect(Observable.just(item));
  }

  private static <T> Observable<T> autoConnect(Observable<T> source) {
    return source.replay().autoConnect();
  }

  private static <T> Flowable<T> autoConnect(Flowable<T> source) {
    return source.replay().autoConnect();
  }
}
