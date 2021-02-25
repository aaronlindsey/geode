package org.apache.geode.internal.net;

import java.net.Socket;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import org.apache.logging.log4j.Logger;

import org.apache.geode.logging.internal.log4j.api.LogService;

// This is derived from code at https://github.com/cloudfoundry/java-buildpack-security-provider
// TODO It probably needs copyright notice and Apache license somewhere
final class FileWatchingX509ExtendedTrustManager extends X509ExtendedTrustManager {

  private static final Logger logger = LogService.getLogger();
  private static final ConcurrentHashMap<Path, FileWatchingX509ExtendedTrustManager> instances =
      new ConcurrentHashMap<>();

  private final AtomicReference<X509ExtendedTrustManager> trustManager = new AtomicReference<>();
  private final Path truststorePath;
  private final Supplier<TrustManager[]> trustManagerSupplier;

  private FileWatchingX509ExtendedTrustManager(Path truststorePath,
      Supplier<TrustManager[]> trustManagerSupplier) {
    this.truststorePath = truststorePath;
    this.trustManagerSupplier = trustManagerSupplier;

    new FileWatcher(truststorePath, new FileWatcherCallback()).watch();

    if (trustManager.compareAndSet(null, trustManager())) {
      logger.info(String.format("Initialized TrustManager for %s", this.truststorePath));
    }
  }

  static FileWatchingX509ExtendedTrustManager forPath(Path path,
      Supplier<TrustManager[]> supplier) {
    return instances.computeIfAbsent(path,
        (Path p) -> new FileWatchingX509ExtendedTrustManager(p, supplier));
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

  private X509ExtendedTrustManager trustManager() {
    for (TrustManager trustManager : trustManagerSupplier.get()) {
      if (trustManager instanceof X509ExtendedTrustManager) {
        return (X509ExtendedTrustManager) trustManager;
      }
    }

    throw new IllegalStateException("No X509ExtendedKeyManager available");
  }

  private class FileWatcherCallback implements Runnable {

    @Override
    public void run() {
      if (trustManager.getAndSet(trustManager()) == null) {
        logger.info(String.format("Initialized TrustManager for %s", truststorePath));
      } else {
        logger.info(String.format("Updated TrustManager for %s", truststorePath));
      }
    }

  }
}
