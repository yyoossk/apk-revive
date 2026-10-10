package com.yyoossk.apkrevive.core;

import com.reandroid.app.AndroidManifest;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlElement;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** APK を読み取り、最近の Android で動かない原因を洗い出す。APK 内のコードは一切実行しない。 */
public final class ApkAnalyzer {

    static final String[] COMPONENT_TAGS = {
            AndroidManifest.TAG_activity, AndroidManifest.TAG_activity_alias,
            AndroidManifest.TAG_service, AndroidManifest.TAG_receiver
    };

    private ApkAnalyzer() {}

    public static ApkReport analyze(File apk, DeviceProfile device) throws IOException {
        return analyze(apk, device, null);
    }

    /** tmpDir を渡すと、64bit 化の判定用にネイティブライブラリを一時展開して解析する */
    public static ApkReport analyze(File apk, DeviceProfile device, File tmpDir) throws IOException {
        ApkReport r = new ApkReport();
        AndroidManifestBlock manifest;
        try (ZipFile zip = new ZipFile(apk)) {
            ZipEntry me = zip.getEntry("AndroidManifest.xml");
            if (me == null) {
                throw new IOException("AndroidManifest.xml が見つかりません。APK ファイルではない可能性があります。");
            }
            try (InputStream in = zip.getInputStream(me)) {
                manifest = AndroidManifestBlock.load(in);
            }
            Set<String> abis = new TreeSet<>();
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                String n = en.nextElement().getName();
                if (n.startsWith("lib/") && n.endsWith(".so")) {
                    String[] parts = n.split("/");
                    if (parts.length >= 3) abis.add(parts[1]);
                }
            }
            r.abis.addAll(abis);
            r.arm64SourceAbi = Arm64Plan.pickSourceAbi(r.abis);
            if (r.arm64SourceAbi != null && tmpDir != null) {
                scanNativeLibs(zip, r, tmpDir);
            }
            SignatureScan sig = SignatureScan.scan(apk, zip);
            r.hasV1Signature = sig.v1;
            r.hasV2Signature = sig.v2;
            r.hasV3Signature = sig.v3;
        }

        r.packageName = manifest.getPackageName();
        r.versionName = manifest.getVersionName();
        r.versionCode = manifest.getVersionCode();
        r.minSdk = manifest.getMinSdkVersion();
        r.targetSdk = manifest.getTargetSdkVersion();
        r.effectiveTargetSdk = r.targetSdk != null ? r.targetSdk : (r.minSdk != null ? r.minSdk : 1);
        r.extractNativeLibs = manifest.isExtractNativeLibs();
        r.usesPermissionCount = manifest.getUsesPermissions().size();
        ResXmlElement root = manifest.getManifestElement();
        r.isSplitApk = root != null && root.searchAttributeByName("split") != null;
        r.componentsMissingExported = countMissingExported(manifest);

        r.deviceMinTargetSdk = device.minInstallableTargetSdk();
        // 最新まで上げると挙動が変わって壊れやすいので「インストールできる最低ライン」を推奨する
        r.recommendedTargetSdk = Math.max(r.effectiveTargetSdk, 24);

        buildIssues(r, device);
        return r;
    }

    static void scanNativeLibs(ZipFile zip, ApkReport r, File tmpDir) throws IOException {
        tmpDir.mkdirs();
        String prefix = "lib/" + r.arm64SourceAbi + "/";
        Enumeration<? extends ZipEntry> en = zip.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            String n = e.getName();
            if (!n.startsWith(prefix) || !n.endsWith(".so") || n.indexOf('/', prefix.length()) >= 0) continue;
            NativeLib lib = new NativeLib();
            lib.path = n;
            lib.abi = r.arm64SourceAbi;
            lib.fileName = n.substring(prefix.length());
            lib.size = e.getSize();
            File tmp = new File(tmpDir, "scan.so");
            try (InputStream in = zip.getInputStream(e)) {
                java.nio.file.Files.copy(in, tmp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                lib.elf = ElfInfo.parse(tmp);
            } catch (IOException ex) {
                lib.elf = null;
            }
            NativeLibCatalog.classify(lib, tmp);
            tmp.delete();
            r.nativeLibs.add(lib);
        }
    }

    static int countMissingExported(AndroidManifestBlock manifest) {
        ResXmlElement app = manifest.getApplicationElement();
        if (app == null) return 0;
        int count = 0;
        for (String tag : COMPONENT_TAGS) {
            Iterator<ResXmlElement> it = app.getElements(tag);
            while (it.hasNext()) {
                ResXmlElement c = it.next();
                if (c.searchAttributeByResourceId(AndroidManifest.ID_exported) == null
                        && c.getElements(AndroidManifest.TAG_intent_filter).hasNext()) {
                    count++;
                }
            }
        }
        return count;
    }

    static boolean hasAppCode(ApkReport r) {
        for (NativeLib l : r.nativeLibs) if (l.kind == NativeLib.Kind.APP_CODE) return true;
        return false;
    }

    private static void buildIssues(ApkReport r, DeviceProfile device) {
        int t = r.effectiveTargetSdk;

        if (r.isSplitApk) {
            r.issues.add(new ApkReport.Issue(ApkReport.Severity.ERROR,
                    "分割APK（Split APK）の一部です",
                    "このファイルだけではインストールできません。分割APKの結合は今のバージョンでは対応していません。",
                    false));
            r.hasUnfixableError = true;
        }

        if (r.deviceMinTargetSdk > 0 && t < r.deviceMinTargetSdk) {
            r.issues.add(new ApkReport.Issue(ApkReport.Severity.ERROR,
                    "この端末ではインストールできません",
                    "targetSdk が " + t + " ですが、この端末（API " + device.sdkInt + "）は "
                            + r.deviceMinTargetSdk + " 以上が必要です。targetSdk を引き上げると直せます。",
                    true));
        } else if (t < 24) {
            r.issues.add(new ApkReport.Issue(ApkReport.Severity.WARNING,
                    "Android 15 以降ではインストールできません",
                    "targetSdk が " + t + " です。Android 15 以降の端末では 24 以上が必要です。",
                    true));
        }

        if (t < 23 && r.recommendedTargetSdk >= 23 && r.usesPermissionCount > 0) {
            r.issues.add(new ApkReport.Issue(ApkReport.Severity.WARNING,
                    "権限の手動許可が必要になる場合があります",
                    "targetSdk を 23 以上にすると、カメラや保存領域などの権限がインストール時に自動で許可されなくなります。"
                            + "アプリが権限を要求しない作りの場合は、設定アプリ →「アプリ」→ 権限 から手動で許可してください。",
                    false));
        }

        if (r.hasNativeLibs()) {
            boolean has64 = false, runnable = false;
            for (String abi : r.abis) {
                if (abi.contains("64")) has64 = true;
                if (device.supportsAbi(abi)) runnable = true;
            }
            boolean swappable = r.arm64SourceAbi != null && !r.nativeLibs.isEmpty() && !hasAppCode(r);
            String howTo = swappable
                    ? "各ライブラリの 64bit 版（arm64-v8a）を用意できれば、差し替えて 64bit 化できます。"
                    : r.arm64SourceAbi != null
                    ? "アプリ固有のコード（ゲームエンジン本体など）を含むため、差し替えでは 64bit 化できない見込みです。"
                    : "再構築では直せません。";
            if (!runnable) {
                r.issues.add(new ApkReport.Issue(ApkReport.Severity.ERROR,
                        "この端末のCPUに対応していません",
                        "含まれるネイティブライブラリ（" + String.join(", ", r.abis) + "）はこの端末で動きません。"
                                + (device.is64BitOnly() ? "この端末は64bit専用です。" : "") + howTo,
                        swappable));
                if (!swappable) r.hasUnfixableError = true;
            } else if (!has64) {
                r.issues.add(new ApkReport.Issue(ApkReport.Severity.WARNING,
                        "32bit専用のアプリです",
                        "この端末では動きますが、64bit専用の端末（最近の一部機種）では動きません。" + howTo,
                        swappable));
            }
            if (!runnable && !swappable) {
                r.issues.add(new ApkReport.Issue(ApkReport.Severity.INFO,
                        "ほかの方法",
                        "・仮想環境アプリ（32bit 対応の Android を丸ごと動かすもの）で動かす\n"
                                + "・root 化した端末なら 32bit 用の zygote を有効にする改造がある\n"
                                + "・開発元に 64bit 版の提供を依頼する",
                        false));
            }
        }

        if (!r.hasV2Signature && !r.hasV3Signature) {
            r.issues.add(new ApkReport.Issue(ApkReport.Severity.INFO,
                    r.hasV1Signature ? "古い署名方式（v1）だけです" : "署名がありません",
                    "再構築時に v1 + v2 + v3 署名を付け直します。",
                    true));
        }

        r.issues.add(new ApkReport.Issue(ApkReport.Severity.INFO,
                "再構築すると署名が変わります",
                "元のアプリが入っている場合は、先にアンインストールが必要です（アプリ内のデータは消えます）。"
                        + "また、元の配布元からのアップデートは受け取れなくなります。",
                false));
    }
}
