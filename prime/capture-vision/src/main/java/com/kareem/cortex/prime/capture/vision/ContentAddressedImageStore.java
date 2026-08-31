package com.kareem.cortex.prime.capture.vision;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Copies a shared image immediately into app-owned durable storage. */
final class ContentAddressedImageStore {
    static final class StoredImage {
        final File file;
        final String sha256;
        final long byteSize;

        StoredImage(File file, String sha256, long byteSize) {
            this.file = file;
            this.sha256 = sha256;
            this.byteSize = byteSize;
        }
    }

    private ContentAddressedImageStore() {}

    static StoredImage importUri(Context context, Uri uri) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        File directory = new File(context.getFilesDir(), "prime-assets/images");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create image asset directory");
        }

        File temp = File.createTempFile("image-", ".part", directory);
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 unavailable", impossible);
        }

        long total = 0L;
        try (InputStream input = resolver.openInputStream(uri);
             FileOutputStream output = new FileOutputStream(temp)) {
            if (input == null) throw new IOException("Unable to open shared image");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read == 0) continue;
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
                total += read;
            }
            output.getFD().sync();
        } catch (Throwable failure) {
            temp.delete();
            if (failure instanceof IOException) throw (IOException) failure;
            throw new IOException("Image import failed", failure);
        }

        if (total <= 0L) {
            temp.delete();
            throw new IOException("Shared image was empty");
        }

        String sha256 = ImageFingerprint.hex(digest.digest());
        File durable = new File(directory, sha256 + ".bin");
        if (durable.exists()) {
            temp.delete();
        } else if (!temp.renameTo(durable)) {
            copyFile(temp, durable);
            temp.delete();
        }
        return new StoredImage(durable, sha256, total);
    }

    private static void copyFile(File source, File target) throws IOException {
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read > 0) output.write(buffer, 0, read);
            }
            output.getFD().sync();
        } catch (IOException failure) {
            target.delete();
            throw failure;
        }
    }
}
