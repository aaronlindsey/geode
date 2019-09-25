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

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

import org.apache.geode.Statistics;

public class FunctionStatsTest {

  private static final String FUNCTION_ID = "TestFunction";

  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  @Mock
  private Statistics statistics;

  @Mock
  private FunctionServiceStats functionServiceStats;

  @Mock
  private LongSupplier clock;

  @Mock
  private BooleanSupplier enableClockStats;

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
    new FunctionStats(FUNCTION_ID, statistics, functionServiceStats, clock, enableClockStats, meterRegistry);

    assertThat(successTimer())
        .as("geode.function.executions timer with tags function=%s, succeeded=true", FUNCTION_ID)
        .isNotNull();
    assertThat(successTimer().getId().getDescription())
        .as("success timer description")
        .isEqualTo("Count and total time of successful function executions");
  }

  @Test
  public void constructor_registersFailureTimer() {
    new FunctionStats(FUNCTION_ID, statistics, functionServiceStats, clock, enableClockStats, meterRegistry);

    assertThat(failureTimer())
        .as("geode.function.executions timer with tags function=%s, succeeded=false", FUNCTION_ID)
        .isNotNull();
    assertThat(failureTimer().getId().getDescription())
        .as("failure timer description")
        .isEqualTo("Count and total time of failed function executions");
  }

  @Test
  public void constructor_doesNotThrow_ifMeterRegistryIsNull() {
    assertThatCode(() -> new FunctionStats(FUNCTION_ID, statistics, functionServiceStats, clock, enableClockStats, null))
        .doesNotThrowAnyException();
  }

  @Test
  public void close_removesSuccessTimer() {
    FunctionStats functionStats = new FunctionStats(FUNCTION_ID, statistics, functionServiceStats, clock, enableClockStats, meterRegistry);

    functionStats.close();

    assertThat(successTimer())
        .as("geode.function.executions timer with tags function=%s, succeeded=true", FUNCTION_ID)
        .isNull();
  }

  @Test
  public void close_removesFailureTimer() {
    FunctionStats functionStats = new FunctionStats(FUNCTION_ID, statistics, functionServiceStats, clock, enableClockStats, meterRegistry);

    functionStats.close();

    assertThat(failureTimer())
        .as("geode.function.executions timer with tags function=%s, succeeded=false", FUNCTION_ID)
        .isNull();
  }

  @Test
  public void close_closesTimers() {
    Timer successTimer = mock(Timer.class);
    Timer failureTimer = mock(Timer.class);
    FunctionStats functionStats = new FunctionStats(FUNCTION_ID, statistics, functionServiceStats,
        clock, enableClockStats, mock(MeterRegistry.class),
        (a, b) -> successTimer, (a, b) -> failureTimer);

    functionStats.close();

    verify(successTimer).close();
    verify(failureTimer).close();
  }

  @Test
  public void close_doesNotThrow_ifMeterRegistryIsNull() {
    FunctionStats functionStats = new FunctionStats(FUNCTION_ID, statistics, functionServiceStats, clock, enableClockStats, null);

    assertThatCode(functionStats::close)
        .doesNotThrowAnyException();
  }

  @Test
  public void endFunctionExecution_incrementsSuccessTimerCount() {
    FunctionStats functionStats = new FunctionStats(FUNCTION_ID, statistics, functionServiceStats, clock, enableClockStats, meterRegistry);

    functionStats.endFunctionExecution(0, false);

    assertThat(successTimer().count())
        .as("Success timer count")
        .isEqualTo(1);
  }

  @Test
  public void endFunctionExecution_doesNotIncrementFailureTimerCount() {
    FunctionStats functionStats =
        new FunctionStats(FUNCTION_ID, statistics, functionServiceStats, clock, enableClockStats, meterRegistry);

    functionStats.endFunctionExecution(0, false);

    assertThat(failureTimer().count())
        .as("Failure timer count")
        .isEqualTo(0);
  }

  @Test
  public void endFunctionExecution_incrementsSuccessTimerTotalTime() {
    FunctionStats functionStats = new FunctionStats(FUNCTION_ID, statistics, functionServiceStats, clock, enableClockStats, meterRegistry);
    when(clock.getAsLong()).thenReturn(42L);

    functionStats.endFunctionExecution(0, false);

    assertThat(successTimer().totalTime(NANOSECONDS))
        .as("Success timer total time")
        .isEqualTo(42);
  }

  @Test
  public void endFunctionExecution_doesNotIncrementFailureTimerTotalTime() {
    FunctionStats functionStats = new FunctionStats(FUNCTION_ID, statistics, functionServiceStats, clock, enableClockStats, meterRegistry);
    when(clock.getAsLong()).thenReturn(42L);

    functionStats.endFunctionExecution(0, false);

    assertThat(failureTimer().totalTime(NANOSECONDS))
        .as("Failure timer total time")
        .isEqualTo(0);
  }

  @Test
  public void endFunctionExecution_doesNotThrow_ifMeterRegistryIsNull() {
    FunctionStats functionStats = new FunctionStats(FUNCTION_ID, statistics, functionServiceStats, clock, enableClockStats, null);

    assertThatCode(() -> functionStats.endFunctionExecution(0, false))
        .doesNotThrowAnyException();
  }

  @Test
  public void endFunctionExecution_incrementsClockStats_ifClockStatsEnabled() {
    FunctionStats functionStats = new FunctionStats(FUNCTION_ID, statistics, functionServiceStats, clock, enableClockStats, meterRegistry);
    when(clock.getAsLong()).thenReturn(42L);
    when(enableClockStats.getAsBoolean()).thenReturn(true);

    functionStats.endFunctionExecution(0, true);

    verify(statistics)
        .incLong(functionStats.getFunctionExecutionsCompletedProcessingTimeId(), 42L);
    verify(statistics)
        .incLong(functionStats.getFunctionExecutionsHasResultCompletedProcessingTimeId(), 42L);
  }

  @Test
  public void endFunctionExecution_doesNotIncrementClockStats_ifClockStatsDisabled() {
    FunctionStats functionStats = new FunctionStats(FUNCTION_ID, statistics, functionServiceStats, clock, enableClockStats, meterRegistry);
    when(clock.getAsLong()).thenReturn(42L);
    when(enableClockStats.getAsBoolean()).thenReturn(false);

    functionStats.endFunctionExecution(0, true);

    verify(statistics, never())
        .incLong(functionStats.getFunctionExecutionsCompletedProcessingTimeId(), 42L);
    verify(statistics, never())
        .incLong(functionStats.getFunctionExecutionsHasResultCompletedProcessingTimeId(), 42L);
  }

  @Test
  public void endFunctionExecutionWithException_incrementsFailureTimerCount() {
    FunctionStats functionStats = new FunctionStats(FUNCTION_ID, statistics, functionServiceStats, clock, enableClockStats, meterRegistry);

    functionStats.endFunctionExecutionWithException(0, false);

    assertThat(failureTimer().count())
        .as("Failure timer count")
        .isEqualTo(1);
  }

  @Test
  public void endFunctionExecutionWithException_doesNotIncrementSuccessTimerCount() {
    FunctionStats functionStats = new FunctionStats(FUNCTION_ID, statistics, functionServiceStats, clock, enableClockStats, meterRegistry);

    functionStats.endFunctionExecutionWithException(0, false);

    assertThat(successTimer().count())
        .as("Success timer count")
        .isEqualTo(0);
  }

  @Test
  public void endFunctionExecutionWithException_incrementsFailureTimerTotalTime() {
    FunctionStats functionStats = new FunctionStats(FUNCTION_ID, statistics, functionServiceStats, clock, enableClockStats, meterRegistry);
    when(clock.getAsLong()).thenReturn(42L);

    functionStats.endFunctionExecutionWithException(0, false);

    assertThat(failureTimer().totalTime(NANOSECONDS))
        .as("Failure timer total time")
        .isEqualTo(42);
  }

  @Test
  public void endFunctionExecutionWithException_doesNotIncrementSuccessTimerTotalTime() {
    FunctionStats functionStats = new FunctionStats(FUNCTION_ID, statistics, functionServiceStats, clock, enableClockStats, meterRegistry);
    when(clock.getAsLong()).thenReturn(42L);

    functionStats.endFunctionExecutionWithException(0, false);

    assertThat(successTimer().totalTime(NANOSECONDS))
        .as("Success timer total time")
        .isEqualTo(0);
  }

  @Test
  public void endFunctionExecutionWithException_doesNotThrow_ifMeterRegistryIsNull() {
    FunctionStats functionStats = new FunctionStats(FUNCTION_ID, statistics, functionServiceStats, clock, enableClockStats, null);

    assertThatCode(() -> functionStats.endFunctionExecutionWithException(0, false))
        .doesNotThrowAnyException();
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
