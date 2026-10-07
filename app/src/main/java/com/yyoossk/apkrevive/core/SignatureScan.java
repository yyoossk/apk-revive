package com.yyoossk.apkrevive.core;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * APK にどの署名方式が付いているかを調べる（検証はしない、存在確認だけ）。
 * v2/v3 は Central Directory 直前の "APK Sig Block 42" を読んで判定する。
 */
final class SignatureScan {
    private static final int V2_ID = 0x7109871a;
    private static final int V3_ID = 0xf05368c0;
    private static final int V31_ID = 0x1b93ad61;

    boolean v1, v2, v3;

    static SignatureScan scan(java.io.File apk, ZipFile zip) {
        SignatureScan s = new SignatureScan();
        Enumeration<? extends ZipEntry> en = zip.entries();
        while (en.hasMoreElements()) {
            String n = en.nextElement().getName().toUpperCase(java.util.Locale.ROOT);
            if (n.startsWith("META-INF/") && (n.endsWith(".RSA") || n.endsWith(".DSA") || n.endsWith(".EC"))) {
                s.v1 = true;
                break;
            }
        }
        try {
            s.scanSigningBlock(apk);
        } catch (IOException | RuntimeException ignored) {
            // 署名ブロックが壊れていても診断全体は止めない
        }
        return s;
    }

    private void scanSigningBlock(java.io.File apk) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(apk, "r")) {
            long len = raf.length();
            int maxSearch = (int) Math.min(len, 0xFFFF + 22);
            byte[] tail = new byte[maxSearch];
            raf.seek(len - maxSearch);
            raf.readFully(tail);
            int eocd = -1;
            for (int i = maxSearch - 22; i >= 0; i--) {
                if (tail[i] == 0x50 && tail[i + 1] == 0x4b && tail[i + 2] == 0x05 && tail[i + 3] == 0x06) {
                    eocd = i;
                    break;
                }
            }
            if (eocd < 0) return;
            ByteBuffer e = ByteBuffer.wrap(tail, eocd, 22).order(ByteOrder.LITTLE_ENDIAN);
            long cdOffset = e.getInt(eocd + 16) & 0xffffffffL;
            if (cdOffset < 32) return;
            byte[] footer = new byte[24];
            raf.seek(cdOffset - 24);
            raf.readFully(footer);
            ByteBuffer f = ByteBuffer.wrap(footer).order(ByteOrder.LITTLE_ENDIAN);
            long blockSize = f.getLong(0);
            if (f.getLong(8) != 0x20676953204b5041L || f.getLong(16) != 0x3234206b636f6c42L) return;
            long blockStart = cdOffset - blockSize - 8;
            if (blockStart < 0 || blockSize > Integer.MAX_VALUE) return;
            int pairsLen = (int) (blockSize - 24);
            byte[] pairs = new byte[pairsLen];
            raf.seek(blockStart + 8);
            raf.readFully(pairs);
            ByteBuffer p = ByteBuffer.wrap(pairs).order(ByteOrder.LITTLE_ENDIAN);
            while (p.remaining() >= 12) {
                long pairLen = p.getLong();
                if (pairLen < 4 || pairLen > p.remaining()) break;
                int id = p.getInt();
                if (id == V2_ID) v2 = true;
                if (id == V3_ID || id == V31_ID) v3 = true;
                p.position(p.position() + (int) pairLen - 4);
            }
        }
    }
}
