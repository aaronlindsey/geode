package org.apache.geode.internal.net;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.Socket;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import org.apache.geode.logging.internal.log4j.api.LogService;

// This is derived from code at https://github.com/cloudfoundry/java-buildpack-security-provider
// TODO It probably needs copyright notice and Apache license somewhere
final class FileWatchingX509ExtendedTrustManager extends X509ExtendedTrustManager {

  private static final Logger logger = LogService.getLogger();

  private final AtomicReference<X509ExtendedTrustManager> trustManager = new AtomicReference<>();

  private final SSLConfig sslConfig;

  private final TrustManagerFactory trustManagerFactory;

  FileWatchingX509ExtendedTrustManager(SSLConfig sslConfig,
      TrustManagerFactory trustManagerFactory) {
    this.sslConfig = sslConfig;
    this.trustManagerFactory = trustManagerFactory;

    new FileWatcher(Paths.get(this.sslConfig.getTruststore()), new FileWatcherCallback()).watch();

    if (trustManager.compareAndSet(null, getTrustManager(getKeyStore()))) {
      logger.info(String.format("Initialized TrustManager for %s", this.sslConfig.getTruststore()));
    }
  }

  @Override
  public void checkClientTrusted(X509Certificate[] x509Certificates, String s, Socket socket)
      throws CertificateException {
    trustManager.get().checkClientTrusted(x509Certificates, s, socket);
  }

  @Override
  public void checkClientTrusted(X509Certificate[] x509Certificates, String s, SSLEngine sslEngine)
      throws CertificateException {
    trustManager.get().checkClientTrusted(x509Certificates, s, sslEngine);
  }

  @Override
  public void checkClientTrusted(X509Certificate[] x509Certificates, String s)
      throws CertificateException {
    trustManager.get().checkClientTrusted(x509Certificates, s);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] x509Certificates, String s, SSLEngine sslEngine)
      throws CertificateException {
    trustManager.get().checkServerTrusted(x509Certificates, s, sslEngine);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] x509Certificates, String s, Socket socket)
      throws CertificateException {
    trustManager.get().checkServerTrusted(x509Certificates, s, socket);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] x509Certificates, String s)
      throws CertificateException {
    trustManager.get().checkServerTrusted(x509Certificates, s);
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return trustManager.get().getAcceptedIssuers();
  }

  private KeyStore getKeyStore() {
    try {
      String keyStoreType = Objects.toString(sslConfig.getKeystoreType(), "JKS");
      KeyStore keyStore = KeyStore.getInstance(keyStoreType);

      if (StringUtils.isBlank(sslConfig.getTruststore())) {
        throw new IllegalStateException("Truststore path is undefined");
      }

      try (FileInputStream keyStoreStream = new FileInputStream(sslConfig.getTruststore())) {
        keyStore.load(keyStoreStream, sslConfig.getTruststorePassword().toCharArray());
      }

      return keyStore;
    } catch (CertificateException | NoSuchAlgorithmException | KeyStoreException | IOException e) {
      throw new UndeclaredThrowableException(e);
    }
  }

  private X509ExtendedTrustManager getTrustManager(KeyStore keyStore) {
    try {
      trustManagerFactory.init(keyStore);

      for (TrustManager trustManager : trustManagerFactory.getTrustManagers()) {
        if (trustManager instanceof X509ExtendedTrustManager) {
          return (X509ExtendedTrustManager) trustManager;
        }
      }

      throw new IllegalStateException("No X509ExtendedKeyManager available");
    } catch (KeyStoreException e) {
      throw new UndeclaredThrowableException(e);
    }
  }

  private class FileWatcherCallback implements Runnable {

    @Override
    public void run() {
      if (trustManager.getAndSet(getTrustManager(getKeyStore())) == null) {
        logger.info(String.format("Initialized TrustManager for %s", sslConfig.getTruststore()));
      } else {
        logger.info(String.format("Updated TrustManager for %s", sslConfig.getTruststore()));
      }
    }

  }
}
