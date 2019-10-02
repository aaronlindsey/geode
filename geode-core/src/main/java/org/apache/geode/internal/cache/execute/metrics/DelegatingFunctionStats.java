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

import java.util.concurrent.TimeUnit;

public class DelegatingFunctionStats implements FunctionStats {

  private final FunctionStats innerFunctionStats;
  private final FunctionExecutionsTimer functionExecutionsTimer;

  DelegatingFunctionStats(FunctionStats innerFunctionStats, FunctionExecutionsTimer functionExecutionsTimer) {
    this.innerFunctionStats = innerFunctionStats;
    this.functionExecutionsTimer = functionExecutionsTimer;
  }

  @Override
  public void close() {
    innerFunctionStats.close();
    functionExecutionsTimer.close();
  }

  @Override
  public int getFunctionExecutionsCompleted() {
    return innerFunctionStats.getFunctionExecutionsCompleted();
  }

  @Override
  public int getFunctionExecutionsRunning() {
    return innerFunctionStats.getFunctionExecutionsRunning();
  }

  @Override
  public void incResultsReturned() {
    innerFunctionStats.incResultsReturned();
  }

  @Override
  public int getResultsReceived() {
    return innerFunctionStats.getResultsReceived();
  }

  @Override
  public void incResultsReceived() {
    innerFunctionStats.incResultsReceived();
  }

  @Override
  public int getFunctionExecutionCalls() {
    return innerFunctionStats.getFunctionExecutionCalls();
  }

  @Override
  public long getTime() {
    return innerFunctionStats.getTime();
  }

  @Override
  public void startFunctionExecution(boolean haveResult) {
    innerFunctionStats.startFunctionExecution(haveResult);
  }

  @Override
  public void endFunctionExecution(long elapsed, TimeUnit timeUnit, boolean haveResult) {
    innerFunctionStats.endFunctionExecution(elapsed, timeUnit, haveResult);
    functionExecutionsTimer.record(elapsed, timeUnit, true);
  }

  @Override
  public void endFunctionExecutionWithException(long elapsed, TimeUnit timeUnit, boolean haveResult) {
    innerFunctionStats.endFunctionExecutionWithException(elapsed, timeUnit, haveResult);
    functionExecutionsTimer.record(elapsed, timeUnit, false);
  }
}
