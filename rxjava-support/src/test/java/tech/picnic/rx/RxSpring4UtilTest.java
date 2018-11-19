package tech.picnic.rx;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testng.Assert.assertEquals;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test(singleThreaded = true)
public final class RxSpring4UtilTest {
  private final TestScheduler testScheduler = new TestScheduler();

  @SuppressWarnings("NullAway")
  private MockMvc mockMvc;

  @BeforeMethod
  public void setup() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TestController(testScheduler))
            .setMessageConverters(
                new StringHttpMessageConverter(),
                new MappingJackson2HttpMessageConverter(),
                new MappingJackson2XmlHttpMessageConverter())
            .build();
  }

  public void testSingleToDeferredResult() throws Exception {
    verifyAsyncGetRequest("/singleToDeferredResult?value=foo", OK, "foo");
    verifyAsyncGetRequest("/singleToDeferredResult?value=error", BAD_REQUEST, "");
  }

  public void testMaybeToDeferredResult() throws Exception {
    verifyAsyncGetRequest("/maybeToDeferredResult", NOT_FOUND, "");
    verifyAsyncGetRequest("/maybeToDeferredResult?value=foo", OK, "foo");
    verifyAsyncGetRequest("/maybeToDeferredResult?value=error", BAD_REQUEST, "");
  }

  public void testObservableToDeferredResult() throws Exception {
    verifyAsyncGetRequest("/observableToDeferredResult?value=foo", OK, "[\"foo\"]");
    verifyAsyncGetRequest(
        "/observableToDeferredResult?value=bar&repeat=2", OK, "[\"bar\",\"bar\"]");
    verifyAsyncGetRequest("/observableToDeferredResult?value=baz&repeat=0", OK, "[]");
    verifyAsyncGetRequest("/observableToDeferredResult?value=error", BAD_REQUEST, "");
  }

  public void testPublisherToDeferredResult() throws Exception {
    verifyAsyncGetRequest("/publisherToDeferredResult?value=foo", OK, "[\"foo\"]");
    verifyAsyncGetRequest("/publisherToDeferredResult?value=bar&repeat=2", OK, "[\"bar\",\"bar\"]");
    verifyAsyncGetRequest("/publisherToDeferredResult?value=baz&repeat=0", OK, "[]");
    verifyAsyncGetRequest("/publisherToDeferredResult?value=error", BAD_REQUEST, "");
  }

  public void testCompletableToDeferredResult() throws Exception {
    verifyAsyncGetRequest("/completableToDeferredResult?fail=false", OK, "");
    verifyAsyncGetRequest("/completableToDeferredResult?fail=true", BAD_REQUEST, "");
  }

  public void testObservableToSse() throws Exception {
    verifySyncGetRequest("/observableToSse?value=foo", OK, "data:foo\n\n");
    verifySyncGetRequest("/observableToSse?value=bar&repeat=2", OK, "data:bar\n\ndata:bar\n\n");
    verifySyncGetRequest("/observableToSse?value=baz&repeat=0", OK, "");
    verifyAsyncGetRequest("/observableToSse?value=error", BAD_REQUEST, "");
  }

  public void testPublisherToSse() throws Exception {
    verifySyncGetRequest("/publisherToSse?value=foo", OK, "data:foo\n\n");
    verifySyncGetRequest("/publisherToSse?value=bar&repeat=2", OK, "data:bar\n\ndata:bar\n\n");
    verifySyncGetRequest("/publisherToSse?value=baz&repeat=0", OK, "");
    verifyAsyncGetRequest("/publisherToSse?value=error", BAD_REQUEST, "");
  }

  public void testPublisherToSseWithKeepAlive() throws Exception {
    testScheduler.advanceTimeTo(0, MILLISECONDS);
    MockHttpServletResponse response =
        doGet("/publisherToSse/with-keep-alive?value=foo&repeat=2&interval=250")
            .andReturn()
            .getResponse();
    testScheduler.advanceTimeTo(99, TimeUnit.MILLISECONDS);
    assertEquals(response.getContentAsString(), "");
    testScheduler.advanceTimeTo(249, TimeUnit.MILLISECONDS);
    assertEquals(
        response.getContentAsString(), "data:keep-alive #0\n\n" + "data:keep-alive #1\n\n");
    testScheduler.advanceTimeTo(250, TimeUnit.MILLISECONDS);
    assertEquals(
        response.getContentAsString(),
        "data:keep-alive #0\n\n" + "data:keep-alive #1\n\n" + "data:foo\n\n");
    testScheduler.advanceTimeTo(300, TimeUnit.MILLISECONDS);
    assertEquals(
        response.getContentAsString(),
        ""
            + "data:keep-alive #0\n\n"
            + "data:keep-alive #1\n\n"
            + "data:foo\n\n"
            + "data:keep-alive #2\n\n");
    testScheduler.advanceTimeTo(1000, TimeUnit.MILLISECONDS);
    assertEquals(
        response.getContentAsString(),
        ""
            + "data:keep-alive #0\n\n"
            + "data:keep-alive #1\n\n"
            + "data:foo\n\n"
            + "data:keep-alive #2\n\n"
            + "data:keep-alive #3\n\n"
            + "data:foo\n\n");
  }

  public void testPublisherToSseWithKeepAliveAndError() throws Exception {
    testScheduler.advanceTimeTo(0, MILLISECONDS);
    MockHttpServletResponse response =
        doGet("/publisherToSse/with-keep-alive?value=error&repeat=1&interval=150")
            .andReturn()
            .getResponse();
    testScheduler.advanceTimeTo(149, TimeUnit.MILLISECONDS);
    assertEquals(response.getContentAsString(), "data:keep-alive #0\n\n");
    testScheduler.advanceTimeTo(200, TimeUnit.MILLISECONDS);
    assertEquals(response.getContentAsString(), "data:keep-alive #0\n\n");

    // XXX: An error prevents further emissions, but there is no other evidence of it in the output.
  }

  public void testPublisherToSseWithComplexObject() throws Exception {
    verifySyncGetRequest(
        "/publisherToSse/with-complex-object?repeat=2",
        OK,
        "" + "data:{\"name\":\"foo\",\"age\":0}\n\n" + "data:{\"name\":\"foo\",\"age\":1}\n\n");
    verifySyncGetRequest(
        "/publisherToSse/with-complex-object?mediaType=application/xml&repeat=2",
        OK,
        ""
            + "data:<Person><name>foo</name><age>0</age></Person>\n\n"
            + "data:<Person><name>foo</name><age>1</age></Person>\n\n");
  }

  private void verifySyncGetRequest(
      String request, HttpStatus expectedStatus, String expectedContent) throws Exception {
    doGet(request)
        .andExpect(status().is(expectedStatus.value()))
        .andExpect(content().string(expectedContent));
  }

  private void verifyAsyncGetRequest(
      String request, HttpStatus expectedStatus, String expectedContent) throws Exception {
    mockMvc
        .perform(asyncDispatch(doGet(request).andReturn()))
        .andExpect(status().is(expectedStatus.value()))
        .andExpect(content().string(expectedContent));
  }

  private ResultActions doGet(String request) throws Exception {
    return mockMvc.perform(get(request)).andExpect(request().asyncStarted());
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
      return Completable.fromCallable(defer(fail, Boolean.TRUE)).to(completableToDeferredResult());
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
          throw new BadRequestException();
        }

        return value;
      };
    }

    @ResponseStatus(BAD_REQUEST)
    static final class BadRequestException extends RuntimeException {
      /** The {@link java.io.Serializable serialization} ID. */
      private static final long serialVersionUID = 1L;
    }
  }
}
