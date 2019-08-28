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


import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Timer;
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
import org.apache.geode.cache.execute.FunctionService;
import org.apache.geode.internal.AvailablePortHelper;
import org.apache.geode.rules.ServiceJarRule;
import org.apache.geode.test.compiler.ClassBuilder;
import org.apache.geode.test.junit.rules.gfsh.GfshRule;

@RunWith(JUnitParamsRunner.class)
public class FunctionExecutionsTimerTest {

  private Path serviceJarPath;

  @Rule
  public GfshRule gfshRule = new GfshRule();

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Rule
  public ServiceJarRule serviceJarRule = new ServiceJarRule();
  private String locatorString;
  private String connectToLocatorCommand;
  private ClientCache clientCache;
  private Pool server1Pool;
  private int serverPort1;

  @Before
  public void before() throws IOException {
    serviceJarPath = serviceJarRule.createJarFor("metrics-publishing-service.jar",
        MetricsPublishingService.class, SimpleMetricsPublishingService.class);

    int[] availablePorts = AvailablePortHelper.getRandomAvailableTCPPorts(3);

    int locatorPort = availablePorts[0];
    serverPort1 = availablePorts[1];
    int serverPort2 = availablePorts[2];

    locatorString = "localhost[" + locatorPort + "]";

    File folderForLocator = temporaryFolder.newFolder("locator");
    File folderForServer1 = temporaryFolder.newFolder("server1");
    File folderForServer2 = temporaryFolder.newFolder("server2");

    String startLocatorCommand = String.join(" ",
        "start locator",
        "--name=" + "locator",
        "--dir=" + folderForLocator.getAbsolutePath(),
        "--port=" + locatorPort);

    String startServer1Command = startServerCommand("server1", serverPort1, folderForServer1);

    gfshRule.execute(startLocatorCommand, startServer1Command);

    Path temporaryFolderPath = temporaryFolder.getRoot().toPath();
    Path functionToTimeJarPath =
        temporaryFolderPath.resolve("function-to-time.jar").toAbsolutePath();
    new ClassBuilder().writeJarFromClass(FunctionToTime.class, functionToTimeJarPath.toFile());
    Path getExecutionsTimerFunctionJarPath =
        temporaryFolderPath.resolve("get-executions-timer-function.jar").toAbsolutePath();
    new ClassBuilder().writeJarFromClass(GetExecutionsTimerFunction.class,
        getExecutionsTimerFunctionJarPath.toFile());

    connectToLocatorCommand = "connect --locator=" + locatorString;
    String deployFunctionToTimeCommand = "deploy --jar=" + functionToTimeJarPath.toAbsolutePath();
    String deployGetExecutionsTimerFunctionCommand =
        "deploy --jar=" + getExecutionsTimerFunctionJarPath.toAbsolutePath();

    gfshRule.execute(connectToLocatorCommand, deployFunctionToTimeCommand,
        deployGetExecutionsTimerFunctionCommand);

    clientCache = new ClientCacheFactory().addPoolLocator("localhost", locatorPort).create();

    server1Pool = PoolManager.createFactory()
        .addServer("localhost", serverPort1)
        .create("server1pool");
  }

  @After
  public void stopMembers() {
    clientCache.close();
    server1Pool.destroy();

    String shutdownCommand = String.join(" ",
        "shutdown",
        "--include-locators=true");
    gfshRule.execute(connectToLocatorCommand, shutdownCommand);
  }

  @Test
  @Parameters({"true", "false"})
  @TestCaseName("{method}(succeededTagValue={0})")
  public void functionExists_notExecuted_expectZeroExecutions(boolean succeededTagValue) {
    @SuppressWarnings("unchecked")
    Execution<String[], Number[], List<Number[]>> execution =
        (Execution<String[], Number[], List<Number[]>>) FunctionService.onServer(server1Pool);

    List<Number[]> result = execution
        .setArguments(new String[] {FunctionToTime.ID, String.valueOf(succeededTagValue)})
        .execute(GetExecutionsTimerFunction.ID)
        .getResult();

    assertThat(result)
        .hasSize(1);
    assertThat(result.get(0))
        .as("Function execution count and total time")
        .containsExactly(0L, 0.0);
  }

  private String startServerCommand(String serverName, int serverPort, File folderForServer) {
    return String.join(" ",
        "start server",
        "--name=" + serverName,
        "--groups=" + serverName,
        "--dir=" + folderForServer.getAbsolutePath(),
        "--server-port=" + serverPort,
        "--locators=" + locatorString,
        "--classpath=" + serviceJarPath);
  }

  public static class FunctionToTime implements Function<Void> {
    private static final String ID = "FunctionToTime";

    @Override
    public void execute(FunctionContext<Void> context) {
      try {
        Thread.sleep(5000);
      } catch (InterruptedException ignored) {
      }
      context.getResultSender().lastResult("OK");
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
      String id = context.getArguments()[0];
      String succeeded = context.getArguments()[1];

      Timer memberFunctionExecutionsTimer = SimpleMetricsPublishingService.getRegistry()
          .find("geode.function.executions")
          .tag("function", id)
          .tag("succeeded", succeeded)
          .timer();

      if (memberFunctionExecutionsTimer == null) {
        context.getResultSender().lastResult(null);
      } else {
        Number[] result = new Number[] {
            memberFunctionExecutionsTimer.count(),
            memberFunctionExecutionsTimer.totalTime(TimeUnit.NANOSECONDS)
        };

        context.getResultSender().lastResult(result);
      }
    }

    @Override
    public String getId() {
      return ID;
    }
  }
}
