package tech.picnic.rx;

import io.reactivex.FlowableOperator;
import org.reactivestreams.Subscriber;

// Step 2: Create a class that implements the FlowableOperator interface and
//         returns the custom consumer type from above in its apply() method.
//         Such class may define additional parameters to be submitted to
//         the custom consumer type.

final class CustomOperator<T> implements FlowableOperator<String, T> {
    private final ThreadLocal<String> tl;

    CustomOperator(ThreadLocal<String> tl) {
        this.tl = tl;
    }

    @Override
    public Subscriber<? super T> apply(Subscriber<? super String> upstream) {
        System.out.println("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX " + tl.get());
        return new CustomSubscriber<>(upstream);
    }
}
