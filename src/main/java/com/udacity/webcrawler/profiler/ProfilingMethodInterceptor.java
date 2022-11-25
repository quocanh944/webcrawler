package com.udacity.webcrawler.profiler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A method interceptor that checks whether {@link Method}s are annotated with the {@link Profiled}
 * annotation. If they are, the method interceptor records how long the method invocation took.
 */
final class ProfilingMethodInterceptor implements InvocationHandler {

  private final Clock clock;
  private final ProfilingState profilingState;
  private final Object object;

  // TODO: You will need to add more instance fields and constructor arguments to this class.

  public ProfilingMethodInterceptor(Clock clock, Object object, ProfilingState profilingState) {
    this.clock = Objects.requireNonNull(clock);
    this.object = object;
    this.profilingState = profilingState;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    // TODO: This method interceptor should inspect the called method to see if it is a profiled
    //       method. For profiled methods, the interceptor should record the start time, then
    //       invoke the method using the object that is being profiled. Finally, for profiled
    //       methods, the interceptor should record how long the method call took, using the
    //       ProfilingState methods.

    // Check it is a profiled method
    if (method.isAnnotationPresent(Profiled.class)) {
      // record the start time
      Instant instant = clock.instant();
      // invoke method using the object that is being profiled
      try {
        return method.invoke(object, args);
      } catch (Throwable throwable) {
        // The Throwable class is the superclass of all errors and exceptions in the Java language
        throw throwable.getCause(); // Throw the cause of this throwable to the specified value.
      } finally {
        // record using profiling State record method
        // calculate the time using Duration between instant mean start time
        // and clock.instant mean end time
        profilingState.record(object.getClass(), method, Duration.between(instant, clock.instant()));
      }
    }

    return method.invoke(object, args);
  }
}
