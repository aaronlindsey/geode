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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Test;

import org.apache.geode.cache.execute.Function;

public class FunctionInstrumentorTest {

  @Test
  public void instrument_meterRegistryNull_expectOriginalFunction() {
    FunctionInstrumentor functionInstrumentor = new FunctionInstrumentor(() -> null);
    Function<?> originalFunction = mock(Function.class);

    Function<?> value = functionInstrumentor.instrument(originalFunction);

    assertThat(value).isSameAs(originalFunction);
  }

  @Test
  public void instrument_meterRegistryNotNull_expectTimingFunctionWithSameId() {
    MeterRegistry meterRegistry = new SimpleMeterRegistry();
    FunctionInstrumentor functionInstrumentor = new FunctionInstrumentor(() -> meterRegistry);
    Function<?> originalFunction = mock(Function.class);
    when(originalFunction.getId()).thenReturn("foo");

    Function<?> value = functionInstrumentor.instrument(originalFunction);

    assertThat(value).isInstanceOf(TimingFunction.class);
    assertThat(value.getId()).isEqualTo("foo");
  }
}
