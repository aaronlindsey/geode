/*
 * licensed to the apache software foundation (asf) under one or more contributor license
 * agreements. see the notice file distributed with this work for additional information regarding
 * copyright ownership. the asf licenses this file to you under the apache license, version 2.0 (the
 * "license"); you may not use this file except in compliance with the license. you may obtain a
 * copy of the license at
 *
 * http://www.apache.org/licenses/license-2.0
 *
 * unless required by applicable law or agreed to in writing, software distributed under the license
 * is distributed on an "as is" basis, without warranties or conditions of any kind, either express
 * or implied. see the license for the specific language governing permissions and limitations under
 * the license.
 */
package org.apache.geode.metrics;


import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import io.micrometer.core.instrument.Timer;
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
import org.apache.geode.cache.execute.FunctionService;
import org.apache.geode.internal.AvailablePortHelper;
import org.apache.geode.rules.ServiceJarRule;
import org.apache.geode.test.compiler.ClassBuilder;
import org.apache.geode.test.junit.rules.gfsh.GfshRule;

public class FunctionExecutionsTimerRegistrationTest {

  private Path serverFolder;
  private ClientCache clientCache;
  private Pool serverPool;
  private int jmxRmiPort;

  @Rule
  public GfshRule gfshRule = new GfshRule();

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Rule
  public ServiceJarRule serviceJarRule = new ServiceJarRule();

  @Before
  public void setUp() throws IOException {
    int[] ports = AvailablePortHelper.getRandomAvailableTCPPorts(2);

    int serverPort = ports[0];
    jmxRmiPort = ports[1];

    serverFolder = temporaryFolder.newFolder("server").toPath().toAbsolutePath();

    Path serviceJarPath = serviceJarRule.createJarFor("services.jar",
        MetricsPublishingService.class, SimpleMetricsPublishingService.class);

    Path testHelpersJarPath =
        temporaryFolder.getRoot().toPath().resolve("test-helpers.jar").toAbsolutePath();
    new ClassBuilder().writeJarFromClasses(testHelpersJarPath.toFile(),
        GetFunctionExecutionTimerValues.class, ExecutionsTimerValues.class);

    String startServerCommand = String.join(" ",
        "start server",
        "--name=server",
        "--dir=" + serverFolder,
        "--server-port=" + serverPort,
        "--classpath=" + serviceJarPath,
        "--J=-Dgemfire.enable-cluster-config=true",
        "--J=-Dgemfire.jmx-manager=true",
        "--J=-Dgemfire.jmx-manager-start=true",
        "--J=-Dgemfire.jmx-manager-port=" + jmxRmiPort);

    String connectCommand = "connect --jmx-manager=localhost[" + jmxRmiPort + "]";
    String deployHelpersCommand = "deploy --jar=" + testHelpersJarPath;

    gfshRule.execute(startServerCommand, connectCommand, deployHelpersCommand);

    clientCache = new ClientCacheFactory().addPoolServer("localhost", serverPort).create();

    serverPool = PoolManager.createFactory()
        .addServer("localhost", serverPort)
        .create("server-pool");
  }

  @After
  public void tearDown() {
    serverPool.destroy();
    clientCache.close();

    String stopServerCommand = "stop server --dir=" + serverFolder;
    gfshRule.execute(stopServerCommand);
  }

  @Test
  public void noTimersRegistered_ifOnlyInternalFunctionsRegistered() {
    List<ExecutionsTimerValues> values = getExecutionsTimerValues();

    assertThat(values).isEmpty();
  }

  @Test
  public void timersRegistered_ifFunctionDeployed() throws IOException {
    deployFunction(UserDeployedFunction.class);

    List<ExecutionsTimerValues> values = getExecutionsTimerValues();

    assertThat(values)
        .hasSize(2)
        .allMatch(v -> v.functionId.equals(UserDeployedFunction.ID), "Has correct function ID")
        .matches(v -> v.stream().anyMatch(t -> t.succeeded), "At least one succeeded timer")
        .matches(v -> v.stream().anyMatch(t -> !t.succeeded), "At least one failure timer");
  }

  private <T> void deployFunction(Class<? extends Function<T>> functionClass) throws IOException {
    Path functionJarPath = temporaryFolder.getRoot().toPath()
        .resolve(functionClass.getSimpleName() + ".jar").toAbsolutePath();

    new ClassBuilder().writeJarFromClasses(functionJarPath.toFile(), functionClass);

    String connectCommand = "connect --jmx-manager=localhost[" + jmxRmiPort + "]";
    String deployFunctionCommand = "deploy --jar=" + functionJarPath;

    gfshRule.execute(connectCommand, deployFunctionCommand);
  }

  private List<ExecutionsTimerValues> getExecutionsTimerValues() {
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

  public static class UserDeployedFunction implements Function<Void> {
    static final String ID = "UserDeployedFunction";

    @Override
    public void execute(FunctionContext<Void> context) {
      // Nothing
    }

    @Override
    public String getId() {
      return ID;
    }

    @Override
    public boolean hasResult() {
      return false;
    }

    @Override
    public boolean isHA() {
      return false;
    }
  }

  public static class GetFunctionExecutionTimerValues implements Function<Void> {
    static final String ID = "GetFunctionExecutionTimerValues";

    @Override
    public void execute(FunctionContext<Void> context) {
      Collection<Timer> timers = SimpleMetricsPublishingService.getRegistry()
          .find("geode.function.executions")
          .timers();

      List<ExecutionsTimerValues> result = timers.stream()
          .map(GetFunctionExecutionTimerValues::toExecutionsTimerValues)
          .filter(t -> !t.functionId.equals(ID))
          .collect(toList());

      context.getResultSender().lastResult(result);
    }

    @Override
    public String getId() {
      return ID;
    }

    private static ExecutionsTimerValues toExecutionsTimerValues(Timer t) {
      String functionId = t.getId().getTag("function");
      boolean succeeded = Boolean.parseBoolean(t.getId().getTag("succeeded"));

      return new ExecutionsTimerValues(
          functionId,
          succeeded,
          t.count(),
          t.totalTime(NANOSECONDS));
    }
  }

  public static class ExecutionsTimerValues implements Serializable {
    final String functionId;
    final boolean succeeded;
    final long count;
    final double totalTime;

    ExecutionsTimerValues(String functionId, boolean succeeded, long count, double totalTime) {
      this.functionId = functionId;
      this.succeeded = succeeded;
      this.count = count;
      this.totalTime = totalTime;
    }

    @Override
    public String toString() {
      return "ExecutionsTimerValues{" +
          "functionId='" + functionId + '\'' +
          ", succeeded=" + succeeded +
          ", count=" + count +
          ", totalTime=" + totalTime +
          '}';
    }
  }
}
