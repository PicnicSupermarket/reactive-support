package com.picnic.reactor.operators;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@Test(groups = "unit")
public final class SideEffectMonoTransformerTest {

  private static final Mono<Integer> TEST_MONO = Mono.just(0);
  private static final IllegalArgumentException EXCEPTION = new IllegalArgumentException("Boom");

  public void testNoErrors() {
    AtomicBoolean sideEffectExecuted = new AtomicBoolean(false);
    Mono<Void> sideEffect = Mono.fromRunnable(() -> sideEffectExecuted.set(true));

    StepVerifier.create(TEST_MONO.transform(SideEffectMonoTransformer.of(v -> sideEffect)))
        .expectNext(0)
        .verifyComplete();
    assertTrue(sideEffectExecuted.get());
  }

  public void testEmpty() {
    AtomicBoolean sideEffectExecuted = new AtomicBoolean(false);
    Mono<Void> sideEffect = Mono.fromRunnable(() -> sideEffectExecuted.set(true));

    StepVerifier.create(Mono.empty().transform(SideEffectMonoTransformer.of(v -> sideEffect)))
        .verifyComplete();
    assertFalse(sideEffectExecuted.get());
  }

  public void testEmptyWithSideEffectError() {
    Mono<Void> sideEffect = Mono.error(EXCEPTION);
    StepVerifier.create(Mono.empty().transform(SideEffectMonoTransformer.of(v -> sideEffect)))
        .verifyComplete();
  }

  public void testErrorIsPropagated() {
    Mono<String> error = Mono.error(EXCEPTION);
    StepVerifier.create(error.transform(SideEffectMonoTransformer.of(v -> Mono.empty())))
        .verifyError(IllegalArgumentException.class);
  }

  public void testSideEffectError() {
    StepVerifier.create(
            TEST_MONO.transform(SideEffectMonoTransformer.of(v -> Mono.error(EXCEPTION))))
        .verifyError(IllegalArgumentException.class);
  }

  public void testSideEffectErrorIsPropagated() {
    Mono<Void> sideEffect = Mono.error(EXCEPTION);

    StepVerifier.create(TEST_MONO.transform(SideEffectMonoTransformer.of(v -> sideEffect)))
        .verifyError(IllegalArgumentException.class);
  }

  public void testSideEffectOnErrorContinue() {
    SideEffectMonoTransformer<Integer> sideEffect =
        SideEffectTransformerBuilder.<Integer>builder()
            .withSideEffect(v -> Mono.error(EXCEPTION))
            .onErrorContinue(true)
            .buildMonoTransformer();

    StepVerifier.create(TEST_MONO.transform(sideEffect)).expectNext(0).verifyComplete();
  }

  public void testSideEffectErrorHandler() {
    AtomicBoolean errorHandlerExecuted = new AtomicBoolean(false);
    SideEffectMonoTransformer<Integer> sideEffect =
        SideEffectTransformerBuilder.<Integer>builder()
            .withSideEffect(v -> Mono.error(EXCEPTION))
            .withErrorHandler((t, v) -> errorHandlerExecuted.set(true))
            .onErrorContinue(true)
            .buildMonoTransformer();

    StepVerifier.create(TEST_MONO.transform(sideEffect)).expectNext(0).verifyComplete();
    assertTrue(errorHandlerExecuted.get());
  }
}
