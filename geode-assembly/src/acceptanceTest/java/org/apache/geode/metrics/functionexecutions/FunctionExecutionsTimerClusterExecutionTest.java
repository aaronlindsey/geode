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
package org.apache.geode.metrics.functionexecutions;


import static java.io.File.pathSeparatorChar;
import static java.lang.Boolean.TRUE;
import static java.util.stream.Collectors.toList;
import static org.apache.geode.test.compiler.ClassBuilder.writeJarFromClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.geode.cache.client.Pool;
import org.apache.geode.cache.client.PoolManager;
import org.apache.geode.cache.execute.Execution;
import org.apache.geode.cache.execute.Function;
import org.apache.geode.cache.execute.FunctionService;
import org.apache.geode.internal.AvailablePortHelper;
import org.apache.geode.metrics.MetricsPublishingService;
import org.apache.geode.metrics.SimpleMetricsPublishingService;
import org.apache.geode.rules.ServiceJarRule;
import org.apache.geode.test.junit.rules.gfsh.GfshRule;

/**
 * Acceptance tests for function executions timer on cluster with two servers and one locator
 */
public class FunctionExecutionsTimerClusterExecutionTest {

  private int locatorPort;
  private ClientCache clientCache;
  private Pool multiServerPool;
  private Region<Object, Object> replicateRegion;

  @Rule
  public GfshRule gfshRule = new GfshRule();

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Rule
  public ServiceJarRule serviceJarRule = new ServiceJarRule();

  @Before
  public void setUp() throws IOException {
    int[] availablePorts = AvailablePortHelper.getRandomAvailableTCPPorts(3);

    locatorPort = availablePorts[0];
    int server1Port = availablePorts[1];
    int server2Port = availablePorts[2];

    Path serviceJarPath = serviceJarRule.createJarFor("metrics-publishing-service.jar",
        MetricsPublishingService.class, SimpleMetricsPublishingService.class);

    Path functionsJarPath = temporaryFolder.getRoot().toPath()
        .resolve("functions.jar").toAbsolutePath();
    writeJarFromClasses(functionsJarPath.toFile(),
        GetFunctionExecutionTimerValues.class, FunctionToTime.class, ExecutionsTimerValues.class);

    String startLocatorCommand = String.join(" ",
        "start locator",
        "--name=" + "locator",
        "--dir=" + temporaryFolder.newFolder("locator").getAbsolutePath(),
        "--port=" + locatorPort);

    String startServer1Command = startServerCommand("server1", server1Port, serviceJarPath, functionsJarPath);
    String startServer2Command = startServerCommand("server2", server2Port, serviceJarPath, functionsJarPath);

    String replicateRegionName = "region";
    String createRegionCommand = "create region --type=REPLICATE --name=" + replicateRegionName;

    gfshRule.execute(startLocatorCommand, startServer1Command, startServer2Command,
        createRegionCommand);

    clientCache = new ClientCacheFactory().create();

    multiServerPool = PoolManager.createFactory()
        .addServer("localhost", server1Port)
        .addServer("localhost", server2Port)
        .create("multiServerPool");

    replicateRegion = clientCache
        .createClientRegionFactory(ClientRegionShortcut.PROXY)
        .setPoolName(multiServerPool.getName())
        .create(replicateRegionName);
  }

  @After
  public void tearDown() {
    replicateRegion.close();
    multiServerPool.destroy();
    clientCache.close();

    String connectToLocatorCommand = "connect --locator=localhost[" + locatorPort + "]";
    String shutdownCommand = "shutdown --include-locators=true";
    gfshRule.execute(connectToLocatorCommand, shutdownCommand);
  }

  @Test
  public void replicateRegionExecutionIncrementsTimerOnOnlyOneServer() {
    FunctionToTime function = new FunctionToTime();
    Duration functionDuration = Duration.ofSeconds(1);
    executeFunctionOnReplicateRegion(function, functionDuration);

    List<ExecutionsTimerValues> values = getAllExecutionsTimerValues(function.getId());

    long totalCount = values.stream().map(x -> x.count).reduce(0L, Long::sum);
    double totalTime = values.stream().map(x -> x.totalTime).reduce(0.0, Double::sum);

    assertThat(totalCount)
        .as("Number of function executions across all servers")
        .isEqualTo(1);

    assertThat(totalTime)
        .as("Total time of function executions across all servers")
        .isBetween((double) functionDuration.toNanos(), ((double) functionDuration.toNanos()) * 2);
  }

  @Test
  public void mutlipleReplicateRegionExecutionsIncrementsTimers() {
    FunctionToTime function = new FunctionToTime();
    Duration functionDuration = Duration.ofSeconds(1);
    int numberOfExecutions = 10;

    for (int i = 0; i < numberOfExecutions; i++) {
      executeFunctionOnReplicateRegion(function, functionDuration);
    }

    List<ExecutionsTimerValues> values = getAllExecutionsTimerValues(function.getId());

    long totalCount = values.stream().map(x -> x.count).reduce(0L, Long::sum);
    double totalTime = values.stream().map(x -> x.totalTime).reduce(0.0, Double::sum);

    double expectedMinimumTotalTime = ((double) functionDuration.toNanos()) * numberOfExecutions;
    double expectedMaximumTotalTime = expectedMinimumTotalTime * 2;

    assertThat(totalCount)
        .as("Number of function executions across all servers")
        .isEqualTo(numberOfExecutions);

    assertThat(totalTime)
        .as("Total time of function executions across all servers")
        .isBetween(expectedMinimumTotalTime, expectedMaximumTotalTime);
  }

  private String startServerCommand(String serverName, int serverPort, Path serviceJarPath, Path functionsJarPath)
      throws IOException {
    return String.join(" ",
        "start server",
        "--name=" + serverName,
        "--groups=" + serverName,
        "--dir=" + temporaryFolder.newFolder(serverName).getAbsolutePath(),
        "--server-port=" + serverPort,
        "--locators=localhost[" + locatorPort + "]",
        "--classpath=" + serviceJarPath + pathSeparatorChar + functionsJarPath);
  }

  private void executeFunctionOnReplicateRegion(Function<? super String[]> function, Duration duration) {
    @SuppressWarnings("unchecked")
    Execution<String[], Object, List<Object>> execution =
        (Execution<String[], Object, List<Object>>) FunctionService.onRegion(replicateRegion);

    execution
        .setArguments(new String[] {String.valueOf(duration.toMillis()), TRUE.toString()})
        .execute(function)
        .getResult();
  }

  private List<ExecutionsTimerValues> getAllExecutionsTimerValues(String functionId) {
    @SuppressWarnings("unchecked")
    Execution<Void, List<ExecutionsTimerValues>, List<List<ExecutionsTimerValues>>> functionExecution =
        (Execution<Void, List<ExecutionsTimerValues>, List<List<ExecutionsTimerValues>>>) FunctionService
            .onServers(multiServerPool);

    List<List<ExecutionsTimerValues>> timerValuesForEachServer = functionExecution
        .execute(new GetFunctionExecutionTimerValues())
        .getResult();

    return timerValuesForEachServer.stream()
        .flatMap(List::stream)
        .filter(v -> v.functionId.equals(functionId))
        .collect(toList());
  }
}
