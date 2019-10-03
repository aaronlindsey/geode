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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.verify;

import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

public class DelegatingFunctionStatsTest {

  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  @Mock
  private FunctionStats innerFunctionStats;

  @Mock
  private FunctionExecutionsTimer functionExecutionsTimer;

  private DelegatingFunctionStats delegatingFunctionStats;

  @Before
  public void setUp() {
    delegatingFunctionStats =
        new DelegatingFunctionStats(innerFunctionStats, functionExecutionsTimer);
  }

  @Test
  public void constructor_throwsIfInnerFunctionStatsIsNull() {
    Throwable thrown =
        catchThrowable(() -> new DelegatingFunctionStats(null, functionExecutionsTimer));

    assertThat(thrown)
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void constructor_throwsIfFunctionExecutionsTimerIsNull() {
    Throwable thrown = catchThrowable(() -> new DelegatingFunctionStats(innerFunctionStats, null));

    assertThat(thrown)
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void close_closesInnerFunctionStats() {
    delegatingFunctionStats.close();

    verify(innerFunctionStats).close();
  }

  @Test
  public void close_closesFunctionExecutionsTimer() {
    delegatingFunctionStats.close();

    verify(functionExecutionsTimer).close();
  }

  @Test
  public void getFunctionExecutionsCompleted_delegatesToInnerFunctionStats() {
    delegatingFunctionStats.getFunctionExecutionsCompleted();

    verify(innerFunctionStats).getFunctionExecutionsCompleted();
  }

  @Test
  public void getFunctionExecutionsRunning_delegatesToInnerFunctionStats() {
    delegatingFunctionStats.getFunctionExecutionsRunning();

    verify(innerFunctionStats).getFunctionExecutionsRunning();
  }

  @Test
  public void incResultsReturned_delegatesToInnerFunctionStats() {
    delegatingFunctionStats.incResultsReturned();

    verify(innerFunctionStats).incResultsReturned();
  }

  @Test
  public void getResultsReceived_delegatesToInnerFunctionStats() {
    delegatingFunctionStats.getResultsReceived();

    verify(innerFunctionStats).getResultsReceived();
  }

  @Test
  public void incResultsReceived_delegatesToInnerFunctionStats() {
    delegatingFunctionStats.incResultsReceived();

    verify(innerFunctionStats).incResultsReceived();
  }

  @Test
  public void getFunctionExecutionCalls_delegatesToInnerFunctionStats() {
    delegatingFunctionStats.getFunctionExecutionCalls();

    verify(innerFunctionStats).getFunctionExecutionCalls();
  }

  @Test
  public void getTime_delegatesToInnerFunctionStats() {
    delegatingFunctionStats.getTime();

    verify(innerFunctionStats).getTime();
  }

  @Test
  public void startFunctionExecution_delegatesToInnerFunctionStats() {
    delegatingFunctionStats.startFunctionExecution(true);

    verify(innerFunctionStats).startFunctionExecution(true);
  }

  @Test
  public void endFunctionExecution_delegatesToInnerFunctionStats() {
    long elapsed = 5;
    TimeUnit timeUnit = TimeUnit.NANOSECONDS;
    boolean haveResult = true;

    delegatingFunctionStats.recordSuccessfulExecution(elapsed, timeUnit, haveResult);

    verify(innerFunctionStats).recordSuccessfulExecution(elapsed, timeUnit, haveResult);
  }

  @Test
  public void endFunctionExecution_timerRecordsSuccessfulExecution() {
    long elapsed = 5;
    TimeUnit timeUnit = TimeUnit.NANOSECONDS;
    boolean haveResult = false;

    delegatingFunctionStats.recordSuccessfulExecution(elapsed, timeUnit, haveResult);

    verify(functionExecutionsTimer).recordSuccess(elapsed, timeUnit);
  }

  @Test
  public void endFunctionExecutionWithException_delegatesToInnerFunctionStats() {
    long elapsed = 5;
    TimeUnit timeUnit = TimeUnit.NANOSECONDS;
    boolean haveResult = true;

    delegatingFunctionStats.recordFailedExecution(elapsed, timeUnit, haveResult);

    verify(innerFunctionStats).recordFailedExecution(elapsed, timeUnit, haveResult);
  }

  @Test
  public void endFunctionExecutionWithException_timerRecordsFailedExecution() {
    long elapsed = 5;
    TimeUnit timeUnit = TimeUnit.NANOSECONDS;
    boolean haveResult = true;

    delegatingFunctionStats.recordFailedExecution(elapsed, timeUnit, haveResult);

    verify(functionExecutionsTimer).recordFailure(elapsed, timeUnit);
  }

}
