package com.yyoossk.apkrevive.core;

import com.reandroid.app.AndroidManifest;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlElement;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * マニフェストを最小限だけ書き換え、zipalign 相当の整列をしながら APK を作り直す（署名前）。
 * コード（dex）には一切手を加えない。
 */
public final class ApkRebuilder {

    public static final class Options {
        /** 再構築後の targetSdk */
        public int targetSdk;
        /** targetSdk 31 以上のとき、intent-filter を持つコンポーネントに exported=true を補う */
        public boolean addMissingExported = true;
        /** 追加するファイル（zip 内パス → 実ファイル）。64bit 版 .so の差し替えに使う */
        public java.util.Map<String, File> additions = new java.util.LinkedHashMap<>();
    }

    public static final class Result {
        public final List<String> changes = new ArrayList<>();
    }

    public interface Progress {
        void onProgress(int done, int total);
    }

    private static final long DOS_TIME_2008 = 1199145600000L; // 再現性のため固定時刻
    private static final int ALIGN_EXTRA_ID = 0xD935;
    /** .so は 16KB 境界に置く（16KB ページの端末でも非圧縮のまま読み込めるように。zipalign -P 16 相当） */
    static final int SO_ALIGN = 16384;

    private ApkRebuilder() {}

    public static Result rebuild(File input, File unsignedOut, Options opt, Progress progress) throws IOException {
        Result result = new Result();
        try (ZipFile zip = new ZipFile(input)) {
            ZipEntry me = zip.getEntry("AndroidManifest.xml");
            if (me == null) throw new IOException("AndroidManifest.xml が見つかりません");
            AndroidManifestBlock manifest;
            try (InputStream in = zip.getInputStream(me)) {
                manifest = AndroidManifestBlock.load(in);
            }
            byte[] manifestBytes = patchManifest(manifest, opt, result);

            List<ZipEntry> entries = new ArrayList<>();
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory() || isOldSignatureFile(e.getName()) || e.getName().equals("AndroidManifest.xml")
                        || opt.additions.containsKey(e.getName())) {
                    continue;
                }
                entries.add(e);
            }
            int total = entries.size() + 1 + opt.additions.size();

            CountingOutputStream counter = new CountingOutputStream(new FileOutputStream(unsignedOut));
            try (ZipOutputStream zos = new ZipOutputStream(counter)) {
                // マニフェストは先頭に置く
                ZipEntry m = new ZipEntry("AndroidManifest.xml");
                m.setTime(DOS_TIME_2008);
                m.setMethod(ZipEntry.DEFLATED);
                zos.putNextEntry(m);
                zos.write(manifestBytes);
                zos.closeEntry();
                if (progress != null) progress.onProgress(1, total);

                byte[] buf = new byte[64 * 1024];
                int done = 1;
                boolean arscStoredChanged = false;
                for (ZipEntry src : entries) {
                    String name = src.getName();
                    boolean isArsc = name.equals("resources.arsc");
                    boolean store = src.getMethod() == ZipEntry.STORED || isArsc;
                    if (isArsc && src.getMethod() != ZipEntry.STORED) arscStoredChanged = true;

                    ZipEntry out = new ZipEntry(name);
                    out.setTime(DOS_TIME_2008);
                    if (store) {
                        long size;
                        long crc;
                        if (src.getMethod() == ZipEntry.STORED) {
                            size = src.getSize();
                            crc = src.getCrc();
                        } else {
                            // 圧縮されていたものを非圧縮にする場合は CRC を計算し直す
                            CRC32 c = new CRC32();
                            long s = 0;
                            try (InputStream in = zip.getInputStream(src)) {
                                int n;
                                while ((n = in.read(buf)) > 0) { c.update(buf, 0, n); s += n; }
                            }
                            size = s;
                            crc = c.getValue();
                        }
                        out.setMethod(ZipEntry.STORED);
                        out.setSize(size);
                        out.setCompressedSize(size);
                        out.setCrc(crc);
                        int align = name.endsWith(".so") ? SO_ALIGN : 4;
                        out.setExtra(alignmentExtra(counter.count, name, align));
                    } else {
                        out.setMethod(ZipEntry.DEFLATED);
                    }
                    zos.putNextEntry(out);
                    try (InputStream in = zip.getInputStream(src)) {
                        int n;
                        while ((n = in.read(buf)) > 0) zos.write(buf, 0, n);
                    }
                    zos.closeEntry();
                    done++;
                    if (progress != null) progress.onProgress(done, total);
                }
                for (java.util.Map.Entry<String, File> add : opt.additions.entrySet()) {
                    File f = add.getValue();
                    CRC32 c = new CRC32();
                    try (InputStream in = new java.io.FileInputStream(f)) {
                        int n;
                        while ((n = in.read(buf)) > 0) c.update(buf, 0, n);
                    }
                    ZipEntry out = new ZipEntry(add.getKey());
                    out.setTime(DOS_TIME_2008);
                    out.setMethod(ZipEntry.STORED);
                    out.setSize(f.length());
                    out.setCompressedSize(f.length());
                    out.setCrc(c.getValue());
                    out.setExtra(alignmentExtra(counter.count, add.getKey(), SO_ALIGN));
                    zos.putNextEntry(out);
                    try (InputStream in = new java.io.FileInputStream(f)) {
                        int n;
                        while ((n = in.read(buf)) > 0) zos.write(buf, 0, n);
                    }
                    zos.closeEntry();
                    done++;
                    if (progress != null) progress.onProgress(done, total);
                }
                if (!opt.additions.isEmpty()) {
                    result.changes.add("64bit 版ライブラリ " + opt.additions.size() + " 個を arm64-v8a として追加");
                }
                if (arscStoredChanged) {
                    result.changes.add("resources.arsc を非圧縮で格納（新しいAndroidの要件）");
                }
            }
        }
        result.changes.add("古い署名ファイルを削除し、zipalign 相当の整列を実施（.so は 16KB 境界）");
        return result;
    }

    static byte[] patchManifest(AndroidManifestBlock manifest, Options opt, Result result) {
        Integer oldTarget = manifest.getTargetSdkVersion();
        Integer minSdk = manifest.getMinSdkVersion();
        int effective = oldTarget != null ? oldTarget : (minSdk != null ? minSdk : 1);
        if (opt.targetSdk > effective) {
            manifest.setTargetSdkVersion(opt.targetSdk);
            result.changes.add("targetSdk を " + effective + " → " + opt.targetSdk + " に変更");
        }
        int newTarget = Math.max(effective, opt.targetSdk);
        if (newTarget >= 31 && opt.addMissingExported) {
            int added = addMissingExported(manifest);
            if (added > 0) {
                result.changes.add(added + " 個のコンポーネントに android:exported=\"true\" を追加（従来の動作を維持）");
            }
        }
        manifest.refresh();
        return manifest.getBytes();
    }

    static int addMissingExported(AndroidManifestBlock manifest) {
        ResXmlElement app = manifest.getApplicationElement();
        if (app == null) return 0;
        int added = 0;
        for (String tag : ApkAnalyzer.COMPONENT_TAGS) {
            Iterator<ResXmlElement> it = app.getElements(tag);
            List<ResXmlElement> targets = new ArrayList<>();
            while (it.hasNext()) {
                ResXmlElement c = it.next();
                if (c.searchAttributeByResourceId(AndroidManifest.ID_exported) == null
                        && c.getElements(AndroidManifest.TAG_intent_filter).hasNext()) {
                    targets.add(c);
                }
            }
            for (ResXmlElement c : targets) {
                c.getOrCreateAndroidAttribute(AndroidManifest.NAME_exported, AndroidManifest.ID_exported)
                        .setValueAsBoolean(true);
                added++;
            }
        }
        return added;
    }

    static boolean isOldSignatureFile(String name) {
        String u = name.toUpperCase(Locale.ROOT);
        if (!u.startsWith("META-INF/") || u.indexOf('/', 9) >= 0) return false;
        return u.equals("META-INF/MANIFEST.MF") || u.endsWith(".SF") || u.endsWith(".RSA")
                || u.endsWith(".DSA") || u.endsWith(".EC");
    }

    /** ローカルヘッダ直後のデータが align 境界に来るよう、0xD935 形式の extra field を作る（zipalign と同じ方式）。 */
    static byte[] alignmentExtra(long headerOffset, String name, int align) {
        int nameLen = name.getBytes(StandardCharsets.UTF_8).length;
        long dataStart = headerOffset + 30 + nameLen + 6;
        int pad = (int) ((align - (dataStart % align)) % align);
        byte[] extra = new byte[6 + pad];
        extra[0] = (byte) (ALIGN_EXTRA_ID & 0xff);
        extra[1] = (byte) (ALIGN_EXTRA_ID >> 8);
        int dataLen = 2 + pad;
        extra[2] = (byte) (dataLen & 0xff);
        extra[3] = (byte) (dataLen >> 8);
        extra[4] = (byte) (align & 0xff);
        extra[5] = (byte) (align >> 8);
        return extra;
    }

    static final class CountingOutputStream extends FilterOutputStream {
        long count;

        CountingOutputStream(OutputStream out) { super(out); }

        @Override public void write(int b) throws IOException { out.write(b); count++; }

        @Override public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
        }
    }
}
