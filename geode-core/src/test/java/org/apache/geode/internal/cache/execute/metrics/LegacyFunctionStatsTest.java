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
import static org.apache.geode.internal.cache.execute.metrics.LegacyFunctionStats.functionExecutionExceptionsId;
import static org.apache.geode.internal.cache.execute.metrics.LegacyFunctionStats.functionExecutionsCompletedId;
import static org.apache.geode.internal.cache.execute.metrics.LegacyFunctionStats.functionExecutionsCompletedProcessingTimeId;
import static org.apache.geode.internal.cache.execute.metrics.LegacyFunctionStats.functionExecutionsHasResultCompletedProcessingTimeId;
import static org.apache.geode.internal.cache.execute.metrics.LegacyFunctionStats.functionExecutionsHasResultRunningId;
import static org.apache.geode.internal.cache.execute.metrics.LegacyFunctionStats.functionExecutionsRunningId;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

import org.apache.geode.Statistics;

public class LegacyFunctionStatsTest {

  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  @Mock
  private Statistics statistics;

  @Mock
  private FunctionServiceStats functionServiceStats;

  @Test
  public void recordSuccessfulExecution_noResult_clockStatsDisabled_incrementsStats() {
    LegacyFunctionStats legacyFunctionStats =
        new LegacyFunctionStats(statistics, functionServiceStats, 0L, false);

    legacyFunctionStats.recordSuccessfulExecution(5, NANOSECONDS, false);

    verify(statistics)
        .incInt(functionExecutionsCompletedId(), 1);
    verify(statistics)
        .incInt(functionExecutionsRunningId(), -1);
    verify(statistics, never())
        .incLong(eq(functionExecutionsCompletedProcessingTimeId()), anyLong());
    verify(statistics, never())
        .incInt(eq(functionExecutionsHasResultRunningId()), anyInt());
    verify(statistics, never())
        .incLong(eq(functionExecutionsHasResultCompletedProcessingTimeId()), anyLong());
  }

  @Test
  public void recordSuccessfulExecution_hasResult_clockStatsDisabled_incrementsStats() {
    LegacyFunctionStats legacyFunctionStats =
        new LegacyFunctionStats(statistics, functionServiceStats, 0L, false);

    legacyFunctionStats.recordSuccessfulExecution(5, NANOSECONDS, true);

    verify(statistics)
        .incInt(functionExecutionsCompletedId(), 1);
    verify(statistics)
        .incInt(functionExecutionsRunningId(), -1);
    verify(statistics, never())
        .incLong(eq(functionExecutionsCompletedProcessingTimeId()), anyLong());
    verify(statistics)
        .incInt(functionExecutionsHasResultRunningId(), -1);
    verify(statistics, never())
        .incLong(eq(functionExecutionsHasResultCompletedProcessingTimeId()), anyLong());
  }

  @Test
  public void recordSuccessfulExecution_noResult_clockStatsEnabled_incrementsStats() {
    LegacyFunctionStats legacyFunctionStats =
        new LegacyFunctionStats(statistics, functionServiceStats, 0L, true);

    long elapsedNanos = 5;
    legacyFunctionStats.recordSuccessfulExecution(elapsedNanos, NANOSECONDS, false);

    verify(statistics)
        .incInt(functionExecutionsCompletedId(), 1);
    verify(statistics)
        .incInt(functionExecutionsRunningId(), -1);
    verify(statistics)
        .incLong(functionExecutionsCompletedProcessingTimeId(), elapsedNanos);
    verify(statistics, never())
        .incInt(eq(functionExecutionsHasResultRunningId()), anyInt());
    verify(statistics, never())
        .incLong(eq(functionExecutionsHasResultCompletedProcessingTimeId()), anyLong());
  }

  @Test
  public void recordSuccessfulExecution_hasResult_clockStatsEnabled_incrementsStats() {
    LegacyFunctionStats legacyFunctionStats =
        new LegacyFunctionStats(statistics, functionServiceStats, 0L, true);

    long elapsedNanos = 5;
    legacyFunctionStats.recordSuccessfulExecution(elapsedNanos, NANOSECONDS, true);

    verify(statistics)
        .incInt(functionExecutionsCompletedId(), 1);
    verify(statistics)
        .incInt(functionExecutionsRunningId(), -1);
    verify(statistics)
        .incLong(functionExecutionsCompletedProcessingTimeId(), elapsedNanos);
    verify(statistics)
        .incInt(functionExecutionsHasResultRunningId(), -1);
    verify(statistics)
        .incLong(functionExecutionsHasResultCompletedProcessingTimeId(), elapsedNanos);
  }

  @Test
  public void recordSuccessfulExecution_incrementsAggregateStats() {
    LegacyFunctionStats legacyFunctionStats =
        new LegacyFunctionStats(statistics, functionServiceStats, 0L, false);

    long elapsedNanos = 5;
    boolean haveResult = true;
    legacyFunctionStats.recordSuccessfulExecution(elapsedNanos, NANOSECONDS, haveResult);

    verify(functionServiceStats)
        .endFunctionExecution(elapsedNanos, haveResult);
  }

  @Test
  public void recordFailedExecution_noResult_incrementsStats() {
    LegacyFunctionStats legacyFunctionStats =
        new LegacyFunctionStats(statistics, functionServiceStats, 0L, false);

    legacyFunctionStats.recordFailedExecution(5, NANOSECONDS, false);

    verify(statistics)
        .incInt(functionExecutionsRunningId(), -1);
    verify(statistics)
        .incInt(functionExecutionExceptionsId(), 1);
    verify(statistics, never())
        .incInt(eq(functionExecutionsHasResultRunningId()), anyInt());
  }

  @Test
  public void recordFailedExecution_hasResult_incrementsStats() {
    LegacyFunctionStats legacyFunctionStats =
        new LegacyFunctionStats(statistics, functionServiceStats, 0L, false);

    legacyFunctionStats.recordFailedExecution(5, NANOSECONDS, true);

    verify(statistics)
        .incInt(functionExecutionsRunningId(), -1);
    verify(statistics)
        .incInt(functionExecutionExceptionsId(), 1);
    verify(statistics)
        .incInt(functionExecutionsHasResultRunningId(), -1);
  }

  @Test
  public void recordFailedExecution_incrementsAggregateStats() {
    LegacyFunctionStats legacyFunctionStats =
        new LegacyFunctionStats(statistics, functionServiceStats, 0L, false);

    boolean haveResult = true;
    legacyFunctionStats.recordFailedExecution(5, NANOSECONDS, haveResult);

    verify(functionServiceStats)
        .endFunctionExecutionWithException(haveResult);
  }
}
