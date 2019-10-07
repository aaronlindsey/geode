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

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;

import java.util.List;
import java.util.stream.Stream;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import org.apache.geode.Statistics;
import org.apache.geode.StatisticsFactory;
import org.apache.geode.cache.execute.Function;

public class FunctionStatsManagerTest {

  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule().strictness(LENIENT);

  @Mock
  private StatisticsFactory statisticsFactory;

  @Mock
  private FunctionServiceStats functionServiceStats;

  @Mock
  private Statistics statistics;

  private MeterRegistry meterRegistry;

  @Before
  public void setUp() {
    when(statisticsFactory.createAtomicStatistics(any(), any()))
        .thenReturn(statistics);

    meterRegistry = new SimpleMeterRegistry();
  }

  @Test
  public void getFunctionStatsByName_usesSingletonDummyStatistics_ifStatsDisabled() {
    FunctionStatsManager functionStatsManager = new FunctionStatsManager(true, statisticsFactory,
        functionServiceStats, () -> meterRegistry);

    FunctionStats functionStats = functionStatsManager.getFunctionStatsByName("foo");

    Statistics singletonDummyStatistics = FunctionStatsManager.getDummyStatistics();
    assertThat(functionStats.getStatistics())
        .isSameAs(singletonDummyStatistics);
  }

  @Test
  public void getFunctionStatsByName_usesRealStatistics_ifStatsEnabled() {
    FunctionStatsManager functionStatsManager = new FunctionStatsManager(false, statisticsFactory,
        functionServiceStats, () -> meterRegistry);
    Statistics statisticsReturnedFromFactory = mock(Statistics.class);
    when(statisticsFactory.createAtomicStatistics(any(), any()))
        .thenReturn(statisticsReturnedFromFactory);

    FunctionStats functionStats = functionStatsManager.getFunctionStatsByName("foo");

    assertThat(functionStats.getStatistics())
        .isSameAs(statisticsReturnedFromFactory);
  }

  @Test
  public void getFunctionStatsByName_returnsSameInstanceForGivenName() {
    FunctionStatsManager functionStatsManager = new FunctionStatsManager(false, statisticsFactory,
        functionServiceStats, () -> meterRegistry);

    FunctionStats first = functionStatsManager.getFunctionStatsByName("foo");
    FunctionStats second = functionStatsManager.getFunctionStatsByName("foo");

    assertThat(second)
        .isSameAs(first);
  }

  @Test
  public void getFunctionStatsFor_usesSingletonDummyStatistics_ifStatsDisabled() {
    FunctionStatsManager functionStatsManager = new FunctionStatsManager(true, statisticsFactory,
        functionServiceStats, () -> meterRegistry);

    FunctionStats functionStats = functionStatsManager.getFunctionStatsFor(functionWithId("id"));

    Statistics singletonDummyStatistics = FunctionStatsManager.getDummyStatistics();
    assertThat(functionStats.getStatistics())
        .isSameAs(singletonDummyStatistics);
  }

  @Test
  public void getFunctionStatsFor_usesRealStatistics_ifStatsEnabled() {
    FunctionStatsManager functionStatsManager = new FunctionStatsManager(false, statisticsFactory,
        functionServiceStats, () -> meterRegistry);
    Statistics statisticsReturnedFromFactory = mock(Statistics.class);
    when(statisticsFactory.createAtomicStatistics(any(), any()))
        .thenReturn(statisticsReturnedFromFactory);

    FunctionStats functionStats = functionStatsManager.getFunctionStatsFor(functionWithId("id"));

    assertThat(functionStats.getStatistics())
        .isSameAs(statisticsReturnedFromFactory);
  }

  @Test
  public void getFunctionStatsFor_registersFunctionExecutionsTimers_ifNonInternalFunction() {
    MeterRegistry theMeterRegistry = new SimpleMeterRegistry();
    FunctionStatsManager functionStatsManager = new FunctionStatsManager(false, statisticsFactory,
        functionServiceStats, () -> theMeterRegistry);

    String functionId = "id";
    functionStatsManager.getFunctionStatsFor(functionWithId(functionId));

    assertThat(functionExecutionsSuccessTimer(theMeterRegistry, functionId))
        .as("Function executions success timer")
        .isNotNull();
    assertThat(functionExecutionsFailureTimer(theMeterRegistry, functionId))
        .as("Function executions failure timer")
        .isNotNull();
  }

  private static Timer functionExecutionsSuccessTimer(MeterRegistry meterRegistry,
      String functionId) {
    return getTimer(meterRegistry, functionId, true);
  }

  private static Timer functionExecutionsFailureTimer(MeterRegistry meterRegistry,
      String functionId) {
    return getTimer(meterRegistry, functionId, false);
  }

  private static Timer getTimer(MeterRegistry meterRegistry, String functionId, boolean succeeded) {
    return meterRegistry
        .find("geode.function.executions")
        .tag("functionId", functionId)
        .tag("succeeded", String.valueOf(succeeded))
        .timer();
  }

  private static Function functionWithId(String id) {
    Function function = mock(Function.class);
    when(function.getId()).thenReturn(id);
    return function;
  }

  // @Test
  // @Parameters({"false, false, false", // stats and meters
  // "false, false, true", // stats and no meters
  // "false, true, false", // stats and no meters
  // "false, true, true", // stats and no meters
  // "true, false, false", // no stats or meters
  // "true, false, true", // no stats and no meters
  // "true, true, false", // no stats and no meters
  // "true, true, true"}) // no stats and no meters
  // public void getFunctionStats_(boolean statsDisabled, boolean isInternalFunction) {

  @Test
  public void close_closesAllCreatedFunctionStats() {
    FunctionStatsManager functionStatsManager = new FunctionStatsManager(false, statisticsFactory,
        functionServiceStats, () -> meterRegistry);

    List<FunctionStats> functionStats = Stream.of("a", "b", "c")
        .map(functionStatsManager::getFunctionStatsByName)
        .collect(toList());

    functionStatsManager.close();

    assertThat(functionStats)
        .allMatch(FunctionStats::isClosed, "Function stats is closed");
  }
}
