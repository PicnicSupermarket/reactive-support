package tech.picnic.rx;
// Step 1: Create the consumer type that will be returned by the FlowableOperator.apply():

import io.reactivex.FlowableSubscriber;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public final class CustomSubscriber<T> implements FlowableSubscriber<T>, Subscription {

    // The downstream's Subscriber that will receive the onXXX events
    final Subscriber<? super String> downstream;

    // The connection to the upstream source that will call this class' onXXX methods
    Subscription upstream;

    // The constructor takes the downstream subscriber and usually any other parameters
    public CustomSubscriber(Subscriber<? super String> downstream) {
        this.downstream = downstream;
    }

    // In the subscription phase, the upstream sends a Subscription to this class
    // and subsequently this class has to send a Subscription to the downstream.
    // Note that relaying the upstream's Subscription directly is not allowed in RxJava
    @Override
    public void onSubscribe(Subscription s) {
        if (upstream != null) {
            s.cancel();
        } else {
            upstream = s;
            downstream.onSubscribe(this);
        }
    }

    // The upstream calls this with the next item and the implementation's
    // responsibility is to emit an item to the downstream based on the intended
    // business logic, or if it can't do so for the particular item,
    // request more from the upstream
    @Override
    public void onNext(T item) {
        String str = item.toString();
        if (str.length() < 2) {
            downstream.onNext(str);
        } else {
            upstream.request(1);
        }
    }

    // Some operators may handle the upstream's error while others
    // could just forward it to the downstream.
    @Override
    public void onError(Throwable throwable) {
        downstream.onError(throwable);
    }

    // When the upstream completes, usually the downstream should complete as well.
    @Override
    public void onComplete() {
        downstream.onComplete();
    }

    // Some operators have to intercept the downstream's request calls to trigger
    // the emission of queued items while others can simply forward the request
    // amount as is.
    @Override
    public void request(long n) {
        upstream.request(n);
    }

    // Some operators may use their own resources which should be cleaned up if
    // the downstream cancels the flow before it completed. Operators without
    // resources can simply forward the cancellation to the upstream.
    // In some cases, a canceled flag may be set by this method so that other parts
    // of this class may detect the cancellation and stop sending events
    // to the downstream.
    @Override
    public void cancel() {
        upstream.cancel();
    }
}
