package com.kareem.cortex.recovery;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Device-side recovery bridge for the fresh Cortex package.
 *
 * This class is intentionally NOT part of the application APK. CI compiles it to a standalone
 * dex which is executed only under `run-as com.kareem.cortex.rebuild`, preserving the installed
 * app UID while allowing Android Keystore secrets to be exported before a signer cutover and
 * re-imported after a controlled reinstall.
 */
public final class CortexCredentialBridge {
    private static final String PREF_FILE = "shared_prefs/cortex_rebuild_secure.xml";
    private static final String[] NAMES = {"gemini_api_key", "groq_api_key"};
    private static final Pattern STRING = Pattern.compile("<string\\s+name=\"([^\"]+)\">(.*?)</string>", Pattern.DOTALL);

    private CortexCredentialBridge() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) fail("usage: export|import <path>");
        String mode = args[0];
        File path = new File(args[1]);
        if ("export".equals(mode)) exportCredentials(path);
        else if ("import".equals(mode)) importCredentials(path);
        else fail("unknown mode");
    }

    private static void exportCredentials(File out) throws Exception {
        Map<String,String> prefs = readPrefStrings(new File(PREF_FILE));
        StringBuilder body = new StringBuilder();
        body.append("format=CORTEX_REBUILD_CREDENTIALS_V1\n");
        int exported = 0;
        for (String name : NAMES) {
            String encrypted = prefs.get(name);
            String iv = prefs.get(name + "_iv");
            if (encrypted == null || encrypted.isEmpty() || iv == null || iv.isEmpty()) {
                body.append(name).append("_present=0\n");
                continue;
            }
            SecretKey key = existingKey(name);
            if (key == null) throw new IllegalStateException("Android Keystore alias missing for " + name);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            byte[] clear = cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP));
            body.append(name).append("_present=1\n");
            body.append(name).append("_b64=")
                    .append(Base64.encodeToString(clear, Base64.NO_WRAP)).append('\n');
            exported++;
        }
        body.append("exported_count=").append(exported).append('\n');
        writeBytes(out, body.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("CORTEX_CREDENTIAL_EXPORT_OK count=" + exported);
    }

    private static void importCredentials(File in) throws Exception {
        Map<String,String> migration = parseProperties(readText(in));
        if (!"CORTEX_REBUILD_CREDENTIALS_V1".equals(migration.get("format"))) {
            throw new IllegalArgumentException("unsupported credential migration format");
        }
        File prefFile = new File(PREF_FILE);
        Map<String,String> prefs = readPrefStrings(prefFile);
        int imported = 0;
        for (String name : NAMES) {
            if (!"1".equals(migration.get(name + "_present"))) continue;
            String b64 = migration.get(name + "_b64");
            if (b64 == null || b64.isEmpty()) throw new IllegalStateException("missing payload for " + name);
            byte[] clear = Base64.decode(b64, Base64.NO_WRAP);
            SecretKey key = getOrCreateKey(name);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(clear);
            prefs.put(name, Base64.encodeToString(encrypted, Base64.NO_WRAP));
            prefs.put(name + "_iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP));
            imported++;
        }
        writePrefStrings(prefFile, prefs);
        System.out.println("CORTEX_CREDENTIAL_IMPORT_OK count=" + imported);
    }

    private static SecretKey existingKey(String name) throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        java.security.Key key = ks.getKey(alias(name), null);
        return key instanceof SecretKey ? (SecretKey) key : null;
    }

    private static SecretKey getOrCreateKey(String name) throws Exception {
        SecretKey existing = existingKey(name);
        if (existing != null) return existing;
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(alias(name),
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return kg.generateKey();
    }

    private static String alias(String name) { return "cortex_rebuild_" + name; }

    private static Map<String,String> readPrefStrings(File file) throws Exception {
        LinkedHashMap<String,String> out = new LinkedHashMap<>();
        if (!file.isFile()) return out;
        Matcher m = STRING.matcher(readText(file));
        while (m.find()) out.put(m.group(1), m.group(2));
        return out;
    }

    private static void writePrefStrings(File file, Map<String,String> values) throws Exception {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n");
        for (Map.Entry<String,String> e : values.entrySet()) {
            xml.append("    <string name=\"").append(xmlEscape(e.getKey())).append("\">")
                    .append(xmlEscape(e.getValue())).append("</string>\n");
        }
        xml.append("</map>\n");
        writeBytes(file, xml.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String xmlEscape(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    private static Map<String,String> parseProperties(String text) {
        LinkedHashMap<String,String> out = new LinkedHashMap<>();
        for (String line : text.split("\\r?\\n")) {
            int eq = line.indexOf('=');
            if (eq > 0) out.put(line.substring(0, eq), line.substring(eq + 1));
        }
        return out;
    }

    private static String readText(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) != -1;) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    private static void writeBytes(File file, byte[] bytes) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(bytes);
            out.getFD().sync();
        }
        file.setReadable(false, false); file.setWritable(false, false); file.setExecutable(false, false);
        file.setReadable(true, true); file.setWritable(true, true);
    }

    private static void fail(String message) { throw new IllegalArgumentException(message); }
}
