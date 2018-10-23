package com.picnic.reactor.operators;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.annotations.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@Test(groups = "unit")
public final class SideEffectFluxTransformerTest {

  private static final Flux<Integer> TEST_FLOWABLE = Flux.just(0);
  private static final IllegalArgumentException EXCEPTION = new IllegalArgumentException("Boom");

  public void testNoErrors() {
    AtomicInteger sideEffectExecuted = new AtomicInteger(0);
    Mono<Void> sideEffect = Mono.fromRunnable(sideEffectExecuted::incrementAndGet);

    StepVerifier.create(Flux.just(0, 1, 2).transform(SideEffectFluxTransformer.of(v -> sideEffect)))
        .expectNext(0, 1, 2)
        .verifyComplete();
    assertEquals(sideEffectExecuted.get(), 3);
  }

  public void testEmpty() {
    Mono<Void> sideEffect = Mono.empty();
    StepVerifier.create(Flux.empty().transform(SideEffectFluxTransformer.of(v -> sideEffect)))
        .verifyComplete();
  }

  public void testEmptyWithSideEffectError() {
    Mono<Void> sideEffect = Mono.error(EXCEPTION);
    StepVerifier.create(Flux.empty().transform(SideEffectFluxTransformer.of(v -> sideEffect)))
        .verifyComplete();
  }

  public void testErrorIsPropagated() {
    Flux<String> flowable = Flux.error(EXCEPTION);
    Mono<Void> sideEffect = Mono.empty();
    StepVerifier.create(flowable.transform(SideEffectFluxTransformer.of(s -> sideEffect)))
        .verifyError(IllegalArgumentException.class);
  }

  public void testFluxError() {
    AtomicInteger counter = new AtomicInteger(0);
    Flux<Integer> flowable =
        Flux.just(0, 1).concatWith(Flux.error(EXCEPTION)).concatWith(Flux.just(3, 4));
    Mono<Void> sideEffect = Mono.fromRunnable(counter::incrementAndGet);
    StepVerifier.create(flowable.transform(SideEffectFluxTransformer.of(s -> sideEffect)))
        .expectNext(0, 1)
        .verifyError(IllegalArgumentException.class);
    assertEquals(counter.get(), 2);
  }

  public void testSideEffectError() {
    AtomicInteger counter = new AtomicInteger(0);
    Flux<Integer> flowable = Flux.just(0, 1, 2, 3, 4);
    StepVerifier.create(
            flowable.transform(
                SideEffectFluxTransformer.of(
                    (Integer value) ->
                        value > 2
                            ? Mono.error(EXCEPTION)
                            : Mono.fromRunnable(counter::incrementAndGet))))
        .expectNext(0, 1, 2)
        .verifyError(IllegalArgumentException.class);
    assertEquals(counter.get(), 3);
  }

  public void testSideEffectErrorIsPropagated() {
    Mono<Void> sideEffect = Mono.error(EXCEPTION);

    StepVerifier.create(TEST_FLOWABLE.transform(SideEffectFluxTransformer.of(v -> sideEffect)))
        .verifyError(IllegalArgumentException.class);
  }

  public void testSideEffectErrorIsIgnored() {
    StepVerifier.create(
            TEST_FLOWABLE.transform(
                SideEffectFluxTransformer.of(
                    v -> Mono.<Void>error(EXCEPTION).onErrorResume(er -> Mono.empty()))))
        .expectNext(0)
        .verifyComplete();
  }

  public void testSideEffectOnErrorContinue() {
    SideEffectFluxTransformer<Integer> sideEffect =
        SideEffectTransformerBuilder.<Integer>builder()
            .withSideEffect(v -> Mono.error(EXCEPTION))
            .onErrorContinue(true)
            .buildFluxTransformer();

    StepVerifier.create(TEST_FLOWABLE.transform(sideEffect)).expectNext(0).verifyComplete();
  }

  public void testSideEffectErrorHandler() {
    AtomicBoolean errorHandlerExecuted = new AtomicBoolean(false);
    SideEffectFluxTransformer<Integer> sideEffect =
        SideEffectTransformerBuilder.<Integer>builder()
            .withSideEffect(v -> Mono.error(EXCEPTION))
            .withErrorHandler((t, v) -> errorHandlerExecuted.set(true))
            .onErrorContinue(true)
            .buildFluxTransformer();

    StepVerifier.create(TEST_FLOWABLE.transform(sideEffect)).expectNext(0).verifyComplete();
    assertTrue(errorHandlerExecuted.get());
  }
}
