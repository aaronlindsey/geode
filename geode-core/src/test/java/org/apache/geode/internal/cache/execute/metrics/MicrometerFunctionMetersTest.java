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
package org.apache.geode.internal.cache.execute.metrics;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

public class MicrometerFunctionMetersTest {

  private static final String FUNCTION_ID = "TestFunction";

  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  private SimpleMeterRegistry meterRegistry;

  @Before
  public void setUp() {
    meterRegistry = new SimpleMeterRegistry();
  }

  @After
  public void tearDown() {
    meterRegistry.close();
  }

  @Test
  public void constructor_registersSuccessTimer() {
    new MicrometerFunctionMeters(FUNCTION_ID, meterRegistry);

    assertThat(successTimer())
        .as("geode.function.executions timer with tags function=%s, succeeded=true", FUNCTION_ID)
        .isNotNull();
    assertThat(successTimer().getId().getDescription())
        .as("success timer description")
        .isEqualTo("Count and total time of successful function executions");
  }

  @Test
  public void constructor_registersFailureTimer() {
    new MicrometerFunctionMeters(FUNCTION_ID, meterRegistry);

    assertThat(failureTimer())
        .as("geode.function.executions timer with tags function=%s, succeeded=false", FUNCTION_ID)
        .isNotNull();
    assertThat(failureTimer().getId().getDescription())
        .as("failure timer description")
        .isEqualTo("Count and total time of failed function executions");
  }

  @Test
  public void constructor_throws_ifMeterRegistryIsNull() {
    Throwable thrown = catchThrowable(() -> new MicrometerFunctionMeters(FUNCTION_ID, null));

    assertThat(thrown)
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void close_removesSuccessTimer() {
    FunctionExecutionsTimer
        functionExecutionsTimer = new MicrometerFunctionMeters(FUNCTION_ID, meterRegistry);

    functionExecutionsTimer.close();

    assertThat(successTimer())
        .as("geode.function.executions timer with tags function=%s, succeeded=true", FUNCTION_ID)
        .isNull();
  }

  @Test
  public void close_removesFailureTimer() {
    FunctionExecutionsTimer
        functionExecutionsTimer = new MicrometerFunctionMeters(FUNCTION_ID, meterRegistry);

    functionExecutionsTimer.close();

    assertThat(failureTimer())
        .as("geode.function.executions timer with tags function=%s, succeeded=false", FUNCTION_ID)
        .isNull();
  }

  @Test
  public void close_closesTimers() {
    Timer successTimer = mock(Timer.class);
    Timer failureTimer = mock(Timer.class);
    FunctionExecutionsTimer functionExecutionsTimer = new MicrometerFunctionMeters(FUNCTION_ID, mock(MeterRegistry.class),
        (a, b) -> successTimer, (a, b) -> failureTimer);

    functionExecutionsTimer.close();

    verify(successTimer).close();
    verify(failureTimer).close();
  }

  @Test
  public void record_incrementsSuccessTimerCount_ifExecutionSucceeded() {
    FunctionExecutionsTimer
        functionExecutionsTimer = new MicrometerFunctionMeters(FUNCTION_ID, meterRegistry);

    functionExecutionsTimer.record(0, NANOSECONDS, true);

    assertThat(successTimer().count())
        .as("Success timer count")
        .isEqualTo(1);
  }

  @Test
  public void record_doesNotIncrementFailureTimerCount_ifExecutionSucceeded() {
    FunctionExecutionsTimer
        functionExecutionsTimer = new MicrometerFunctionMeters(FUNCTION_ID, meterRegistry);

    functionExecutionsTimer.record(0, NANOSECONDS, true);

    assertThat(failureTimer().count())
        .as("Failure timer count")
        .isEqualTo(0);
  }

  @Test
  public void record_incrementsSuccessTimerTotalTime_ifExecutionSucceeded() {
    FunctionExecutionsTimer
        functionExecutionsTimer = new MicrometerFunctionMeters(FUNCTION_ID, meterRegistry);

    functionExecutionsTimer.record(42, NANOSECONDS, true);

    assertThat(successTimer().totalTime(NANOSECONDS))
        .as("Success timer total time")
        .isEqualTo(42);
  }

  @Test
  public void record_doesNotIncrementFailureTimerTotalTime_ifExecutionSucceeded() {
    FunctionExecutionsTimer
        functionExecutionsTimer = new MicrometerFunctionMeters(FUNCTION_ID, meterRegistry);

    functionExecutionsTimer.record(24, NANOSECONDS, true);

    assertThat(failureTimer().totalTime(NANOSECONDS))
        .as("Failure timer total time")
        .isEqualTo(0);
  }

  @Test
  public void record_incrementsFailureTimerCount_ifExecutionFailed() {
    FunctionExecutionsTimer
        functionExecutionsTimer = new MicrometerFunctionMeters(FUNCTION_ID, meterRegistry);

    functionExecutionsTimer.record(0, NANOSECONDS, false);

    assertThat(failureTimer().count())
        .as("Failure timer count")
        .isEqualTo(1);
  }

  @Test
  public void record_doesNotIncrementSuccessTimerCount_ifExecutionFailed() {
    FunctionExecutionsTimer
        functionExecutionsTimer = new MicrometerFunctionMeters(FUNCTION_ID, meterRegistry);

    functionExecutionsTimer.record(0, NANOSECONDS, false);

    assertThat(successTimer().count())
        .as("Success timer count")
        .isEqualTo(0);
  }

  @Test
  public void record_incrementsFailureTimerTotalTime_ifExecutionFailed() {
    FunctionExecutionsTimer
        functionExecutionsTimer = new MicrometerFunctionMeters(FUNCTION_ID, meterRegistry);

    functionExecutionsTimer.record(68, NANOSECONDS, false);

    assertThat(failureTimer().totalTime(NANOSECONDS))
        .as("Failure timer total time")
        .isEqualTo(68);
  }

  @Test
  public void record_doesNotIncrementSuccessTimerTotalTime_ifExecutionFailed() {
    FunctionExecutionsTimer
        functionExecutionsTimer = new MicrometerFunctionMeters(FUNCTION_ID, meterRegistry);

    functionExecutionsTimer.record(12, NANOSECONDS, false);

    assertThat(successTimer().totalTime(NANOSECONDS))
        .as("Success timer total time")
        .isEqualTo(0);
  }

  private Timer successTimer() {
    return functionExecutionsTimer(true);
  }

  private Timer failureTimer() {
    return functionExecutionsTimer(false);
  }

  private Timer functionExecutionsTimer(boolean succeededTagValue) {
    return meterRegistry
        .find("geode.function.executions")
        .tag("function", FUNCTION_ID)
        .tag("succeeded", String.valueOf(succeededTagValue))
        .timer();
  }
}
