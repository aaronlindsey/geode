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

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.Objects.requireNonNull;

import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.apache.geode.StatisticDescriptor;
import org.apache.geode.Statistics;
import org.apache.geode.StatisticsType;
import org.apache.geode.StatisticsTypeFactory;
import org.apache.geode.annotations.Immutable;
import org.apache.geode.annotations.VisibleForTesting;
import org.apache.geode.distributed.internal.DistributionStats;
import org.apache.geode.internal.NanoTimer;
import org.apache.geode.internal.statistics.StatisticsTypeFactoryImpl;

public class FunctionStatsImpl implements FunctionStats {

  private static final String statName = "FunctionStatistics";

  /**
   * The <code>StatisticsType</code> of the statistics
   */
  @Immutable
  private static final StatisticsType _type;

  /**
   * Total number of completed function.execute() calls (aka invocations of a individual
   * function)Name of the function executions cimpleted statistic
   */
  private static final String FUNCTION_EXECUTIONS_COMPLETED = "functionExecutionsCompleted";

  /**
   * Total time consumed for all completed invocations of a individual function. Name of the
   * function executions completed processing time statistic
   */
  private static final String FUNCTION_EXECUTIONS_COMPLETED_PROCESSING_TIME =
      "functionExecutionsCompletedProcessingTime";

  /**
   * A guage indicating the number of currently running invocations Name of the function executions
   * running statistic
   */
  private static final String FUNCTION_EXECUTIONS_RUNNING = "functionExecutionsRunning";

  /**
   * Total number of results sent to the ResultCollector Name of the results returned statistic
   */
  private static final String RESULTS_SENT_TO_RESULTCOLLECTOR = "resultsSentToResultCollector";

  /**
   * Total number of FunctionService...execute() calls Name of the total function executions call
   * statistic
   */
  private static final String FUNCTION_EXECUTION_CALLS = "functionExecutionCalls";

  /**
   * Total time consumed for all completed execute() calls where hasResult() returns true. Name of
   * the function executions calls having hasResult=true time statistic
   */
  private static final String FUNCTION_EXECUTIONS_HASRESULT_COMPLETED_PROCESSING_TIME =
      "functionExecutionsHasResultCompletedProcessingTime";

  /**
   * A gauge indicating the number of currently active execute() calls for functions where
   * hasResult() returns true. Name of the function execution time statistic
   */
  private static final String FUNCTION_EXECUTIONS_HASRESULT_RUNNING =
      "functionExecutionsHasResultRunning";


  /**
   * Total number of results sent to the ResultCollector Name of the results returned statistic
   */
  private static final String RESULTS_RECEIVED = "resultsReceived";

  /**
   * Total number of Exceptions Occurred while executing function Name of the functionExecution
   * exceptions statistic
   */
  private static final String FUNCTION_EXECUTION_EXCEPTIONS = "functionExecutionsExceptions";

  /**
   * Id of the FUNCTION_EXECUTIONS_COMPLETED statistic
   */
  private static final int _functionExecutionsCompletedId;

  /**
   * Id of the FUNCTION_EXECUTIONS_COMPLETED_PROCESSING_TIME statistic
   */
  private static final int _functionExecutionsCompletedProcessingTimeId;

  /**
   * Id of the FUNCTION_EXECUTIONS_RUNNING statistic
   */
  private static final int _functionExecutionsRunningId;

  /**
   * Id of the RESULTS_SENT_TO_RESULTCOLLECTOR statistic
   */
  private static final int _resultsSentToResultCollectorId;

  /**
   * Id of the FUNCTION_EXECUTIONS_CALL statistic
   */
  private static final int _functionExecutionCallsId;

  /**
   * Id of the FUNCTION_EXECUTION_HASRESULT_COMPLETED_TIME statistic
   */
  private static final int _functionExecutionsHasResultCompletedProcessingTimeId;

  /**
   * Id of the FUNCTION_EXECUTIONS_HASRESULT_RUNNING statistic
   */
  private static final int _functionExecutionsHasResultRunningId;

  /**
   * Id of the RESULTS_RECEIVED statistic
   */
  private static final int _resultsReceived;

  /**
   * Id of the FUNCTION_EXECUTIONS_EXCEPTIONS statistic
   */
  private static final int _functionExecutionExceptionsId;

  /**
   * Static initializer to create and initialize the <code>StatisticsType</code>
   */
  static {

    String statDescription = "This is the stats for the individual Function's Execution";

    StatisticsTypeFactory f = StatisticsTypeFactoryImpl.singleton();

    _type = f.createType(statName, statDescription,
        new StatisticDescriptor[] {f.createIntCounter(FUNCTION_EXECUTIONS_COMPLETED,
            "Total number of completed function.execute() calls for given function", "operations"),

            f.createLongCounter(FUNCTION_EXECUTIONS_COMPLETED_PROCESSING_TIME,
                "Total time consumed for all completed invocations of the given function",
                "nanoseconds"),

            f.createIntGauge(FUNCTION_EXECUTIONS_RUNNING,
                "number of currently running invocations of the given function", "operations"),

            f.createIntCounter(RESULTS_SENT_TO_RESULTCOLLECTOR,
                "Total number of results sent to the ResultCollector", "operations"),

            f.createIntCounter(RESULTS_RECEIVED,
                "Total number of results received and passed to the ResultCollector", "operations"),

            f.createIntCounter(FUNCTION_EXECUTION_CALLS,
                "Total number of FunctionService.execute() calls for given function", "operations"),

            f.createLongCounter(FUNCTION_EXECUTIONS_HASRESULT_COMPLETED_PROCESSING_TIME,
                "Total time consumed for all completed given function.execute() calls where hasResult() returns true.",
                "nanoseconds"),

            f.createIntGauge(FUNCTION_EXECUTIONS_HASRESULT_RUNNING,
                "A gauge indicating the number of currently active execute() calls for functions where hasResult() returns true.",
                "operations"),

            f.createIntCounter(FUNCTION_EXECUTION_EXCEPTIONS,
                "Total number of Exceptions Occurred while executing function", "operations"),
        });

    // Initialize id fields
    _functionExecutionsCompletedId = _type.nameToId(FUNCTION_EXECUTIONS_COMPLETED);
    _functionExecutionsCompletedProcessingTimeId =
        _type.nameToId(FUNCTION_EXECUTIONS_COMPLETED_PROCESSING_TIME);
    _functionExecutionsRunningId = _type.nameToId(FUNCTION_EXECUTIONS_RUNNING);
    _resultsSentToResultCollectorId = _type.nameToId(RESULTS_SENT_TO_RESULTCOLLECTOR);
    _functionExecutionCallsId = _type.nameToId(FUNCTION_EXECUTION_CALLS);
    _functionExecutionsHasResultCompletedProcessingTimeId =
        _type.nameToId(FUNCTION_EXECUTIONS_HASRESULT_COMPLETED_PROCESSING_TIME);
    _functionExecutionsHasResultRunningId = _type.nameToId(FUNCTION_EXECUTIONS_HASRESULT_RUNNING);
    _functionExecutionExceptionsId = _type.nameToId(FUNCTION_EXECUTION_EXCEPTIONS);
    _resultsReceived = _type.nameToId(RESULTS_RECEIVED);
  }

  private final MeterRegistry meterRegistry;
  private final Statistics _stats;
  private final FunctionServiceStats aggregateStats;
  private final LongSupplier clock;
  private final BooleanSupplier enableClockStats;
  private final Timer successTimer;
  private final Timer failureTimer;

  private boolean isClosed = false;

  FunctionStatsImpl(String functionId, MeterRegistry meterRegistry, Statistics statistics,
      FunctionServiceStats functionServiceStats) {
    this(functionId, meterRegistry, statistics, functionServiceStats, NanoTimer::getTime,
        () -> DistributionStats.enableClockStats, FunctionStatsImpl::registerSuccessTimer,
        FunctionStatsImpl::registerFailureTimer);
  }

  @VisibleForTesting
  FunctionStatsImpl(String functionId, MeterRegistry meterRegistry, Statistics stats,
      FunctionServiceStats aggregateStats, long clockResult, boolean enableClockStatsResult) {
    this(functionId, meterRegistry, stats, aggregateStats, () -> clockResult,
        () -> enableClockStatsResult, FunctionStatsImpl::registerSuccessTimer,
        FunctionStatsImpl::registerFailureTimer);
  }

  @VisibleForTesting
  FunctionStatsImpl(String functionId, MeterRegistry meterRegistry, Statistics stats,
      FunctionServiceStats aggregateStats, long clockResult, boolean enableClockStatsResult,
      Timer successTimerResult, Timer registerFailureResult) {
    this(functionId, meterRegistry, stats, aggregateStats, () -> clockResult,
        () -> enableClockStatsResult, (a, b) -> successTimerResult,
        (a, b) -> registerFailureResult);
  }

  private FunctionStatsImpl(String functionId, MeterRegistry meterRegistry, Statistics stats,
      FunctionServiceStats aggregateStats, LongSupplier clock, BooleanSupplier enableClockStats,
      BiFunction<String, MeterRegistry, Timer> registerSuccessTimerFunction,
      BiFunction<String, MeterRegistry, Timer> registerFailureTimerFunction) {

    requireNonNull(meterRegistry);

    this.meterRegistry = meterRegistry;
    this._stats = stats;
    this.aggregateStats = aggregateStats;
    this.clock = clock;
    this.enableClockStats = enableClockStats;

    successTimer = registerSuccessTimerFunction.apply(functionId, meterRegistry);
    failureTimer = registerFailureTimerFunction.apply(functionId, meterRegistry);
  }

  public static StatisticsType getStatisticsType() {
    return _type;
  }

  @Override
  public void close() {
    meterRegistry.remove(successTimer);
    successTimer.close();

    meterRegistry.remove(failureTimer);
    failureTimer.close();
    this._stats.close();
    isClosed = true;
  }

  @Override
  public boolean isClosed() {
    return isClosed;
  }

  @Override
  public int getFunctionExecutionsCompleted() {
    return this._stats.getInt(_functionExecutionsCompletedId);
  }

  @Override
  public int getFunctionExecutionsRunning() {
    return this._stats.getInt(_functionExecutionsRunningId);
  }

  @Override
  public void incResultsReturned() {
    this._stats.incInt(_resultsSentToResultCollectorId, 1);
    aggregateStats.incResultsReturned();
  }

  @Override
  public int getResultsReceived() {
    return this._stats.getInt(_resultsReceived);
  }

  @Override
  public void incResultsReceived() {
    this._stats.incInt(_resultsReceived, 1);
    aggregateStats.incResultsReceived();
  }

  @Override
  public int getFunctionExecutionCalls() {
    return this._stats.getInt(_functionExecutionCallsId);
  }

  @Override
  public long getTime() {
    return clock.getAsLong();
  }

  @Override
  public void startFunctionExecution(boolean haveResult) {
    // Increment number of function execution calls
    this._stats.incInt(_functionExecutionCallsId, 1);

    // Increment number of functions running
    this._stats.incInt(_functionExecutionsRunningId, 1);

    if (haveResult) {
      // Increment number of function excution with haveResult = true call
      this._stats.incInt(_functionExecutionsHasResultRunningId, 1);
    }
    aggregateStats.startFunctionExecution(haveResult);
  }

  @Override
  public void recordSuccessfulExecution(long elapsed, TimeUnit timeUnit, boolean haveResult) {
    successTimer.record(elapsed, timeUnit);

    // Increment number of function executions completed
    this._stats.incInt(_functionExecutionsCompletedId, 1);

    // Decrement function Executions running.
    this._stats.incInt(_functionExecutionsRunningId, -1);

    if (enableClockStats.getAsBoolean()) {
      // Increment function execution complete processing time
      this._stats.incLong(_functionExecutionsCompletedProcessingTimeId, elapsed);
    }

    if (haveResult) {
      // Decrement function Executions with haveResult = true running.
      this._stats.incInt(_functionExecutionsHasResultRunningId, -1);

      if (enableClockStats.getAsBoolean()) {
        // Increment function execution with haveResult = true complete processing time
        this._stats.incLong(_functionExecutionsHasResultCompletedProcessingTimeId, elapsed);
      }
    }

    aggregateStats.endFunctionExecution(elapsed, haveResult);
  }

  @Override
  public void recordFailedExecution(long elapsed, TimeUnit timeUnit, boolean haveResult) {
    failureTimer.record(elapsed, timeUnit);

    // Decrement function Executions running.
    this._stats.incInt(_functionExecutionsRunningId, -1);

    // Increment number of function excution exceptions
    this._stats.incInt(_functionExecutionExceptionsId, 1);

    if (haveResult) {
      // Decrement function Executions with haveResult = true running.
      this._stats.incInt(_functionExecutionsHasResultRunningId, -1);
    }
    aggregateStats.endFunctionExecutionWithException(haveResult);
  }

  @Override
  @VisibleForTesting
  public Statistics getStatistics() {
    return _stats;
  }

  @Override
  @VisibleForTesting
  public MeterRegistry getMeterRegistry() {
    return meterRegistry;
  }

  @VisibleForTesting
  static int functionExecutionsCompletedId() {
    return _functionExecutionsCompletedId;
  }

  @VisibleForTesting
  static int functionExecutionsRunningId() {
    return _functionExecutionsRunningId;
  }

  @VisibleForTesting
  static int functionExecutionsHasResultRunningId() {
    return _functionExecutionsHasResultRunningId;
  }

  @VisibleForTesting
  static int functionExecutionsCompletedProcessingTimeId() {
    return _functionExecutionsCompletedProcessingTimeId;
  }

  @VisibleForTesting
  static int functionExecutionsHasResultCompletedProcessingTimeId() {
    return _functionExecutionsHasResultCompletedProcessingTimeId;
  }

  @VisibleForTesting
  static int functionExecutionExceptionsId() {
    return _functionExecutionExceptionsId;
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
