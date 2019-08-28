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
import org.apache.geode.cache.execute.Function;
import org.apache.geode.cache.execute.FunctionException;
import org.apache.geode.distributed.internal.InternalDistributedSystem;
import org.apache.geode.internal.cache.InternalCache;
import org.apache.geode.internal.cache.LocalRegion;
import org.apache.geode.internal.cache.PartitionedRegion;

@RunWith(JUnitParamsRunner.class)
public class InternalFunctionExecutionServiceTest {
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

    Region mockRegion = mock(Region.class);
    RegionAttributes mockAttributes = mock(RegionAttributes.class);
    when(mockAttributes.getPoolName()).thenReturn("testPool");
    when(mockRegion.getAttributes()).thenReturn(mockAttributes);


    assertThatThrownBy(() -> functionExecutionService.onRegion(mockRegion))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Could not find a pool named testPool.");
  }

  @Test
  public void onRegionShouldThrowExceptionWhenMultiUserAuthenticationIsSetForNonProxyRegions() {
    Pool mockPool = mock(Pool.class);
    when(mockPool.getMultiuserAuthentication()).thenReturn(true);
    when(functionExecutionService.findPool(any())).thenReturn(mockPool);

    Region mockRegion = mock(Region.class);
    RegionAttributes mockAttributes = mock(RegionAttributes.class);
    when(mockAttributes.getPoolName()).thenReturn("testPool");
    when(mockRegion.getAttributes()).thenReturn(mockAttributes);

    assertThatThrownBy(() -> functionExecutionService.onRegion(mockRegion))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  public void onRegionShouldReturnClientExecutorImplementationForClientRegions() {
    LocalRegion mockRegion = mock(LocalRegion.class);
    when(mockRegion.hasServerProxy()).thenReturn(true);
    RegionAttributes mockAttributes = mock(RegionAttributes.class);
    when(mockAttributes.getPoolName()).thenReturn(null);
    when(mockRegion.getAttributes()).thenReturn(mockAttributes);

    assertThat(functionExecutionService.onRegion(mockRegion))
        .isInstanceOf(ServerRegionFunctionExecutor.class);
  }

  @Test
  public void onRegionShouldReturnPartitionExecutorImplementationForPartitionedRegions() {
    PartitionedRegion mockRegion = mock(PartitionedRegion.class);
    RegionAttributes mockAttributes = mock(RegionAttributes.class);
    when(mockAttributes.getPoolName()).thenReturn(null);
    when(mockRegion.getAttributes()).thenReturn(mockAttributes);

    assertThat(functionExecutionService.onRegion(mockRegion))
        .isInstanceOf(PartitionedRegionFunctionExecutor.class);
  }

  @Test
  public void registerFunction_cacheIsNull_doesNotThrow() {
    InternalDistributedSystem internalDistributedSystem = mock(InternalDistributedSystem.class);
    Function function = mock(Function.class);
    when(internalDistributedSystem.getCache()).thenReturn(null);
    when(function.getId()).thenReturn("foo");

    InternalFunctionExecutionServiceImpl service =
        new InternalFunctionExecutionServiceImpl(() -> internalDistributedSystem);

    assertThatCode(() -> service.registerFunction(function)).doesNotThrowAnyException();
  }

  @Test
  public void registerFunction_meterRegistryIsNull_doesNotThrow() {
    InternalDistributedSystem internalDistributedSystem = mock(InternalDistributedSystem.class);
    InternalCache internalCache = mock(InternalCache.class);
    Function function = mock(Function.class);
    when(internalDistributedSystem.getCache()).thenReturn(internalCache);
    when(internalCache.getMeterRegistry()).thenReturn(null);
    when(function.getId()).thenReturn("foo");

    InternalFunctionExecutionServiceImpl service =
        new InternalFunctionExecutionServiceImpl(() -> internalDistributedSystem);

    assertThatCode(() -> service.registerFunction(function)).doesNotThrowAnyException();
  }

  @Test
  @Parameters({"true", "false"})
  @TestCaseName("{method}(succeededTagValue={0})")
  public void registerFunction_registersFunctionExecutionsTimer(boolean succeededTagValue) {
    Function function = mock(Function.class);

    when(function.getId()).thenReturn("foo");

    functionExecutionService.registerFunction(function);

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
}
