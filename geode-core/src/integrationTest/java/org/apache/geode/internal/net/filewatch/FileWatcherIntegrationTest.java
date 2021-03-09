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
package org.apache.geode.internal.net.filewatch;

import static org.apache.geode.test.awaitility.GeodeAwaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.apache.geode.test.junit.rules.ExecutorServiceRule;

public class FileWatcherIntegrationTest {
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Rule
  public ExecutorServiceRule executorService = new ExecutorServiceRule();

  private Path watchedFile;
  private Runnable callback;

  @Before
  public void setUp() throws Exception {
    watchedFile = temporaryFolder.newFile("watched").toPath();
    callback = mock(Runnable.class);

    FileWatcher fileWatcher = new FileWatcher(watchedFile, callback);
    executorService.getExecutorService().submit(fileWatcher);

    // give the file watcher time to start watching for changes
    Thread.sleep(Duration.ofSeconds(5).toMillis());
  }

  @Test
  public void detectsChangeToWatchedFile() throws Exception {
    Files.write(watchedFile, "foo".getBytes(StandardCharsets.UTF_8));

    await().untilAsserted(() -> verify(callback).run());
  }

  @Test
  public void doesNotDetectChangeToOtherFileInSameDirectory() throws Exception {
    Path unwatchedFile = temporaryFolder.newFile("unwatched").toPath();
    Files.write(unwatchedFile, "foo".getBytes(StandardCharsets.UTF_8));

    // give the file watcher sufficient time to detect the change
    Thread.sleep(Duration.ofSeconds(15).toMillis());

    verifyNoInteractions(callback);
  }
}
