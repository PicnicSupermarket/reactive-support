# Picnic Reactive Support

[![Build Status][travisci-badge]][travisci-builds]
[![Maven Central][maven-central-badge]][maven-central-browse]
[![Coverage][coveralls-badge]][coveralls-stats]
[![SonarCloud Quality Gate][sonarcloud-badge-quality-gate]][sonarcloud-dashboard]
[![SonarCloud Bugs][sonarcloud-badge-bugs]][sonarcloud-measure-reliability]
[![SonarCloud Vulnerabilities][sonarcloud-badge-vulnerabilities]][sonarcloud-measure-security]
[![SonarCloud Debt Ratio][sonarcloud-badge-debt-ratio]][sonarcloud-measure-maintainability]
[![BCH compliance][bettercodehub-badge]][bettercodehub]

A collection of Reactive Programming (RxJava and Reactor) utilities forged and
implemented in the Picnic backend.

## How to Install

Artifacts are hosted on [Maven's Central Repository][maven-central-search]:

### Gradle

```groovy
dependencies {
    compile 'tech.picnic.reactive-support:rxjava-support:0.0.1'
}
```

### Maven

```xml
<dependency>
    <groupId>tech.picnic.reactive-support</groupId>
    <artifactId>rxjava-support</artifactId>
    <version>0.0.1</version>
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
                            } else {
                                MDC.clear();
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

### `RxSpring4Util`

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

## Contributing

Contributions are welcome! Feel free to file an [issue][new-issue] or open a
[pull request][new-pr].

When submitting changes, please make every effort to follow existing
conventions and style in order to keep the code as readable as possible. New
code must be covered by tests. As a rule of thumb, overall test coverage should
not decrease. (There are exceptions to this rule, e.g. when more code is
deleted than added.)

[bettercodehub-badge]: https://bettercodehub.com/edge/badge/PicnicSupermarket/reactive-support?branch=master
[bettercodehub]: https://bettercodehub.com
[coveralls-badge]: https://coveralls.io/repos/github/PicnicSupermarket/reactive-support/badge.svg?branch=master
[coveralls-stats]: https://coveralls.io/github/PicnicSupermarket/reactive-support
[flowable-retrywhen]: http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Flowable.html#retryWhen-io.reactivex.functions.Function-
[maven-central-badge]: https://img.shields.io/maven-central/v/tech.picnic.reactive-support/reactive-support.svg
[maven-central-browse]: https://repo1.maven.org/maven2/tech/picnic/reactive-support
[maven-central-search]: https://search.maven.org
[new-issue]: https://github.com/PicnicSupermarket/reactive-support/issues/new
[new-pr]: https://github.com/PicnicSupermarket/reactive-support/compare
[scheduler]: http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Scheduler.html
[sonarcloud-badge-bugs]: https://sonarcloud.io/api/badges/measure?key=tech.picnic.reactive-support%3Areactive-support&metric=bugs
[sonarcloud-badge-debt-ratio]: https://sonarcloud.io/api/badges/measure?key=tech.picnic.reactive-support%3Areactive-support&metric=sqale_debt_ratio
[sonarcloud-badge-quality-gate]: https://sonarcloud.io/api/badges/gate?key=tech.picnic.reactive-support%3Areactive-support
[sonarcloud-badge-vulnerabilities]: https://sonarcloud.io/api/badges/measure?key=tech.picnic.reactive-support%3Areactive-support&metric=vulnerabilities
[sonarcloud-dashboard]: https://sonarcloud.io/dashboard?id=tech.picnic.reactive-support%3Areactive-support
[sonarcloud-measure-maintainability]: https://sonarcloud.io/component_measures?id=tech.picnic.reactive-support%3Areactive-support&metric=Coverage
[sonarcloud-measure-reliability]: https://sonarcloud.io/component_measures?id=tech.picnic.reactive-support%3Areactive-support&metric=Reliability
[sonarcloud-measure-security]: https://sonarcloud.io/component_measures?id=tech.picnic.reactive-support%3Areactive-support&metric=Security
[travisci-badge]: https://travis-ci.org/PicnicSupermarket/reactive-support.svg?branch=master
[travisci-builds]: https://travis-ci.org/PicnicSupermarket/reactive-support
