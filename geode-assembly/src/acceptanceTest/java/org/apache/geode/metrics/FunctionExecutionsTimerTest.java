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
package org.apache.geode.metrics;


import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.naming.TestCaseName;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.geode.cache.client.Pool;
import org.apache.geode.cache.client.PoolManager;
import org.apache.geode.cache.execute.Execution;
import org.apache.geode.cache.execute.Function;
import org.apache.geode.cache.execute.FunctionContext;
import org.apache.geode.cache.execute.FunctionException;
import org.apache.geode.cache.execute.FunctionService;
import org.apache.geode.internal.AvailablePortHelper;
import org.apache.geode.rules.ServiceJarRule;
import org.apache.geode.test.compiler.ClassBuilder;
import org.apache.geode.test.junit.rules.gfsh.GfshRule;

@RunWith(JUnitParamsRunner.class)
public class FunctionExecutionsTimerTest {

  private ClientCache clientCache;
  private Pool server1Pool;
  private Pool multiServerPool;
  private Region<Object, Object> replicateRegion;
  private String connectToLocatorCommand;
  private String startServer1Command;
  private String stopServer1Command;

  @Rule
  public GfshRule gfshRule = new GfshRule();

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Rule
  public ServiceJarRule serviceJarRule = new ServiceJarRule();
  private int server1Port;

  @Before
  public void setUp() throws IOException {
    int[] availablePorts = AvailablePortHelper.getRandomAvailableTCPPorts(3);

    int locatorPort = availablePorts[0];
    server1Port = availablePorts[1];
    int server2Port = availablePorts[2];

    File server1Folder = temporaryFolder.newFolder("server1");
    File server2Folder = temporaryFolder.newFolder("server2");

    Path serviceJarPath = serviceJarRule.createJarFor("metrics-publishing-service.jar",
        MetricsPublishingService.class, SimpleMetricsPublishingService.class);

    String startLocatorCommand = String.join(" ",
        "start locator",
        "--name=" + "locator",
        "--dir=" + temporaryFolder.newFolder("locator").getAbsolutePath(),
        "--port=" + locatorPort);

    startServer1Command = startServerCommand("server1", server1Port, server1Folder, locatorPort,
        serviceJarPath);
    String startServer2Command = startServerCommand("server2", server2Port, server2Folder,
        locatorPort, serviceJarPath);

    String replicateRegionName = "region";
    String createRegionCommand = "create region --type=REPLICATE --name=" + replicateRegionName;

    gfshRule.execute(startLocatorCommand, startServer1Command, startServer2Command,
        createRegionCommand, deployFunctionCommand(GetExecutionsTimerFunction.class));

    clientCache = new ClientCacheFactory().create();

    server1Pool = PoolManager.createFactory()
        .addServer("localhost", server1Port)
        .create("server1");

    multiServerPool = PoolManager.createFactory()
        .addServer("localhost", server1Port)
        .addServer("localhost", server2Port)
        .create("multiServerPool");

    replicateRegion = clientCache
        .createClientRegionFactory(ClientRegionShortcut.PROXY)
        .setPoolName(multiServerPool.getName())
        .create(replicateRegionName);

    connectToLocatorCommand = "connect --locator=localhost[" + locatorPort + "]";
    stopServer1Command = "stop server --dir=" + server1Folder.getAbsolutePath();
  }

  @After
  public void tearDown() {
    if (!replicateRegion.isDestroyed()) {
      replicateRegion.close();
    }

    if (!multiServerPool.isDestroyed()) {
      multiServerPool.destroy();
    }

    if (!server1Pool.isDestroyed()) {
      server1Pool.destroy();
    }

    clientCache.close();

    String shutdownCommand = "shutdown --include-locators=true";
    gfshRule.execute(connectToLocatorCommand, shutdownCommand);
  }

  @Test
  @Parameters({"true", "false"})
  @TestCaseName("{method}(succeededTagValue={0})")
  public void functionExists_notExecuted_expectZeroExecutions(boolean succeededTagValue)
      throws IOException {
    gfshRule.execute(connectToLocatorCommand, deployFunctionCommand(FunctionToTime.class));

    ExecutionsTimerValues result =
        getTimerValuesFromServer1(FunctionToTime.ID, String.valueOf(succeededTagValue));

    assertThat(result.count)
        .as("Function execution count")
        .isEqualTo(0);

    assertThat(result.totalTime)
        .as("Function execution total time")
        .isEqualTo(0);
  }

  @Test
  public void functionExists_undeployJar_expectMetersRemoved() throws IOException {
    gfshRule.execute(connectToLocatorCommand, deployFunctionCommand(FunctionToTime.class));

    replicateRegion.close();
    multiServerPool.destroy();
    server1Pool.destroy();

    gfshRule.execute(connectToLocatorCommand, undeployFunctionCommand(FunctionToTime.class),
        stopServer1Command, startServer1Command);

    server1Pool = PoolManager.createFactory()
        .addServer("localhost", server1Port)
        .create("server1");

    ExecutionsTimerValues result = getTimerValuesFromServer1(FunctionToTime.ID);

    Assertions.assertThat(result)
        .as("Function execution timers")
        .isNull();
  }

  @Test
  public void meterRecordsCountAndTotalTimeIfFunctionSucceeds() throws IOException {
    gfshRule.execute(connectToLocatorCommand, deployFunctionCommand(FunctionToTime.class));

    Duration functionDuration = Duration.ofSeconds(1);
    executeFunctionThatSucceeds(FunctionToTime.ID, functionDuration);

    String succeededTagValue = TRUE.toString();
    ExecutionsTimerValues result = getTimerValuesFromServer1(FunctionToTime.ID, succeededTagValue);

    assertThat(result.count)
        .as("Function execution count")
        .isEqualTo(1);

    assertThat(result.totalTime)
        .as("Function execution total time")
        .isGreaterThan(functionDuration.toNanos());
  }

  @Test
  public void meterRecordsCountAndTotalTimeIfFunctionThrows() throws IOException {
    gfshRule.execute(connectToLocatorCommand, deployFunctionCommand(FunctionToTime.class));

    Duration functionDuration = Duration.ofSeconds(1);
    executeFunctionThatThrows(FunctionToTime.ID, functionDuration);

    String succeededTagValue = FALSE.toString();
    ExecutionsTimerValues result = getTimerValuesFromServer1(FunctionToTime.ID, succeededTagValue);

    assertThat(result.count)
        .as("Function execution count")
        .isEqualTo(1);

    assertThat(result.totalTime)
        .as("Function execution total time")
        .isGreaterThan(functionDuration.toNanos());
  }

  @Test
  public void replicateRegionExecutionIncrementsMeterOnOnlyOneServer() throws IOException {
    gfshRule.execute(connectToLocatorCommand, deployFunctionCommand(FunctionToTime.class));

    Duration functionDuration = Duration.ofSeconds(1);
    executeFunctionOnReplicateRegion(FunctionToTime.ID, functionDuration);

    List<ExecutionsTimerValues> values = getTimerValuesFromAllServers(FunctionToTime.ID);

    long totalCount = values.stream().map(x -> x.count).reduce(0L, Long::sum);
    double totalTime = values.stream().map(x -> x.totalTime).reduce(0.0, Double::sum);

    assertThat(values)
        .as("Execution timer values for each server")
        .hasSize(2);

    assertThat(totalCount)
        .as("Number of function executions across all servers")
        .isEqualTo(1);

    assertThat(totalTime)
        .as("Total time of function executions across all servers")
        .isBetween((double) functionDuration.toNanos(), ((double) functionDuration.toNanos()) * 2);
  }

  @Test
  public void mutlipleReplicateRegionExecutionsIncrementsMeters() throws IOException {
    gfshRule.execute(connectToLocatorCommand, deployFunctionCommand(FunctionToTime.class));

    Duration functionDuration = Duration.ofSeconds(1);
    int numberOfExecutions = 10;

    for (int i = 0; i < numberOfExecutions; i++) {
      executeFunctionOnReplicateRegion(FunctionToTime.ID, functionDuration);
    }

    List<ExecutionsTimerValues> values = getTimerValuesFromAllServers(FunctionToTime.ID);

    long totalCount = values.stream().map(x -> x.count).reduce(0L, Long::sum);
    double totalTime = values.stream().map(x -> x.totalTime).reduce(0.0, Double::sum);

    assertThat(values)
        .as("Execution timer values for each server")
        .hasSize(2);

    assertThat(totalCount)
        .as("Number of function executions across all servers")
        .isEqualTo(numberOfExecutions);

    double expectedMinimumTotalTime = ((double) functionDuration.toNanos()) * numberOfExecutions;
    double expectedMaximumTotalTime = expectedMinimumTotalTime * 2;

    assertThat(totalTime)
        .as("Total time of function executions across all servers")
        .isBetween(expectedMinimumTotalTime, expectedMaximumTotalTime);
  }

  private void executeFunctionThatSucceeds(String functionId, Duration duration) {
    @SuppressWarnings("unchecked")
    Execution<String[], String, List<String>> execution =
        (Execution<String[], String, List<String>>) FunctionService.onServer(server1Pool);

    Throwable thrown = catchThrowable(() -> execution
        .setArguments(new String[] {String.valueOf(duration.toMillis()), TRUE.toString()})
        .execute(functionId)
        .getResult());

    assertThat(thrown)
        .as("Exception from function expected to succeed")
        .isNull();
  }

  private void executeFunctionThatThrows(String functionId, Duration duration) {
    @SuppressWarnings("unchecked")
    Execution<String[], String, List<String>> execution =
        (Execution<String[], String, List<String>>) FunctionService.onServer(server1Pool);

    Throwable thrown = catchThrowable(() -> execution
        .setArguments(new String[] {String.valueOf(duration.toMillis()), FALSE.toString()})
        .execute(functionId)
        .getResult());

    assertThat(thrown)
        .withFailMessage("Expected function to throw but it did not")
        .isNotNull();
  }

  private void executeFunctionOnReplicateRegion(String functionId, Duration duration) {
    @SuppressWarnings("unchecked")
    Execution<String[], String, List<String>> execution =
        (Execution<String[], String, List<String>>) FunctionService.onRegion(replicateRegion);

    Throwable thrown = catchThrowable(() -> execution
        .setArguments(new String[] {String.valueOf(duration.toMillis()), TRUE.toString()})
        .execute(FunctionToTime.ID)
        .getResult());

    assertThat(thrown)
        .as("Exception from function expected to succeed")
        .isNull();
  }

  private ExecutionsTimerValues getTimerValuesFromServer1(String... args) {
    List<ExecutionsTimerValues> values = getTimerValuesFromPool(server1Pool, args);

    assertThat(values)
        .hasSize(1);

    return values.get(0);
  }

  private List<ExecutionsTimerValues> getTimerValuesFromAllServers(String... args) {
    return getTimerValuesFromPool(multiServerPool, args);
  }

  private List<ExecutionsTimerValues> getTimerValuesFromPool(Pool serverPool, String... args) {
    @SuppressWarnings("unchecked")
    Execution<String[], Number[], List<Number[]>> execution =
        (Execution<String[], Number[], List<Number[]>>) FunctionService.onServers(serverPool);

    List<Number[]> result = execution
        .setArguments(args)
        .execute(GetExecutionsTimerFunction.ID)
        .getResult();

    return result.stream()
        .map(x -> x == null ? null : new ExecutionsTimerValues((long) x[0], (double) x[1]))
        .collect(toList());
  }

  private String startServerCommand(String serverName, int serverPort, File serverFolder, int locatorPort, Path serviceJarPath) {
    return String.join(" ",
        "start server",
        "--name=" + serverName,
        "--groups=" + serverName,
        "--dir=" + serverFolder.getAbsolutePath(),
        "--server-port=" + serverPort,
        "--locators=localhost[" + locatorPort + "]",
        "--classpath=" + serviceJarPath);
  }

  private <T> String deployFunctionCommand(Class<? extends Function<T>> function)
      throws IOException {
    Path jarPath = temporaryFolder.getRoot().toPath().resolve(function.getSimpleName() + ".jar").toAbsolutePath();

    new ClassBuilder().writeJarFromClass(function, jarPath.toFile());

    return "deploy --jar=" + jarPath.toAbsolutePath();
  }

  private <T> String undeployFunctionCommand(Class<? extends Function<T>> function) {
    return "undeploy --jar=" + function.getSimpleName() + ".jar";
  }

  public static class FunctionToTime implements Function<String[]> {
    private static final String ID = "FunctionToTime";

    @Override
    public void execute(FunctionContext<String[]> context) {
      String[] arguments = context.getArguments();
      long timeToSleep = Long.parseLong(arguments[0]);
      boolean successful = Boolean.parseBoolean(arguments[1]);

      try {
        Thread.sleep(timeToSleep);
      } catch (InterruptedException ignored) {
      }

      if (successful) {
        context.getResultSender().lastResult("OK");
      } else {
        throw new FunctionException("FAIL");
      }
    }

    @Override
    public String getId() {
      return ID;
    }
  }

  public static class GetExecutionsTimerFunction implements Function<String[]> {
    private static final String ID = "GetExecutionsTimerFunction";

    @Override
    public void execute(FunctionContext<String[]> context) {
      String[] arguments = context.getArguments();
      String id = arguments[0];
      String succeeded = arguments.length > 1 ? arguments[1] : null;

      Search meterSearch = SimpleMetricsPublishingService.getRegistry()
          .find("geode.function.executions")
          .tag("function", id);

      if (succeeded != null) {
        meterSearch = meterSearch.tag("succeeded", succeeded);
      }

      Timer memberFunctionExecutionsTimer = meterSearch
          .timer();

      if (memberFunctionExecutionsTimer == null) {
        context.getResultSender().lastResult(null);
      } else {
        Number[] result = new Number[] {
            memberFunctionExecutionsTimer.count(),
            memberFunctionExecutionsTimer.totalTime(NANOSECONDS)
        };

        context.getResultSender().lastResult(result);
      }
    }

    @Override
    public String getId() {
      return ID;
    }
  }

  private static class ExecutionsTimerValues {
    final long count;
    final double totalTime;

    ExecutionsTimerValues(long count, double totalTime) {
      this.count = count;
      this.totalTime = totalTime;
    }
  }
}
