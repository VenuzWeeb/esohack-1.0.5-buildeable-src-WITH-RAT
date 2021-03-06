package com.squareup.okhttp.internal.tls;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;

public final class AndroidTrustRootIndex implements TrustRootIndex {
   private final X509TrustManager trustManager;
   private final Method findByIssuerAndSignatureMethod;

   public AndroidTrustRootIndex(X509TrustManager trustManager, Method findByIssuerAndSignatureMethod) {
      this.findByIssuerAndSignatureMethod = findByIssuerAndSignatureMethod;
      this.trustManager = trustManager;
   }

   public X509Certificate findByIssuerAndSignature(X509Certificate cert) {
      try {
         TrustAnchor trustAnchor = (TrustAnchor)this.findByIssuerAndSignatureMethod.invoke(this.trustManager, cert);
         return trustAnchor != null ? trustAnchor.getTrustedCert() : null;
      } catch (IllegalAccessException var3) {
         throw new AssertionError();
      } catch (InvocationTargetException var4) {
         return null;
      }
   }

   public static TrustRootIndex get(X509TrustManager trustManager) {
      try {
         Method method = trustManager.getClass().getDeclaredMethod("findTrustAnchorByIssuerAndSignature", X509Certificate.class);
         method.setAccessible(true);
         return new AndroidTrustRootIndex(trustManager, method);
      } catch (NoSuchMethodException var2) {
         return null;
      }
   }
}
