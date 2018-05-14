package tech.picnic.rx;

import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import org.junit.jupiter.api.Test;

public class MyTest {

    @Test
    public void test() throws InterruptedException {
        ThreadLocal<String> tl = new ThreadLocal<>();

        tl.set("d2wdqwdqwqwdqwdqwd");
        Flowable<String> f =
                Flowable.range(5, 10)
                        .doOnNext(u -> System.out.println("A " + tl.get()))
                        .observeOn(Schedulers.io())
                        .doOnNext(u -> System.out.println("B " + tl.get()))
                        .lift(new CustomOperator<Integer>(tl))
                        .doOnNext(u -> System.out.println("C " + tl.get()))
                        ;

        f.test().await().assertResult("5", "6", "7", "8", "9");

        f.test().await().assertResult("5", "6", "7", "8", "9");
    }
}
