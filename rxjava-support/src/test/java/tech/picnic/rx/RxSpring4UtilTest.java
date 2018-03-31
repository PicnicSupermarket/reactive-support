package tech.picnic.rx;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static tech.picnic.rx.RxSpring4Util.completableToDeferredResult;
import static tech.picnic.rx.RxSpring4Util.maybeToDeferredResult;
import static tech.picnic.rx.RxSpring4Util.observableToDeferredResult;
import static tech.picnic.rx.RxSpring4Util.observableToSse;
import static tech.picnic.rx.RxSpring4Util.publisherToDeferredResult;
import static tech.picnic.rx.RxSpring4Util.publisherToSse;
import static tech.picnic.rx.RxSpring4Util.singleToDeferredResult;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.common.collect.ImmutableList;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.schedulers.TestScheduler;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@TestInstance(Lifecycle.PER_CLASS)
public final class RxSpring4UtilTest {
  private final TestScheduler testScheduler = new TestScheduler();

  @SuppressWarnings("NullAway")
  private MockMvc mockMvc;

  @BeforeAll
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TestController(testScheduler))
            .alwaysExpect(request().asyncStarted())
            .setMessageConverters(
                new StringHttpMessageConverter(),
                new MappingJackson2HttpMessageConverter(),
                new MappingJackson2XmlHttpMessageConverter())
            .build();
  }

  @Test
  public void testSingleToDeferredResult() throws Exception {
    mockMvc
        .perform(get("/singleToDeferredResult?value=foo"))
        .andExpect(request().asyncResult("foo"));
    mockMvc
        .perform(get("/singleToDeferredResult?value=error"))
        .andExpect(request().asyncResult(instanceOf(IllegalArgumentException.class)));
  }

  @Test
  public void testMaybeToDeferredResult() throws Exception {
    assertNull(mockMvc.perform(get("/maybeToDeferredResult")).andReturn().getAsyncResult());
    mockMvc
        .perform(get("/maybeToDeferredResult?value=foo"))
        .andExpect(request().asyncResult("foo"));
    mockMvc
        .perform(get("/maybeToDeferredResult?value=error"))
        .andExpect(request().asyncResult(instanceOf(IllegalArgumentException.class)));
  }

  @Test
  public void testObservableToDeferredResult() throws Exception {
    mockMvc
        .perform(get("/observableToDeferredResult?value=foo"))
        .andExpect(request().asyncResult(ImmutableList.of("foo")));
    mockMvc
        .perform(get("/observableToDeferredResult?value=bar&repeat=2"))
        .andExpect(request().asyncResult(ImmutableList.of("bar", "bar")));
    mockMvc
        .perform(get("/observableToDeferredResult?value=baz&repeat=0"))
        .andExpect(request().asyncResult(ImmutableList.of()));
    mockMvc
        .perform(get("/observableToDeferredResult?value=error"))
        .andExpect(request().asyncResult(instanceOf(IllegalArgumentException.class)));
  }

  @Test
  public void testPublisherToDeferredResult() throws Exception {
    mockMvc
        .perform(get("/publisherToDeferredResult?value=foo"))
        .andExpect(request().asyncResult(ImmutableList.of("foo")));
    mockMvc
        .perform(get("/publisherToDeferredResult?value=bar&repeat=2"))
        .andExpect(request().asyncResult(ImmutableList.of("bar", "bar")));
    mockMvc
        .perform(get("/publisherToDeferredResult?value=baz&repeat=0"))
        .andExpect(request().asyncResult(ImmutableList.of()));
    mockMvc
        .perform(get("/publisherToDeferredResult?value=error"))
        .andExpect(request().asyncResult(instanceOf(IllegalArgumentException.class)));
  }

  @Test
  public void testCompletableToDeferredResult() throws Exception {
    assertNull(
        mockMvc
            .perform(get("/completableToDeferredResult?fail=false"))
            .andReturn()
            .getAsyncResult());
    mockMvc
        .perform(get("/completableToDeferredResult?fail=true"))
        .andExpect(request().asyncResult(instanceOf(IllegalArgumentException.class)));
  }

  @Test
  public void testObservableToSse() throws Exception {
    mockMvc.perform(get("/observableToSse?value=foo")).andExpect(content().string("data:foo\n\n"));
    mockMvc
        .perform(get("/observableToSse?value=bar&repeat=2"))
        .andExpect(content().string("data:bar\n\ndata:bar\n\n"));
    mockMvc.perform(get("/observableToSse?value=baz&repeat=0")).andExpect(content().string(""));
    mockMvc
        .perform(get("/observableToSse?value=error"))
        .andExpect(request().asyncResult(instanceOf(IllegalArgumentException.class)));
  }

  @Test
  public void testPublisherToSse() throws Exception {
    mockMvc.perform(get("/publisherToSse?value=foo")).andExpect(content().string("data:foo\n\n"));
    mockMvc
        .perform(get("/publisherToSse?value=bar&repeat=2"))
        .andExpect(content().string("data:bar\n\ndata:bar\n\n"));
    mockMvc.perform(get("/publisherToSse?value=baz&repeat=0")).andExpect(content().string(""));
    mockMvc
        .perform(get("/publisherToSse?value=error"))
        .andExpect(request().asyncResult(instanceOf(IllegalArgumentException.class)));
  }

  @Test
  public void testPublisherToSseWithKeepAlive() throws Exception {
    testScheduler.advanceTimeTo(0, MILLISECONDS);
    MockHttpServletResponse response =
        mockMvc
            .perform(get("/publisherToSse/with-keep-alive?value=foo&repeat=2&interval=250"))
            .andReturn()
            .getResponse();
    testScheduler.advanceTimeTo(99, TimeUnit.MILLISECONDS);
    assertEquals("", response.getContentAsString());
    testScheduler.advanceTimeTo(249, TimeUnit.MILLISECONDS);
    assertEquals(
        "data:keep-alive #0\n\n" + "data:keep-alive #1\n\n", response.getContentAsString());
    testScheduler.advanceTimeTo(250, TimeUnit.MILLISECONDS);
    assertEquals(
        "data:keep-alive #0\n\n" + "data:keep-alive #1\n\n" + "data:foo\n\n",
        response.getContentAsString());
    testScheduler.advanceTimeTo(300, TimeUnit.MILLISECONDS);
    assertEquals(
        ""
            + "data:keep-alive #0\n\n"
            + "data:keep-alive #1\n\n"
            + "data:foo\n\n"
            + "data:keep-alive #2\n\n",
        response.getContentAsString());
    testScheduler.advanceTimeTo(1000, TimeUnit.MILLISECONDS);
    assertEquals(
        ""
            + "data:keep-alive #0\n\n"
            + "data:keep-alive #1\n\n"
            + "data:foo\n\n"
            + "data:keep-alive #2\n\n"
            + "data:keep-alive #3\n\n"
            + "data:foo\n\n",
        response.getContentAsString());
  }

  @Test
  public void testPublisherToSseWithKeepAliveAndError() throws Exception {
    testScheduler.advanceTimeTo(0, MILLISECONDS);
    MockHttpServletResponse response =
        mockMvc
            .perform(get("/publisherToSse/with-keep-alive?value=error&repeat=1&interval=150"))
            .andReturn()
            .getResponse();
    testScheduler.advanceTimeTo(149, TimeUnit.MILLISECONDS);
    assertEquals("data:keep-alive #0\n\n", response.getContentAsString());
    testScheduler.advanceTimeTo(200, TimeUnit.MILLISECONDS);
    assertEquals("data:keep-alive #0\n\n", response.getContentAsString());

    // XXX: An error prevents further emissions, but there is no other evidence of it in the output.
  }

  @Test
  public void testPublisherToSseWithComplexObject() throws Exception {
    mockMvc
        .perform(get("/publisherToSse/with-complex-object?repeat=2"))
        .andExpect(
            content()
                .string(
                    ""
                        + "data:{\"name\":\"foo\",\"age\":0}\n\n"
                        + "data:{\"name\":\"foo\",\"age\":1}\n\n"));
    mockMvc
        .perform(get("/publisherToSse/with-complex-object?mediaType=application/xml&repeat=2"))
        .andExpect(
            content()
                .string(
                    ""
                        + "data:<Person><name>foo</name><age>0</age></Person>\n\n"
                        + "data:<Person><name>foo</name><age>1</age></Person>\n\n"));
  }

  static class Person {
    private final String name;
    private final int age;

    @JsonCreator
    Person(String name, int age) {
      this.name = name;
      this.age = age;
    }

    public String getName() {
      return name;
    }

    public int getAge() {
      return age;
    }
  }

  @RestController
  static class TestController {
    private final TestScheduler testScheduler;

    TestController(TestScheduler testScheduler) {
      this.testScheduler = testScheduler;
    }

    @GetMapping("/singleToDeferredResult")
    public DeferredResult<String> withSingleToDeferredResult(@RequestParam String value) {
      return Single.fromCallable(defer(value, "error")).to(singleToDeferredResult());
    }

    @GetMapping("/maybeToDeferredResult")
    public DeferredResult<String> withMaybeToDeferredResult(@RequestParam Optional<String> value) {
      return Maybe.fromCallable(defer(value.orElse(null), "error")).to(maybeToDeferredResult());
    }

    @GetMapping("/observableToDeferredResult")
    public DeferredResult<ImmutableList<String>> withObservableToDeferredResult(
        @RequestParam String value, @RequestParam(defaultValue = "1") int repeat) {
      return Observable.defer(() -> Observable.just(defer(value, "error").call()))
          .repeat(repeat)
          .to(observableToDeferredResult(ImmutableList::copyOf));
    }

    @GetMapping("/publisherToDeferredResult")
    public DeferredResult<ImmutableList<String>> withPublisherToDeferredResult(
        @RequestParam String value, @RequestParam(defaultValue = "1") int repeat) {
      return Flowable.defer(() -> Flowable.just(defer(value, "error").call()))
          .repeat(repeat)
          .to(publisherToDeferredResult(ImmutableList::copyOf));
    }

    @GetMapping("/completableToDeferredResult")
    public DeferredResult<Void> withPublisherToDeferredResult(@RequestParam boolean fail) {
      return Completable.defer(
              () ->
                  fail ? Completable.error(new IllegalArgumentException()) : Completable.complete())
          .to(completableToDeferredResult());
    }

    @GetMapping("/observableToSse")
    public SseEmitter withObservableToSse(
        @RequestParam String value, @RequestParam(defaultValue = "1") int repeat) {
      return Observable.defer(() -> Observable.just(defer(value, "error").call()))
          .repeat(repeat)
          .to(observableToSse());
    }

    @GetMapping("/publisherToSse")
    public SseEmitter withPublisherToSse(
        @RequestParam String value, @RequestParam(defaultValue = "1") int repeat) {
      return Flowable.defer(() -> Flowable.just(defer(value, "error").call()))
          .repeat(repeat)
          .to(publisherToSse());
    }

    @GetMapping("/publisherToSse/with-keep-alive")
    public SseEmitter withPublisherToSseAndKeepAlive(
        @RequestParam String value, @RequestParam int repeat, @RequestParam int interval) {
      return Flowable.interval(interval, MILLISECONDS, testScheduler)
          .map(i -> defer(value, "error").call())
          .limit(repeat)
          .to(publisherToSse(null, Duration.ofMillis(100), i -> "keep-alive #" + i, testScheduler));
    }

    @GetMapping("/publisherToSse/with-complex-object")
    public SseEmitter withPublisherToSseAndComplexObject(
        @RequestParam Optional<String> mediaType, @RequestParam int repeat) {
      return Flowable.range(0, repeat)
          .map(i -> new Person("foo", i))
          .to(publisherToSse(mediaType.map(MediaType::valueOf).orElse(null)));
    }

    private static <T> Callable<T> defer(T value, T errorValue) {
      return () -> {
        if (errorValue.equals(value)) {
          throw new IllegalArgumentException("Error!");
        }

        return value;
      };
    }
  }
}
