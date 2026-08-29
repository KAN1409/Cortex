package com.kareem.cortex;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.security.MessageDigest;
import java.util.Locale;

/** Package + UID + permanent signing-certificate registry for trusted local connector apps. */
public final class CortexConnectorRegistryV1 {
    private static final String RELAY_PACKAGE="com.kareem.secondbrain";
    private static final String RELAY_CERT_SHA256="fd402eefcec5b1576d6e7b1e5663a835d4c439d03baaa04506dd662e4b4c7d74";
    private CortexConnectorRegistryV1() {}

    public static Identity resolve(Context context, int sendingUid) {
        if (context == null || sendingUid <= 0) return null;
        try {
            PackageManager pm = context.getPackageManager();
            String[] packages = pm.getPackagesForUid(sendingUid);
            if (packages == null) return null;
            for (String pkg : packages) {
                if (RELAY_PACKAGE.equals(pkg) && signerMatches(pm,pkg,RELAY_CERT_SHA256)) {
                    return new Identity("second_brain", pkg, 100);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    static boolean signerMatches(PackageManager pm,String packageName,String expectedSha256){
        if(pm==null||packageName==null||expectedSha256==null)return false;
        try{
            Signature[] signatures;
            if(Build.VERSION.SDK_INT>=28){
                PackageInfo info=pm.getPackageInfo(packageName,PackageManager.GET_SIGNING_CERTIFICATES);
                if(info.signingInfo==null)return false;
                signatures=info.signingInfo.hasMultipleSigners()?info.signingInfo.getApkContentsSigners():info.signingInfo.getSigningCertificateHistory();
            }else{
                @SuppressWarnings("deprecation") PackageInfo info=pm.getPackageInfo(packageName,PackageManager.GET_SIGNATURES);
                @SuppressWarnings("deprecation") Signature[] legacy=info.signatures;signatures=legacy;
            }
            if(signatures==null)return false;
            for(Signature signature:signatures)if(signature!=null&&expectedSha256.equalsIgnoreCase(sha256(signature.toByteArray())))return true;
        }catch(Throwable ignored){}
        return false;
    }

    private static String sha256(byte[] bytes)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] digest=md.digest(bytes);StringBuilder b=new StringBuilder(digest.length*2);for(byte x:digest)b.append(String.format(Locale.US,"%02x",x));return b.toString();}

    public static final class Identity {
        public final String connectorId, packageName;
        public final int sourcePriority;
        Identity(String connectorId, String packageName, int sourcePriority) {
            this.connectorId = connectorId;
            this.packageName = packageName;
            this.sourcePriority = sourcePriority;
        }
    }
}
