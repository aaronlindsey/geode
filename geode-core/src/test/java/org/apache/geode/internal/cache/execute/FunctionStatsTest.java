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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
    new FunctionStats(FUNCTION_ID, statistics, functionServiceStats, meterRegistry);

    assertThat(successTimer())
        .as("geode.function.executions timer with tags function=%s, succeeded=true", FUNCTION_ID)
        .isNotNull();
    assertThat(successTimer().getId().getDescription())
        .as("success timer description")
        .isEqualTo("Count and total time of successful function executions");
  }

  @Test
  public void constructor_registersFailureTimer() {
    new FunctionStats(FUNCTION_ID, statistics, functionServiceStats, meterRegistry);

    assertThat(failureTimer())
        .as("geode.function.executions timer with tags function=%s, succeeded=false", FUNCTION_ID)
        .isNotNull();
    assertThat(failureTimer().getId().getDescription())
        .as("failure timer description")
        .isEqualTo("Count and total time of failed function executions");
  }

  @Test
  public void constructor_doesNotThrow_ifMeterRegistryIsNull() {
    assertThatCode(() -> new FunctionStats(FUNCTION_ID, statistics, functionServiceStats, null))
        .doesNotThrowAnyException();
  }

  @Test
  public void close_removesSuccessTimer() {
    FunctionStats functionStats =
        new FunctionStats(FUNCTION_ID, statistics, functionServiceStats, meterRegistry);

    functionStats.close();

    assertThat(successTimer())
        .as("geode.function.executions timer with tags function=%s, succeeded=true", FUNCTION_ID)
        .isNull();
  }

  @Test
  public void close_removesFailureTimer() {
    FunctionStats functionStats =
        new FunctionStats(FUNCTION_ID, statistics, functionServiceStats, meterRegistry);

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
        mock(MeterRegistry.class), (a, b) -> successTimer, (a, b) -> failureTimer);

    functionStats.close();

    verify(successTimer).close();
    verify(failureTimer).close();
  }

  @Test
  public void close_doesNotThrow_ifMeterRegistryIsNull() {
    FunctionStats functionStats =
        new FunctionStats(FUNCTION_ID, statistics, functionServiceStats, null);

    assertThatCode(functionStats::close)
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
