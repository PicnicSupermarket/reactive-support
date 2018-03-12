# Picnic Reactive Support

[![Build Status][travisci-badge]][travisci-builds]
[![Maven Central][maven-central-badge]][maven-central-browse]
[![Coverage][coveralls-badge]][coveralls-stats]

A collection of Reactive Programming (RxJava and Reactor) utilities forged and
implemented in the Picnic backend.

## How to Install

Using [JitPack][jitpack]:

### Gradle

```groovy
dependencies {
    compile 'com.github.PicnicSupermarket.reactive-support:rxjava-support:master-SNAPSHOT'
}
```

### Maven

```xml
<dependency>
    <groupId>com.github.PicnicSupermarket.reactive-support</groupId>
    <artifactId>rxjava-support</artifactId>
    <version>master-SNAPSHOT</version>
</dependency>
```

## How To Use

This library contains a number of useful RxJava utilities. Listed below are
example usages of some notable cases.

### RxJava Thread Context Propagation

To propagate thread-local contexts to RxJava [`Scheduler`][scheduler] thread
pools you can create your own `RxThreadLocal` containing a value fetcher
`Supplier<T>` (that is called before a `Runnable` is scheduled by a
`Scheduler`), a context setter `Consumer<T>` that sets the context on the new
RxJava `Scheduler` thread, and an unset `Consumer<T>` which reinstates the
original context after the `Scheduler` has finished executing the scheduled
`Runnable`. The same `Consumer<T>` can be used to both configure and restore
the context.

Please see the code below for an example usage with initialisation in
`ServletContextListener`, where we propagate the contents of SLF4J's `MDC` map
and Spring's `SecurityContextHolder`. This way that we can retain meaningful
logging attributes and execute secured methods from within RxJava-scheduled
actions.

```java
@WebListener
public final class RxServletContextListener implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        RxThreadLocal<?> mdcPropagation =
                RxThreadLocal.from(
                        MDC::getCopyOfContextMap,
                        m -> {
                            if (m != null) {
                                MDC.setContextMap(m);
                            }
                        });
        RxThreadLocal<?> securityContextPropagation =
                RxThreadLocal.from(
                        SecurityContextHolder::getContext, SecurityContextHolder::setContext);
        PicnicRxPlugins.configureContextPropagation(mdcPropagation, securityContextPropagation);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        PicnicRxPlugins.unsetContextPropagation();
        Schedulers.shutdown();
    }
}
```

### `RetryStrategy`

The `RetryStrategy` type is meant to be used in conjunction with
[`Flowable#retryWhen`][flowable-retrywhen] to create and define more advanced
retry strategies. For example:


```java
someFlowable
    .retryWhen(
         RetryStrategy.ifInstanceOf(TooManyRequestsException.class)
             .withBackoffScheduler(Schedulers.io())
             .exponentialBackoff(Duration.ofMillis(500)
             .build())
```

### `RxSpringUtil`

This is a utility that can be used to convert RxJava reactive types into
asynchronous Spring web types. See below for a number of example usages.

Converting Spring `DeferredResult`:

```java
@PostMapping("/endpoint")
public DeferredResult<Integer> someEndpoint() {
    return Single.just(1).to(singleToDeferredResult());
}

@GetMapping("/another")
public DeferredResult<ImmutableSet<Integer>> someOtherEndpoint() {
    return Flowable.just(1, 2, 3).to(publisherToDeferredResult(ImmutableSet::copyOf));
}
```

Converting to Spring's `SseEmitter`:

```java
@GetMapping("/sse")
public SseEmitter sseEndpoint() {
    return Flowable.just(1, 2, 3).to(publisherToSse());
}
```

### Custom Types

The following types can help save on some boilerplate code.

#### `AutoConnectUtil`

This util converts reactive types (`Observables`, `Flowables`), `Callables` and
`Iterables` etc. into an autoconnected replay `Observable`/`Flowable`. For example:

```java
Observable<String> dataStream = AutoConnectUtil.fromObservable(expensiveIoObs);
// Result replayed, i.e. expensive I/O is triggered once and then replayed to all subscribers
Single<Long> count = dataStream.count();
Observable<String> mapped = dataStream.map(String::toUpperCase);
```

## Contributing

Contributions are welcome! Feel free to file an [issue][new-issue] or open a
[pull request][new-pr].

When submitting changes, please make every effort to follow existing
conventions and style in order to keep the code as readable as possible. New
code must be covered by tests. As a rule of thumb, overall test coverage should
not decrease. (There are exceptions to this rule, e.g. when more code is
deleted than added.)

[coveralls-badge]: https://coveralls.io/repos/github/PicnicSupermarket/reactive-support/badge.svg?branch=master
[coveralls-stats]: https://coveralls.io/github/PicnicSupermarket/reactive-support
[flowable-retrywhen]: http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Flowable.html#retryWhen-io.reactivex.functions.Function-
[jitpack]: https://jitpack.io
[maven-central-badge]: https://img.shields.io/maven-central/v/tech.picnic.reactive-support/reactive-support.svg
[maven-central-browse]: https://repo1.maven.org/maven2/tech/picnic/reactive-support
[new-issue]: https://github.com/PicnicSupermarket/reactive-support/issues/new
[new-pr]: https://github.com/PicnicSupermarket/reactive-support/compare
[scheduler]: http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Scheduler.html
[travisci-badge]: https://travis-ci.org/PicnicSupermarket/reactive-support.svg?branch=master
[travisci-builds]: https://travis-ci.org/PicnicSupermarket/reactive-support
