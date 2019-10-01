/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.internal.cache.execute;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.apache.geode.annotations.VisibleForTesting;
import org.apache.geode.internal.NanoTimer;

public class MicrometerFunctionStats implements FunctionStats {

  private final LongSupplier clock;
  private final MeterRegistry meterRegistry;
  private final Timer successTimer;
  private final Timer failureTimer;

  MicrometerFunctionStats(String functionId, MeterRegistry meterRegistry) {
    this(functionId, NanoTimer::getTime, meterRegistry);
  }

  @VisibleForTesting
  MicrometerFunctionStats(String functionId, LongSupplier clock, MeterRegistry meterRegistry) {
    this(functionId, clock, meterRegistry, MicrometerFunctionStats::registerSuccessTimer, MicrometerFunctionStats::registerFailureTimer);
  }

  @VisibleForTesting
  MicrometerFunctionStats(String functionId, LongSupplier clock, MeterRegistry meterRegistry, BiFunction<String, MeterRegistry, Timer> registerSuccessTimerFunction, BiFunction<String, MeterRegistry, Timer> registerFailureTimerFunction) {
    Objects.requireNonNull(meterRegistry);

    this.clock = clock;
    this.meterRegistry = meterRegistry;

    successTimer = registerSuccessTimerFunction.apply(functionId, meterRegistry);
    failureTimer = registerFailureTimerFunction.apply(functionId, meterRegistry);
  }

  @Override
  public long startTime() {
    return clock.getAsLong();
  }

  @Override
  public void close() {
    meterRegistry.remove(successTimer);
    successTimer.close();

    meterRegistry.remove(failureTimer);
    failureTimer.close();
  }

  @Override
  public void endFunctionExecution(long start, boolean haveResult) {
    long elapsed = clock.getAsLong() - start;

    successTimer.record(elapsed, NANOSECONDS);
  }

  @Override
  public void endFunctionExecutionWithException(long startTime, boolean haveResult) {
    long elapsed = clock.getAsLong() - startTime;

    failureTimer.record(elapsed, NANOSECONDS);
  }

  private static Timer registerSuccessTimer(String functionId, MeterRegistry meterRegistry) {
    return Timer.builder("geode.function.executions")
        .description("Count and total time of successful function executions")
        .tag("function", functionId)
        .tag("succeeded", TRUE.toString())
        .register(meterRegistry);
  }

  private static Timer registerFailureTimer(String functionId, MeterRegistry meterRegistry) {
    return Timer.builder("geode.function.executions")
        .description("Count and total time of failed function executions")
        .tag("function", functionId)
        .tag("succeeded", FALSE.toString())
        .register(meterRegistry);
  }

}
