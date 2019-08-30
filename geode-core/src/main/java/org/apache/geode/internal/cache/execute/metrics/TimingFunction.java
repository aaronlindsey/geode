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

import java.util.Collection;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.apache.geode.cache.execute.Function;
import org.apache.geode.cache.execute.FunctionContext;
import org.apache.geode.security.ResourcePermission;

public class TimingFunction<T> implements Function<T>, AutoCloseable {

  private final Function<T> innerFunction;
  private final MeterRegistry meterRegistry;
  private final Timer successTimer;
  private final Timer failureTimer;

  public TimingFunction(Function<T> innerFunction, MeterRegistry meterRegistry) {
    this.innerFunction = innerFunction;
    this.meterRegistry = meterRegistry;

    successTimer = Timer.builder("geode.function.executions")
        .description("")
        .tag("function", innerFunction.getId())
        .tag("succeeded", TRUE.toString())
        .register(meterRegistry);

    failureTimer = Timer.builder("geode.function.executions")
        .description("")
        .tag("function", innerFunction.getId())
        .tag("succeeded", FALSE.toString())
        .register(meterRegistry);
  }

  @Override
  public void execute(FunctionContext<T> context) {
    // long startTime = System.nanoTime();
    // try {
    // innerFunction.execute(context);
    // successTimer.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
    // } catch (Throwable ignored) {
    // failureTimer.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
    // }
  }

  @Override
  public boolean hasResult() {
    return innerFunction.hasResult();
  }

  @Override
  public String getId() {
    return innerFunction.getId();
  }

  @Override
  public boolean optimizeForWrite() {
    return innerFunction.optimizeForWrite();
  }

  @Override
  public boolean isHA() {
    return innerFunction.isHA();
  }

  @Override
  public Collection<ResourcePermission> getRequiredPermissions(String regionName) {
    return innerFunction.getRequiredPermissions(regionName);
  }

  @Override
  public Collection<ResourcePermission> getRequiredPermissions(String regionName, Object args) {
    return innerFunction.getRequiredPermissions(regionName, args);
  }

  @Override
  public void close() {
    meterRegistry
        .find("geode.function.executions")
        .tag("function", getId())
        .meters()
        .forEach(meterRegistry::remove);
  }
}
