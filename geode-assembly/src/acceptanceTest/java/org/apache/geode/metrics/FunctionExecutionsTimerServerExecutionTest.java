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


import static java.lang.Boolean.TRUE;
import static java.util.stream.Collectors.toList;
import static org.apache.geode.test.compiler.ClassBuilder.writeJarFromClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.cache.client.Pool;
import org.apache.geode.cache.client.PoolManager;
import org.apache.geode.cache.execute.Execution;
import org.apache.geode.cache.execute.Function;
import org.apache.geode.cache.execute.FunctionContext;
import org.apache.geode.cache.execute.FunctionException;
import org.apache.geode.cache.execute.FunctionService;
import org.apache.geode.internal.AvailablePortHelper;
import org.apache.geode.management.internal.cli.functions.ListFunctionFunction;
import org.apache.geode.rules.ServiceJarRule;
import org.apache.geode.test.junit.rules.gfsh.GfshRule;

public class FunctionExecutionsTimerServerExecutionTest {

  private int serverPort;
  private ClientCache clientCache;
  private Pool serverPool;
  private String connectCommand;
  private String startServerCommand;
  private String stopServerCommand;

  @Rule
  public GfshRule gfshRule = new GfshRule();

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Rule
  public ServiceJarRule serviceJarRule = new ServiceJarRule();

  @Before
  public void setUp() throws IOException {
    int[] ports = AvailablePortHelper.getRandomAvailableTCPPorts(2);

    serverPort = ports[0];
    int jmxRmiPort = ports[1];

    Path serverFolder = temporaryFolder.newFolder("server").toPath().toAbsolutePath();

    Path serviceJarPath = serviceJarRule.createJarFor("services.jar",
        MetricsPublishingService.class, SimpleMetricsPublishingService.class);

    Path functionJarPath =
        temporaryFolder.getRoot().toPath().resolve("functions.jar").toAbsolutePath();
    writeJarFromClasses(functionJarPath.toFile(), FunctionToTime.class,
        GetFunctionExecutionTimerValues.class, ExecutionsTimerValues.class);

    startServerCommand = String.join(" ",
        "start server",
        "--name=server",
        "--dir=" + serverFolder,
        "--server-port=" + serverPort,
        "--classpath=" + serviceJarPath,
        "--J=-Dgemfire.enable-cluster-config=true",
        "--J=-Dgemfire.jmx-manager=true",
        "--J=-Dgemfire.jmx-manager-start=true",
        "--J=-Dgemfire.jmx-manager-port=" + jmxRmiPort);

    stopServerCommand = "stop server --dir=" + serverFolder;

    connectCommand = "connect --jmx-manager=localhost[" + jmxRmiPort + "]";
    String deployFunctionsCommand = "deploy --jar=" + functionJarPath;

    gfshRule.execute(startServerCommand, connectCommand, deployFunctionsCommand);

    createClientAndPool();
  }

  @After
  public void tearDown() {
    closeClientAndPool();

    gfshRule.execute(stopServerCommand);
  }

  @Test
  public void timersNotRegisteredIfOnlyInternalFunctionsExecuted() {
    executeInternalFunction();

    assertThat(getAllExecutionsTimerValues())
        .as("Function executions timers")
        .isEmpty();
  }

  @Test
  public void timersRecordCountAndTotalTimeIfFunctionSucceeds() {
    Duration functionDuration = Duration.ofSeconds(1);
    executeFunctionThatSucceeds(FunctionToTime.ID, functionDuration);

    ExecutionsTimerValues successTimerValues = getSuccessTimerValues(FunctionToTime.ID);
    ExecutionsTimerValues failureTimerValues = getFailureTimerValues(FunctionToTime.ID);

    assertThat(successTimerValues.count)
        .as("Successful function executions count")
        .isEqualTo(1);

    assertThat(successTimerValues.totalTime)
        .as("Successful function executions total time")
        .isGreaterThan(functionDuration.toNanos());

    assertThat(failureTimerValues.count)
        .as("Failed function executions count")
        .isEqualTo(0);

    assertThat(failureTimerValues.totalTime)
        .as("Failed function executions total time")
        .isEqualTo(0);
  }

  @Test
  public void timersUnregisteredIfServerRestarts() {
    executeFunctionThatSucceeds(FunctionToTime.ID, Duration.ofMillis(1));

    restartServer();

    assertThat(getAllExecutionsTimerValues())
        .as("Function executions timers")
        .isEmpty();
  }

  private void createClientAndPool() {
    clientCache = new ClientCacheFactory().addPoolServer("localhost", serverPort).create();
    serverPool = PoolManager.createFactory()
        .addServer("localhost", serverPort)
        .create("server-pool");
  }

  private void closeClientAndPool() {
    serverPool.destroy();
    clientCache.close();
  }

  private void restartServer() {
    closeClientAndPool();
    gfshRule.execute(connectCommand, stopServerCommand, startServerCommand);
    createClientAndPool();
  }

  /**
   * Invokes a GFSH command which internally invokes {@link ListFunctionFunction} which is an
   * internal function.
   */
  private void executeInternalFunction() {
    gfshRule.execute(connectCommand, "list functions");
  }

  @SuppressWarnings("SameParameterValue")
  private void executeFunctionThatSucceeds(String functionId, Duration duration) {
    @SuppressWarnings("unchecked")
    Execution<String[], String, List<String>> execution =
        (Execution<String[], String, List<String>>) FunctionService.onServer(serverPool);

    Throwable thrown = catchThrowable(() -> execution
        .setArguments(new String[] {String.valueOf(duration.toMillis()), TRUE.toString()})
        .execute(functionId)
        .getResult());

    assertThat(thrown)
        .as("Exception from function expected to succeed")
        .isNull();
  }

  private ExecutionsTimerValues getSuccessTimerValues(String functionId) {
    return getExecutionsTimerValues(functionId, true);
  }

  private ExecutionsTimerValues getFailureTimerValues(String functionId) {
    return getExecutionsTimerValues(functionId, false);
  }

  private ExecutionsTimerValues getExecutionsTimerValues(String functionId, boolean succeededTagValue) {
    List<ExecutionsTimerValues> executionsTimerValues = getAllExecutionsTimerValues().stream()
        .filter(v -> v.functionId.equals(functionId))
        .filter(v -> v.succeeded == succeededTagValue)
        .collect(toList());

    assertThat(executionsTimerValues)
        .hasSize(1);

    return executionsTimerValues.get(0);
  }

  private List<ExecutionsTimerValues> getAllExecutionsTimerValues() {
    @SuppressWarnings("unchecked")
    Execution<Void, List<ExecutionsTimerValues>, List<List<ExecutionsTimerValues>>> functionExecution =
        (Execution<Void, List<ExecutionsTimerValues>, List<List<ExecutionsTimerValues>>>) FunctionService
            .onServer(serverPool);

    List<List<ExecutionsTimerValues>> results = functionExecution
        .execute(GetFunctionExecutionTimerValues.ID)
        .getResult();

    assertThat(results)
        .hasSize(1);

    return results.get(0);
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
}
