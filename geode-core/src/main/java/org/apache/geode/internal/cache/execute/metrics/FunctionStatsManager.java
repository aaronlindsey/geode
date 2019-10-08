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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import io.micrometer.core.instrument.MeterRegistry;

import org.apache.geode.Statistics;
import org.apache.geode.StatisticsFactory;
import org.apache.geode.StatisticsType;
import org.apache.geode.annotations.VisibleForTesting;
import org.apache.geode.cache.execute.Function;
import org.apache.geode.distributed.internal.InternalDistributedSystem;
import org.apache.geode.internal.InternalEntity;
import org.apache.geode.internal.statistics.DummyStatisticsImpl;
import org.apache.geode.internal.util.JavaWorkarounds;
import org.apache.geode.metrics.internal.NoopMeterRegistry;

public class FunctionStatsManager {

  private static final StatisticsType functionStatsType = FunctionStatsImpl.getStatisticsType();
  private static final Statistics dummyStatistics =
      new DummyStatisticsImpl(functionStatsType, null, 0);
  private static final FunctionServiceStats dummyFunctionServiceStats =
      FunctionServiceStats.createDummy();
  private static final MeterRegistry noopMeterRegistry = new NoopMeterRegistry();
  private static final FunctionStats dummyFunctionStats = new FunctionStatsImpl("",
      noopMeterRegistry, dummyStatistics, dummyFunctionServiceStats);

  private final boolean statsDisabled;
  private final StatisticsFactory statisticsFactory;
  private final FunctionServiceStats functionServiceStats;
  private final Supplier<MeterRegistry> meterRegistrySupplier;
  private final Map<String, FunctionStats> functionExecutionStatsMap;


  public FunctionStatsManager(boolean statsDisabled, StatisticsFactory statisticsFactory,
      FunctionServiceStats functionServiceStats, Supplier<MeterRegistry> meterRegistrySupplier) {
    this.statsDisabled = statsDisabled;
    this.statisticsFactory = statisticsFactory;
    this.functionServiceStats = functionServiceStats;
    this.meterRegistrySupplier = meterRegistrySupplier;
    this.functionExecutionStatsMap = new ConcurrentHashMap<>();
  }

  @VisibleForTesting
  static FunctionStats getDummyFunctionStats() {
    return dummyFunctionStats;
  }

  @VisibleForTesting
  public static Statistics getDummyStatistics() {
    return dummyStatistics;
  }

  public FunctionStats getFunctionStatsByName(String name) {
    return JavaWorkarounds.computeIfAbsent(functionExecutionStatsMap, name, this::createWithNoopMeters);
  }

  public FunctionStats getFunctionStatsFor(Function function) {
    FunctionStats functionStats = createFromFunction(function);

    return JavaWorkarounds.computeIfAbsent(functionExecutionStatsMap, function.getId(),
        functionId -> functionStats);
  }

  private FunctionStats createFromFunction(Function function) {
    if (statsDisabled) {
      return dummyFunctionStats;
    }

    if (function instanceof InternalEntity) {
      return createWithNoopMeters(function.getId());
    }

    return create(function.getId(), meterRegistrySupplier.get());
  }

  private FunctionStats createWithNoopMeters(String name) {
    return create(name, noopMeterRegistry);
  }

  private FunctionStats create(String name, MeterRegistry meterRegistry) {
    StatisticsType statisticsType = FunctionStatsImpl.getStatisticsType();
    Statistics statistics = statisticsFactory.createAtomicStatistics(statisticsType, name);
    return new FunctionStatsImpl(name, meterRegistrySupplier.get(), statistics,
        functionServiceStats);
  }

  public void close() {
    for (FunctionStats functionstats : functionExecutionStatsMap.values()) {
      functionstats.close();
    }
  }


  /**
   * Returns the Function Stats for the given function
   *
   * @param functionID represents the function for which we are returning the function Stats
   * @param ds represents the Distributed System
   * @return object of the FunctionStats
   */
  public static FunctionStats getFunctionStats(String functionID, InternalDistributedSystem ds) {
    return ds.getFunctionStats(functionID);
  }

  public static FunctionStats getFunctionStats(String functionID) {
    InternalDistributedSystem ds = InternalDistributedSystem.getAnyInstance();
    if (ds == null) {
      return dummyFunctionStats;
    }
    return ds.getFunctionStats(functionID);
  }

  public interface Factory {
    FunctionStatsManager create(boolean statsDisabled, StatisticsFactory statisticsFactory,
        FunctionServiceStats functionServiceStats,
        Supplier<MeterRegistry> meterRegistrySupplier);
  }
}
