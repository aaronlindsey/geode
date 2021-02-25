package org.apache.geode.internal.net;

import java.net.Socket;
import java.nio.file.Path;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;

import org.apache.logging.log4j.Logger;

import org.apache.geode.logging.internal.log4j.api.LogService;

// This is derived from code at https://github.com/cloudfoundry/java-buildpack-security-provider
// TODO It probably needs copyright notice and Apache license somewhere
final class FileWatchingX509ExtendedKeyManager extends X509ExtendedKeyManager {

  private static final Logger logger = LogService.getLogger();
  private static final ConcurrentHashMap<Path, FileWatchingX509ExtendedKeyManager> instances =
      new ConcurrentHashMap<>();

  private final AtomicReference<X509ExtendedKeyManager> keyManager = new AtomicReference<>();
  private final Path keystorePath;
  private final Supplier<KeyManager[]> keyManagerSupplier;

  private FileWatchingX509ExtendedKeyManager(Path keystorePath,
      Supplier<KeyManager[]> keyManagerSupplier) {
    this.keystorePath = keystorePath;
    this.keyManagerSupplier = keyManagerSupplier;

    new FileWatcher(this.keystorePath, new FileWatcherCallback()).watch();

    if (keyManager.compareAndSet(null, keyManager())) {
      logger.info(String.format("Initialized KeyManager for %s", keystorePath));
    }
  }

  static FileWatchingX509ExtendedKeyManager forPath(Path keystorePath,
      Supplier<KeyManager[]> keyManagerSupplier) {
    return instances.computeIfAbsent(keystorePath,
        (Path path) -> new FileWatchingX509ExtendedKeyManager(path, keyManagerSupplier));
  }

  @Override
  public String chooseClientAlias(String[] strings, Principal[] principals, Socket socket) {
    return keyManager.get().chooseClientAlias(strings, principals, socket);
  }

  @Override
  public String chooseEngineClientAlias(String[] strings, Principal[] principals,
      SSLEngine sslEngine) {
    return keyManager.get().chooseEngineClientAlias(strings, principals, sslEngine);
  }

  @Override
  public String chooseEngineServerAlias(String s, Principal[] principals, SSLEngine sslEngine) {
    return keyManager.get().chooseEngineServerAlias(s, principals, sslEngine);
  }

  @Override
  public String chooseServerAlias(String s, Principal[] principals, Socket socket) {
    return keyManager.get().chooseServerAlias(s, principals, socket);
  }

  @Override
  public X509Certificate[] getCertificateChain(String s) {
    return keyManager.get().getCertificateChain(s);
  }

  @Override
  public String[] getClientAliases(String s, Principal[] principals) {
    return keyManager.get().getClientAliases(s, principals);
  }

  @Override
  public PrivateKey getPrivateKey(String s) {
    return keyManager.get().getPrivateKey(s);
  }

  @Override
  public String[] getServerAliases(String s, Principal[] principals) {
    return keyManager.get().getServerAliases(s, principals);
  }

  private X509ExtendedKeyManager keyManager() {
    for (KeyManager keyManager : keyManagerSupplier.get()) {
      if (keyManager instanceof X509ExtendedKeyManager) {
        return (X509ExtendedKeyManager) keyManager;
      }
    }

    throw new IllegalStateException("No X509ExtendedKeyManager available");
  }

  private final class FileWatcherCallback implements Runnable {

    @Override
    public void run() {
      if (keyManager.getAndSet(keyManager()) == null) {
        logger.info(String.format("Initialized KeyManager for %s", keystorePath));
      } else {
        logger.info(String.format("Updated KeyManager for %s", keystorePath));
      }
    }

  }

}
