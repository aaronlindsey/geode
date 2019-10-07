/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.geode.metrics.internal;

import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;

public class NoopMeterRegistry extends MeterRegistry {

  private static final Clock NOOP_CLOCK = new Clock() {
    @Override
    public long wallTime() {
      return 0;
    }

    @Override
    public long monotonicTime() {
      return 0;
    }
  };

  public NoopMeterRegistry() {
    this(NOOP_CLOCK);
  }

  private NoopMeterRegistry(Clock clock) {
    super(clock);
  }

  @Override
  protected <T> Gauge newGauge(Meter.Id id, T obj, ToDoubleFunction<T> valueFunction) {
    return null;
  }

  @Override
  protected Counter newCounter(Meter.Id id) {
    return null;
  }

  @Override
  protected LongTaskTimer newLongTaskTimer(Meter.Id id) {
    return null;
  }

  @Override
  protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig,
      PauseDetector pauseDetector) {
    return null;
  }

  @Override
  protected DistributionSummary newDistributionSummary(Meter.Id id,
      DistributionStatisticConfig distributionStatisticConfig, double scale) {
    return null;
  }

  @Override
  protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
    return null;
  }

  @Override
  protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction,
      ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
    return null;
  }

  @Override
  protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj,
      ToDoubleFunction<T> countFunction) {
    return null;
  }

  @Override
  protected TimeUnit getBaseTimeUnit() {
    return null;
  }

  @Override
  protected DistributionStatisticConfig defaultHistogramConfig() {
    return null;
  }
}
