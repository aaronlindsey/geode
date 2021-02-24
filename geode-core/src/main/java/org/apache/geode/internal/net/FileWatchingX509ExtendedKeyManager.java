package org.apache.geode.internal.net;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.Socket;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import org.apache.geode.logging.internal.log4j.api.LogService;

// This is derived from code at https://github.com/cloudfoundry/java-buildpack-security-provider
// TODO It probably needs copyright notice and Apache license somewhere
final class FileWatchingX509ExtendedKeyManager extends X509ExtendedKeyManager {

  private static final Logger logger = LogService.getLogger();

  private final AtomicReference<X509ExtendedKeyManager> keyManager = new AtomicReference<>();

  private final SSLConfig sslConfig;

  private final KeyManagerFactory keyManagerFactory;

  FileWatchingX509ExtendedKeyManager(SSLConfig sslConfig, KeyManagerFactory keyManagerFactory) {
    this.sslConfig = sslConfig;
    this.keyManagerFactory = keyManagerFactory;

    new FileWatcher(Paths.get(this.sslConfig.getKeystore()), new FileWatcherCallback()).watch();

    if (keyManager.compareAndSet(null, getKeyManager(getKeyStore()))) {
      logger.info(String.format("Initialized KeyManager for %s", sslConfig.getKeystore()));
    }
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

  private X509ExtendedKeyManager getKeyManager(KeyStore keyStore) {
    try {
      keyManagerFactory.init(keyStore, sslConfig.getKeystorePassword().toCharArray());

      for (KeyManager keyManager : keyManagerFactory.getKeyManagers()) {
        if (keyManager instanceof X509ExtendedKeyManager) {
          return (X509ExtendedKeyManager) keyManager;
        }
      }

      throw new IllegalStateException("No X509ExtendedKeyManager available");
    } catch (UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException e) {
      throw new UndeclaredThrowableException(e);
    }
  }

  private KeyStore getKeyStore() {
    try {
      String keyStoreType = Objects.toString(sslConfig.getKeystoreType(), "JKS");
      KeyStore keyStore = KeyStore.getInstance(keyStoreType);

      if (StringUtils.isBlank(sslConfig.getKeystore())) {
        throw new IllegalStateException("Keystore path is undefined");
      }

      try (FileInputStream keyStoreStream = new FileInputStream(sslConfig.getKeystore())) {
        keyStore.load(keyStoreStream, sslConfig.getKeystorePassword().toCharArray());
      }

      return keyStore;
    } catch (CertificateException | NoSuchAlgorithmException | KeyStoreException | IOException e) {
      throw new UndeclaredThrowableException(e);
    }
  }

  private final class FileWatcherCallback implements Runnable {

    @Override
    public void run() {
      if (keyManager.getAndSet(getKeyManager(getKeyStore())) == null) {
        logger.info(String.format("Initialized KeyManager for %s", sslConfig.getKeystore()));
      } else {
        logger.info(String.format("Updated KeyManager for %s", sslConfig.getKeystore()));
      }
    }

  }

}
