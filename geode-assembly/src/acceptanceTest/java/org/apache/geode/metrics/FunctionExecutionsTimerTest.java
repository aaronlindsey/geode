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
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

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
import org.apache.geode.rules.ServiceJarRule;
import org.apache.geode.test.compiler.ClassBuilder;
import org.apache.geode.test.junit.rules.gfsh.GfshRule;

@RunWith(JUnitParamsRunner.class)
public class FunctionExecutionsTimerTest {

  private static final String FUNCTION_TO_TIME_JAR = "function-to-time.jar";

  private int locatorPort;
  private int serverPort1;
  private int serverPort2;
  private File server1folder;
  private File server2folder;
  private Path serviceJarPath;
  private ClientCache clientCache;
  private Pool server1Pool;

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
    serverPort1 = availablePorts[1];
    serverPort2 = availablePorts[2];

    server1folder = temporaryFolder.newFolder("server1");
    server2folder = temporaryFolder.newFolder("server2");

    serviceJarPath = serviceJarRule.createJarFor("metrics-publishing-service.jar",
        MetricsPublishingService.class, SimpleMetricsPublishingService.class);

    String startLocatorCommand = String.join(" ",
        "start locator",
        "--name=" + "locator",
        "--dir=" + temporaryFolder.newFolder("locator").getAbsolutePath(),
        "--port=" + locatorPort);

    String startServer1Command = startServerCommand("server1", serverPort1, server1folder);

    gfshRule.execute(startLocatorCommand, startServer1Command);

    Path temporaryFolderPath = temporaryFolder.getRoot().toPath();
    Path functionToTimeJarPath =
        temporaryFolderPath.resolve(FUNCTION_TO_TIME_JAR).toAbsolutePath();
    new ClassBuilder().writeJarFromClass(FunctionToTime.class, functionToTimeJarPath.toFile());
    Path getExecutionsTimerFunctionJarPath =
        temporaryFolderPath.resolve("get-executions-timer-function.jar").toAbsolutePath();
    new ClassBuilder().writeJarFromClass(GetExecutionsTimerFunction.class,
        getExecutionsTimerFunctionJarPath.toFile());

    String deployFunctionToTimeCommand = "deploy --jar=" + functionToTimeJarPath.toAbsolutePath();
    String deployGetExecutionsTimerFunctionCommand =
        "deploy --jar=" + getExecutionsTimerFunctionJarPath.toAbsolutePath();

    gfshRule.execute(connectToLocatorCommand(), deployFunctionToTimeCommand,
        deployGetExecutionsTimerFunctionCommand);

    clientCache = new ClientCacheFactory().create();

    server1Pool = PoolManager.createFactory()
        .addServer("localhost", serverPort1)
        .create("server1pool");
  }

  @After
  public void tearDown() {
    clientCache.close();
    server1Pool.destroy();

    String shutdownCommand = "shutdown --include-locators=true";
    gfshRule.execute(connectToLocatorCommand(), shutdownCommand);
  }

  @Test
  @Parameters({"true", "false"})
  @TestCaseName("{method}(succeededTagValue={0})")
  public void functionExists_notExecuted_expectZeroExecutions(boolean succeededTagValue) {
    ExecutionsTimerValues result =
        getExecutionsTimerValues(FunctionToTime.ID, String.valueOf(succeededTagValue));

    assertThat(result.count)
        .as("Function execution count")
        .isEqualTo(0);

    assertThat(result.totalTime)
        .as("Function execution total time")
        .isEqualTo(0);
  }

  @Test
  public void functionExists_undeployJar_expectMetersRemoved() {
    String undeployFunctionToTimeCommand = "undeploy --jar=" + FUNCTION_TO_TIME_JAR;
    String stopServer1Command = "stop server --dir=" + server1folder.getAbsolutePath();
    String startServer1Command = startServerCommand("server1", serverPort1, server1folder);

    gfshRule.execute(connectToLocatorCommand(), undeployFunctionToTimeCommand, stopServer1Command,
        startServer1Command);

    ExecutionsTimerValues result = getExecutionsTimerValues(FunctionToTime.ID);

    assertThat(result)
        .as("Function execution timers")
        .isNull();
  }

  @Test
  public void meterRecordsCountAndTotalTimeIfFunctionSucceeds() {
    Duration functionDuration = Duration.ofSeconds(1);
    executeFunctionThatSucceeds(FunctionToTime.ID, functionDuration);

    String succeededTagValue = TRUE.toString();
    ExecutionsTimerValues result = getExecutionsTimerValues(FunctionToTime.ID, succeededTagValue);

    assertThat(result.count)
        .as("Function execution count")
        .isEqualTo(1);

    assertThat(result.totalTime)
        .as("Function execution total time")
        .isGreaterThan(functionDuration.toNanos());
  }

  @Test
  public void meterRecordsCountAndTotalTimeIfFunctionThrows() {
    Duration functionDuration = Duration.ofSeconds(1);
    executeFunctionThatThrows(FunctionToTime.ID, functionDuration);

    String succeededTagValue = FALSE.toString();
    ExecutionsTimerValues result = getExecutionsTimerValues(FunctionToTime.ID, succeededTagValue);

    assertThat(result.count)
        .as("Function execution count")
        .isEqualTo(1);

    assertThat(result.totalTime)
        .as("Function execution total time")
        .isGreaterThan(functionDuration.toNanos());
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

  private ExecutionsTimerValues getExecutionsTimerValues(String... args) {
    @SuppressWarnings("unchecked")
    Execution<String[], Number[], List<Number[]>> execution =
        (Execution<String[], Number[], List<Number[]>>) FunctionService.onServer(server1Pool);

    List<Number[]> result = execution
        .setArguments(args)
        .execute(GetExecutionsTimerFunction.ID)
        .getResult();

    assertThat(result)
        .hasSize(1);

    if (result.get(0) == null) {
      return null;
    }

    long count = (long) result.get(0)[0];
    double totalTime = (double) result.get(0)[1];
    return new ExecutionsTimerValues(count, totalTime);
  }

  private String connectToLocatorCommand() {
    return "connect --locator=localhost[" + locatorPort + "]";
  }

  private String startServerCommand(String serverName, int serverPort, File serverFolder) {
    return String.join(" ",
        "start server",
        "--name=" + serverName,
        "--groups=" + serverName,
        "--dir=" + serverFolder.getAbsolutePath(),
        "--server-port=" + serverPort,
        "--locators=localhost[" + locatorPort + "]",
        "--classpath=" + serviceJarPath);
  }

  public static class FunctionToTime implements Function<String[]> {
    private static final String ID = "FunctionToTime";
    static final String OK_RESULT = "OK";
    static final String FAIL_RESULT = "FAIL";

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
        context.getResultSender().lastResult(OK_RESULT);
      } else {
        throw new FunctionException(FAIL_RESULT);
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
