package net.greg.examples.httpclient.utils;

public final class MyTruststore {

  // Trusts Certs - No SSL validation
  TrustManager[] trustAllCerts = new TrustManager[] {

    new X509TrustManager() {

      @Override
      public void checkClientTrusted(X509Certificate[] chain, String authType)
          throws CertificateException { /* Empty method */ }

      @Override
      public void checkServerTrusted(X509Certificate[] chain, String authType)
          throws CertificateException { /* Empty method */ }

      @Override
      public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
      }
    } // new X509TrustManager()
  }; // TrustManager[]
}
