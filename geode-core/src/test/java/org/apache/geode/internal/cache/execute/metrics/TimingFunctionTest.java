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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Collections;

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

import org.apache.geode.cache.execute.Function;
import org.apache.geode.security.ResourcePermission;

public class TimingFunctionTest {

  private static final String INNER_FUNCTION_ID = "InnerFunction";

  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  @Mock
  private Function<Void> innerFunction;

  private MeterRegistry meterRegistry;

  @Before
  public void setUp() {
    meterRegistry = new SimpleMeterRegistry();

    when(innerFunction.getId()).thenReturn(INNER_FUNCTION_ID);
  }

  @After
  public void tearDown() {
    meterRegistry.close();
  }

  @Test
  public void registersSuccessTimer() {
    new TimingFunction<>(innerFunction, meterRegistry);

    Timer timer = meterRegistry
        .find("geode.function.executions")
        .tag("function", INNER_FUNCTION_ID)
        .tag("succeeded", "true")
        .timer();

    assertThat(timer)
        .as("geode.function.executions timer with tags function=%s, succeeded=true",
            INNER_FUNCTION_ID)
        .isNotNull();
  }

  @Test
  public void registersFailureTimer() {
    new TimingFunction<>(innerFunction, meterRegistry);

    Timer timer = meterRegistry
        .find("geode.function.executions")
        .tag("function", INNER_FUNCTION_ID)
        .tag("succeeded", "false")
        .timer();

    assertThat(timer)
        .as("geode.function.executions timer with tags function=%s, succeeded=false",
            INNER_FUNCTION_ID)
        .isNotNull();
  }

  @Test
  public void close_removesSuccessTimer() {
    TimingFunction<Void> timingFunction = new TimingFunction<>(innerFunction, meterRegistry);

    timingFunction.close();

    Timer timer = meterRegistry
        .find("geode.function.executions")
        .tag("function", INNER_FUNCTION_ID)
        .tag("succeeded", "true")
        .timer();

    assertThat(timer)
        .as("geode.function.executions timer with tags function=%s, succeeded=true",
            INNER_FUNCTION_ID)
        .isNull();
  }

  @Test
  public void close_removesFailureTimer() {
    TimingFunction<Void> timingFunction = new TimingFunction<>(innerFunction, meterRegistry);

    timingFunction.close();

    Timer timer = meterRegistry
        .find("geode.function.executions")
        .tag("function", INNER_FUNCTION_ID)
        .tag("succeeded", "false")
        .timer();

    assertThat(timer)
        .as("geode.function.executions timer with tags function=%s, succeeded=false",
            INNER_FUNCTION_ID)
        .isNull();
  }

  @Test
  public void hasResult_delegatesToInnerFunction() {
    when(innerFunction.hasResult()).thenReturn(true);
    TimingFunction<Void> timingFunction = new TimingFunction<>(innerFunction, meterRegistry);

    boolean value = timingFunction.hasResult();

    assertThat(value).isTrue();
    verify(innerFunction).hasResult();
  }

  @Test
  public void getId_delegatesToInnerFunction() {
    TimingFunction<Void> timingFunction = new TimingFunction<>(innerFunction, meterRegistry);

    String value = timingFunction.getId();

    assertThat(value).isEqualTo(INNER_FUNCTION_ID);
    verify(innerFunction, atLeastOnce()).getId();
  }

  @Test
  public void optimizeForWrite_delegatesToInnerFunction() {
    when(innerFunction.optimizeForWrite()).thenReturn(true);
    TimingFunction<Void> timingFunction = new TimingFunction<>(innerFunction, meterRegistry);

    boolean value = timingFunction.optimizeForWrite();

    assertThat(value).isTrue();
    verify(innerFunction).optimizeForWrite();
  }

  @Test
  public void isHA_delegatesToInnerFunction() {
    when(innerFunction.isHA()).thenReturn(true);
    TimingFunction<Void> timingFunction = new TimingFunction<>(innerFunction, meterRegistry);

    boolean value = timingFunction.isHA();

    assertThat(value).isTrue();
    verify(innerFunction).isHA();
  }

  @Test
  public void getRequiredPermissions_delegatesToInnerFunction() {
    ResourcePermission resourcePermission = mock(ResourcePermission.class);
    when(innerFunction.getRequiredPermissions(any()))
        .thenReturn(Collections.singleton(resourcePermission));
    TimingFunction<Void> timingFunction = new TimingFunction<>(innerFunction, meterRegistry);

    Collection<ResourcePermission> value = timingFunction.getRequiredPermissions("foo");

    assertThat(value).containsExactly(resourcePermission);
    verify(innerFunction).getRequiredPermissions(eq("foo"));
  }

  @Test
  public void getRequiredPermissions_withArgs_delegatesToInnerFunction() {
    ResourcePermission resourcePermission = mock(ResourcePermission.class);
    when(innerFunction.getRequiredPermissions(any(), any()))
        .thenReturn(Collections.singleton(resourcePermission));
    TimingFunction<Void> timingFunction = new TimingFunction<>(innerFunction, meterRegistry);

    Collection<ResourcePermission> value =
        timingFunction.getRequiredPermissions("foo", new String[] {"bar"});

    assertThat(value).containsExactly(resourcePermission);
    verify(innerFunction).getRequiredPermissions(eq("foo"), eq(new String[] {"bar"}));
  }
}
