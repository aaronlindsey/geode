package org.apache.geode.internal.net;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.Logger;

import org.apache.geode.logging.internal.log4j.api.LogService;

// This is copied from https://github.com/cloudfoundry/java-buildpack-security-provider
// TODO It probably needs copyright notice and Apache license somewhere
final class FileWatcher implements Runnable, Thread.UncaughtExceptionHandler, ThreadFactory {

  private static final Logger logger = LogService.getLogger();

  private final Runnable callback;

  private final AtomicInteger counter = new AtomicInteger();

  private final ExecutorService executorService;

  private final Path source;

  FileWatcher(Path source, Runnable callback) {
    this.callback = callback;
    executorService = Executors.newSingleThreadExecutor(this);
    this.source = source.normalize().toAbsolutePath();
  }

  @Override
  public Thread newThread(Runnable r) {
    Thread thread = new Thread(r);
    thread.setDaemon(true);
    thread.setName(String.format("file-watcher-%s-%d", source.getName(source.getNameCount() - 1),
        counter.getAndIncrement()));
    thread.setUncaughtExceptionHandler(this);

    return thread;
  }

  @Override
  public void run() {
    logger.info(String.format("Start watching %s", source));

    WatchService watchService;
    WatchKey expected;

    try {
      watchService = source.getFileSystem().newWatchService();
      expected =
          source.getParent().register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
    } catch (IOException e) {
      logger.error("Unable to setup file watcher", e);
      return;
    }

    for (;;) {
      try {
        logger.debug("Waiting for event");
        WatchKey actual = watchService.take();

        if (!actual.equals(expected)) {
          logger.warn(String.format("Unknown watch key: %s", actual));
          continue;
        }

        for (WatchEvent<?> watchEvent : actual.pollEvents()) {
          Path changed = (Path) watchEvent.context();

          if (!source.getFileName().equals(changed)) {
            logger.debug(String.format("Discarding unimportant file change: %s", changed));
            continue;
          }
          callback.run();
        }

        if (!actual.reset()) {
          logger.warn(String.format("Watch key is no longer valid: %s", actual));
          break;
        }
      } catch (InterruptedException e) {
        logger.warn("Thread interrupted");
        Thread.currentThread().interrupt();
        break;
      }
    }

    logger.info(String.format("Stop watching %s", source));
  }

  @Override
  public void uncaughtException(Thread t, Throwable e) {
    logger.warn("Suppressing watch error", e);
    watch();
  }

  void watch() {
    executorService.execute(this);
  }

}
