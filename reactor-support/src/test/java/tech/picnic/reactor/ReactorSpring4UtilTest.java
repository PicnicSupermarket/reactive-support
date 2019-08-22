package tech.picnic.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;
import static tech.picnic.reactor.ReactorSpring4Util.publisherToDeferredResult;
import static tech.picnic.reactor.ReactorSpring4Util.publisherToSse;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.scheduler.VirtualTimeScheduler;

@TestInstance(Lifecycle.PER_CLASS)
class ReactorSpring4UtilTest {
  private VirtualTimeScheduler testScheduler;

  @SuppressWarnings("NullAway")
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    testScheduler = VirtualTimeScheduler.create();
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TestController(testScheduler))
            .setMessageConverters(
                new StringHttpMessageConverter(),
                new MappingJackson2HttpMessageConverter(),
                new MappingJackson2XmlHttpMessageConverter())
            .build();
  }

  @Test
  void testPublisherToDeferredResult() throws Exception {
    verifyAsyncGetRequest("/publisherToDeferredResult?value=foo", OK, "[\"foo\"]");
    verifyAsyncGetRequest("/publisherToDeferredResult?value=bar&repeat=1", OK, "[\"bar\",\"bar\"]");
    verifyAsyncGetRequest("/publisherToDeferredResult?value=error", BAD_REQUEST, "");
  }

  @Test
  void testPublisherToSse() throws Exception {
    verifySyncGetRequest("/publisherToSse?value=foo", "data:foo\n\n");
    verifySyncGetRequest("/publisherToSse?value=bar&repeat=1", "data:bar\n\ndata:bar\n\n");
    verifyAsyncGetRequest("/publisherToSse?value=error", BAD_REQUEST, "");
  }

  @Test
  void testPublisherToSseWithKeepAlive() throws Exception {
    testScheduler.advanceTimeTo(Instant.EPOCH);
    MockHttpServletResponse response =
        doGet("/publisherToSse/with-keep-alive?value=foo&repeat=2&interval=250")
            .andReturn()
            .getResponse();
    testScheduler.advanceTimeTo(Instant.EPOCH.plus(Duration.ofMillis(99)));
    assertEquals("", response.getContentAsString());
    testScheduler.advanceTimeTo(Instant.EPOCH.plus(Duration.ofMillis(249)));
    assertEquals(
        "data:keep-alive #0\n\n" + "data:keep-alive #1\n\n", response.getContentAsString());
    testScheduler.advanceTimeTo(Instant.EPOCH.plus(Duration.ofMillis(250)));
    assertEquals(
        "data:keep-alive #0\n\n" + "data:keep-alive #1\n\n" + "data:foo\n\n",
        response.getContentAsString());
    testScheduler.advanceTimeTo(Instant.EPOCH.plus(Duration.ofMillis(300)));
    assertEquals(
        ""
            + "data:keep-alive #0\n\n"
            + "data:keep-alive #1\n\n"
            + "data:foo\n\n"
            + "data:keep-alive #2\n\n",
        response.getContentAsString());
    testScheduler.advanceTimeTo(Instant.EPOCH.plus(Duration.ofSeconds(1)));
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
  void testPublisherToSseWithKeepAliveAndError() throws Exception {
    testScheduler.advanceTimeTo(Instant.EPOCH);
    MockHttpServletResponse response =
        doGet("/publisherToSse/with-keep-alive?value=error&repeat=1&interval=150")
            .andReturn()
            .getResponse();
    testScheduler.advanceTimeTo(Instant.EPOCH.plus(Duration.ofMillis(149)));
    assertEquals("data:keep-alive #0\n\n", response.getContentAsString());
    testScheduler.advanceTimeTo(Instant.EPOCH.plus(Duration.ofMillis(200)));
    assertEquals("data:keep-alive #0\n\n", response.getContentAsString());

    // XXX: An error prevents further emissions, but there is no other evidence of it in the output.
  }

  @Test
  void testPublisherToSseWithComplexObject() throws Exception {
    verifySyncGetRequest(
        "/publisherToSse/with-complex-object?repeat=2",
        "" + "data:{\"name\":\"foo\",\"age\":0}\n\n" + "data:{\"name\":\"foo\",\"age\":1}\n\n");
    verifySyncGetRequest(
        "/publisherToSse/with-complex-object?mediaType=application/xml&repeat=2",
        ""
            + "data:<Person><name>foo</name><age>0</age></Person>\n\n"
            + "data:<Person><name>foo</name><age>1</age></Person>\n\n");
  }

  private void verifySyncGetRequest(String request, String expectedContent) throws Exception {
    doGet(request)
        .andExpect(MockMvcResultMatchers.status().is(HttpStatus.OK.value()))
        .andExpect(MockMvcResultMatchers.content().string(expectedContent));
  }

  private void verifyAsyncGetRequest(
      String request, HttpStatus expectedStatus, String expectedContent) throws Exception {
    mockMvc
        .perform(MockMvcRequestBuilders.asyncDispatch(doGet(request).andReturn()))
        .andExpect(MockMvcResultMatchers.status().is(expectedStatus.value()))
        .andExpect(MockMvcResultMatchers.content().string(expectedContent));
  }

  private ResultActions doGet(String request) throws Exception {
    return mockMvc
        .perform(MockMvcRequestBuilders.get(request))
        .andExpect(MockMvcResultMatchers.request().asyncStarted());
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
    private final VirtualTimeScheduler virtualTimeScheduler;

    TestController(VirtualTimeScheduler virtualTimeScheduler) {
      this.virtualTimeScheduler = virtualTimeScheduler;
    }

    @GetMapping("/publisherToDeferredResult")
    public DeferredResult<ImmutableList<String>> withPublisherToDeferredResult(
        @RequestParam String value, @RequestParam(defaultValue = "0") int repeat) {
      return Flux.defer(() -> Mono.fromCallable(defer(value)))
          .repeat(repeat)
          .as(publisherToDeferredResult(ImmutableList::copyOf));
    }

    @GetMapping("/publisherToSse")
    public SseEmitter withPublisherToSse(
        @RequestParam String value, @RequestParam(defaultValue = "0") int repeat) {
      return Flux.defer(() -> Mono.fromCallable(defer(value))).repeat(repeat).as(publisherToSse());
    }

    @GetMapping("/publisherToSse/with-keep-alive")
    public SseEmitter withPublisherToSseAndKeepAlive(
        @RequestParam String value, @RequestParam int repeat, @RequestParam int interval) {
      return Flux.interval(Duration.ofMillis(interval), virtualTimeScheduler)
          .flatMap(i -> Mono.fromCallable(defer(value)))
          .limitRequest(repeat)
          .as(
              publisherToSse(
                  null, Duration.ofMillis(100), i -> "keep-alive #" + i, virtualTimeScheduler));
    }

    @GetMapping("/publisherToSse/with-complex-object")
    public SseEmitter withPublisherToSseAndComplexObject(
        @RequestParam Optional<String> mediaType, @RequestParam int repeat) {
      return Flux.range(0, repeat)
          .map(i -> new Person("foo", i))
          .as(publisherToSse(mediaType.map(MediaType::valueOf).orElse(null)));
    }

    private static <T> Callable<T> defer(T value) {
      return () -> {
        if ("error".equals(value)) {
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
