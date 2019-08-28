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
package org.apache.geode.internal.cache.execute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.Collection;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.naming.TestCaseName;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionAttributes;
import org.apache.geode.cache.client.Pool;
import org.apache.geode.cache.execute.Execution;
import org.apache.geode.cache.execute.Function;
import org.apache.geode.cache.execute.FunctionException;
import org.apache.geode.distributed.internal.InternalDistributedSystem;
import org.apache.geode.internal.cache.InternalCache;
import org.apache.geode.internal.cache.LocalRegion;
import org.apache.geode.internal.cache.PartitionedRegion;

@RunWith(JUnitParamsRunner.class)
public class InternalFunctionExecutionServiceImplTest {

  private InternalFunctionExecutionServiceImpl functionExecutionService;
  private MeterRegistry meterRegistry;

  @Before
  public void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    InternalDistributedSystem internalDistributedSystem = mock(InternalDistributedSystem.class);
    InternalCache internalCache = mock(InternalCache.class);

    when(internalDistributedSystem.getCache()).thenReturn(internalCache);
    when(internalCache.getMeterRegistry()).thenReturn(meterRegistry);

    functionExecutionService =
        spy(new InternalFunctionExecutionServiceImpl(() -> internalDistributedSystem));
  }

  @Test
  public void onRegionShouldThrowExceptionWhenRegionIsNull() {
    assertThatThrownBy(() -> functionExecutionService.onRegion(null))
        .isInstanceOf(FunctionException.class)
        .hasMessage("Region instance passed is null");
  }

  @Test
  public void onRegionShouldThrowExceptionWhenThePoolAssociatedWithTheRegionCanNotBeFound() {
    when(functionExecutionService.findPool(any())).thenReturn(null);

    Region region = mock(Region.class);
    RegionAttributes regionAttributes = mock(RegionAttributes.class);
    when(region.getAttributes()).thenReturn(regionAttributes);
    when(regionAttributes.getPoolName()).thenReturn("testPool");

    assertThatThrownBy(() -> functionExecutionService.onRegion(region))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Could not find a pool named testPool.");
  }

  @Test
  public void onRegionShouldThrowExceptionWhenMultiUserAuthenticationIsSetForNonProxyRegions() {
    Pool pool = mock(Pool.class);
    Region region = mock(Region.class);
    RegionAttributes regionAttributes = mock(RegionAttributes.class);
    when(functionExecutionService.findPool(any())).thenReturn(pool);
    when(pool.getMultiuserAuthentication()).thenReturn(true);
    when(region.getAttributes()).thenReturn(regionAttributes);
    when(regionAttributes.getPoolName()).thenReturn("testPool");

    assertThatThrownBy(() -> functionExecutionService.onRegion(region))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  public void onRegionShouldReturnClientExecutorImplementationForClientRegions() {
    LocalRegion region = mock(LocalRegion.class);
    RegionAttributes regionAttributes = mock(RegionAttributes.class);
    when(region.getAttributes()).thenReturn(regionAttributes);
    when(region.hasServerProxy()).thenReturn(true);
    when(regionAttributes.getPoolName()).thenReturn(null);

    Execution value = functionExecutionService.onRegion(region);

    assertThat(value)
        .isInstanceOf(ServerRegionFunctionExecutor.class);
  }

  @Test
  public void onRegionShouldReturnPartitionExecutorImplementationForPartitionedRegions() {
    PartitionedRegion region = mock(PartitionedRegion.class);
    RegionAttributes regionAttributes = mock(RegionAttributes.class);
    when(region.getAttributes()).thenReturn(regionAttributes);
    when(regionAttributes.getPoolName()).thenReturn(null);

    Execution value = functionExecutionService.onRegion(region);

    assertThat(value)
        .isInstanceOf(PartitionedRegionFunctionExecutor.class);
  }

  @Test
  public void registerFunction_cacheIsNull_doesNotThrow() {
    InternalDistributedSystem internalDistributedSystem = mock(InternalDistributedSystem.class);
    Function function = functionWithId("foo");
    when(internalDistributedSystem.getCache()).thenReturn(null);
    FunctionExecutionService service =
        new InternalFunctionExecutionServiceImpl(() -> internalDistributedSystem);

    assertThatCode(() -> service.registerFunction(function))
        .doesNotThrowAnyException();
  }

  @Test
  public void registerFunction_meterRegistryIsNull_doesNotThrow() {
    InternalDistributedSystem internalDistributedSystem = mock(InternalDistributedSystem.class);
    InternalCache internalCache = mock(InternalCache.class);
    Function function = functionWithId("foo");
    when(internalDistributedSystem.getCache()).thenReturn(internalCache);
    when(internalCache.getMeterRegistry()).thenReturn(null);
    FunctionExecutionService service =
        new InternalFunctionExecutionServiceImpl(() -> internalDistributedSystem);

    assertThatCode(() -> service.registerFunction(function))
        .doesNotThrowAnyException();
  }

  @Test
  @Parameters({"true", "false"})
  @TestCaseName("{method}(succeededTagValue={0})")
  public void registerFunction_registersFunctionExecutionsTimer(boolean succeededTagValue) {
    functionExecutionService.registerFunction(functionWithId("foo"));

    Timer timer = meterRegistry
        .find("geode.function.executions")
        .tag("function", "foo")
        .tag("succeeded", String.valueOf(succeededTagValue))
        .timer();

    assertThat(timer)
        .as("geode.function.executions timer with tags function=foo, succeeded=%s",
            succeededTagValue)
        .isNotNull();
  }

  @Test
  public void unregisterFunction_unregistersFunctionExecutionsTimer() {
    functionExecutionService.registerFunction(functionWithId("foo"));

    functionExecutionService.unregisterFunction("foo");

    Timer timer = meterRegistry
        .find("geode.function.executions")
        .tag("function", "foo")
        .timer();

    assertThat(timer)
        .as("geode.function.executions timer with tag function=foo")
        .isNull();
  }

  @Test
  public void unregisterAllFunctions_unregistersAllFunctionExecutionsTimers() {
    functionExecutionService.registerFunction(functionWithId("foo"));
    functionExecutionService.registerFunction(functionWithId("bar"));

    functionExecutionService.unregisterAllFunctions();

    Collection<Meter> meters = meterRegistry
        .find("geode.function.executions")
        .meters();

    assertThat(meters)
        .as("Collection of all geode.function.executions timers")
        .isEmpty();
  }

  private Function functionWithId(String id) {
    Function function = mock(Function.class);
    when(function.getId()).thenReturn(id);
    return function;
  }
}
