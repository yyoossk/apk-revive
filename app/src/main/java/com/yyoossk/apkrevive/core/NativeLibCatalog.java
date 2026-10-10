package com.yyoossk.apkrevive.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** よく使われるネイティブライブラリの判定表。ファイル名と JNI 関数名、文字列から種類とバージョンを推定する。 */
public final class NativeLibCatalog {

    static final class Entry {
        final String label;
        final NativeLib.Kind kind;
        final Pattern fileName;
        final String javaPrefix; // null なら名前だけで判定
        final Pattern version;   // null ならバージョンを探さない
        final String hint;

        Entry(String label, NativeLib.Kind kind, String fileName, String javaPrefix, String version, String hint) {
            this.label = label;
            this.kind = kind;
            this.fileName = Pattern.compile(fileName);
            this.javaPrefix = javaPrefix;
            this.version = version == null ? null : Pattern.compile(version);
            this.hint = hint;
        }
    }

    private static final String SAME_VERSION = "同じバージョンの arm64-v8a 版を公式配布物（AAR や Maven の jar 内の jni/ フォルダ）から取り出して選んでください。";

    static final List<Entry> ENTRIES = Arrays.asList(
            // ---- 差し替えできないもの（先に判定） ----
            new Entry("Unity（IL2CPP）", NativeLib.Kind.APP_CODE, "^libil2cpp\\.so$", null, null,
                    "ゲーム本体のコードがこのファイルに変換されています。開発元が 64bit 版を出していない限り直せません。"),
            new Entry("Unity", NativeLib.Kind.APP_CODE, "^lib(unity|mono|monobdwgc-2\\.0)\\.so$", null, "(20\\d\\d|5)\\.\\d+\\.\\d+[abfp]\\d+",
                    "Unity の Android 64bit（ARM64）は IL2CPP 方式でしか動かないため、Mono 方式のゲームは差し替えできません。"),
            new Entry("Cocos2d-x（C++）", NativeLib.Kind.APP_CODE, "^libcocos2dcpp\\.so$", null, "cocos2d-x[- ]?\\d+\\.\\d+(\\.\\d+)?",
                    "ゲーム本体の C++ コードがエンジンと一体になっているため差し替えできません。"),
            // ---- スクリプト駆動エンジン ----
            new Entry("Cocos2d-x（Lua / JavaScript）", NativeLib.Kind.ENGINE_MAYBE, "^libcocos2d(lua|js)\\.so$", null, "cocos2d-x[- ]?\\d+\\.\\d+(\\.\\d+)?",
                    "ゲーム本体はスクリプトなので、同じバージョンのエンジンをテンプレートから 64bit ビルドすれば動く可能性があります（独自の C++ 拡張があると動きません）。"),
            new Entry("Cocos2d-x ランタイム", NativeLib.Kind.ENGINE_MAYBE, "^libcocos2d.*\\.so$", "Java_org_cocos2dx_", "cocos2d-x[- ]?\\d+\\.\\d+(\\.\\d+)?",
                    "エンジン部分のみなら、同じバージョンを 64bit ビルドすれば動く可能性があります。"),
            // ---- 汎用ライブラリ ----
            new Entry("SQLCipher", NativeLib.Kind.SWAPPABLE, "^libsqlcipher.*\\.so$", "Java_net_sqlcipher_", "\\b\\d\\.\\d+\\.\\d+ community\\b", SAME_VERSION),
            new Entry("JNA", NativeLib.Kind.SWAPPABLE, "^libjnidispatch\\.so$", "Java_com_sun_jna_", null, SAME_VERSION),
            new Entry("libGDX", NativeLib.Kind.SWAPPABLE, "^libgdx.*\\.so$", "Java_com_badlogic_gdx_", null, SAME_VERSION),
            new Entry("SDL2", NativeLib.Kind.SWAPPABLE, "^libSDL2.*\\.so$", "Java_org_libsdl_app_", null,
                    "SDL2 本体は差し替えられますが、同じ APK の libmain.so（ゲーム本体）が 32bit のままだと動きません。"),
            new Entry("Realm", NativeLib.Kind.SWAPPABLE, "^librealm-jni\\.so$", "Java_io_realm_", null, SAME_VERSION),
            new Entry("FFmpeg", NativeLib.Kind.SWAPPABLE, "^lib(av(codec|format|util|filter|device)|sw(scale|resample)|ffmpeg).*\\.so$", null, "FFmpeg version [^\\s]+|Lavc\\d+\\.\\d+\\.\\d+",
                    "同じバージョン・同じビルド設定の FFmpeg を arm64-v8a 向けにビルドしたものが必要です。"),
            new Entry("OpenAL Soft", NativeLib.Kind.SWAPPABLE, "^libopenal\\.so$", null, null, SAME_VERSION),
            new Entry("LAME", NativeLib.Kind.SWAPPABLE, "^libmp3lame\\.so$", null, null, SAME_VERSION),
            new Entry("Bugly / Crashlytics 等", NativeLib.Kind.SWAPPABLE, "^lib(Bugly|crashlytics.*)\\.so$", null, null,
                    "クラッシュ収集用です。同じ SDK バージョンの arm64-v8a 版を選んでください。"),
            // ---- NDK ランタイム ----
            new Entry("NDK C++ ランタイム", NativeLib.Kind.NDK_RUNTIME, "^lib(c\\+\\+_shared|gnustl_shared|stlport_shared)\\.so$", null, null,
                    "Android NDK に含まれています。差し替える他のライブラリをビルドした NDK と同じものを選んでください。")
    );

    private NativeLibCatalog() {}

    /** 判定して lib にラベル・種類・ヒント・バージョンを設定する。 */
    public static void classify(NativeLib lib, File extracted) {
        Set<String> syms = lib.elf != null ? lib.elf.javaSymbols : java.util.Collections.<String>emptySet();
        for (Entry e : ENTRIES) {
            boolean nameOk = e.fileName.matcher(lib.fileName).find();
            boolean symOk = e.javaPrefix != null && anyStartsWith(syms, e.javaPrefix);
            if (nameOk || symOk) {
                lib.label = e.label;
                lib.kind = e.kind;
                lib.hint = e.hint;
                if (e.version != null && extracted != null) lib.version = findString(extracted, e.version);
                return;
            }
        }
        lib.kind = NativeLib.Kind.UNKNOWN;
        lib.label = null;
        lib.hint = syms.isEmpty() && (lib.elf == null || !lib.elf.hasJniOnLoad)
                ? "他のライブラリから使われる部品のようです。同じものの 64bit 版が必要です。"
                : "アプリ固有のコードの可能性が高いです。JNI 関数がすべて一致する 64bit 版があれば差し替えられます。";
    }

    static boolean anyStartsWith(Set<String> s, String prefix) {
        for (String x : s) if (x.startsWith(prefix)) return true;
        return false;
    }

    /** ファイル中の印字可能な文字列を走査し、最初に正規表現に一致した部分を返す。 */
    static String findString(File f, Pattern p) {
        final int max = 64 * 1024 * 1024;
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[256 * 1024];
            StringBuilder cur = new StringBuilder();
            int n, total = 0;
            while ((n = in.read(buf)) > 0 && total < max) {
                total += n;
                for (int i = 0; i < n; i++) {
                    int c = buf[i] & 0xff;
                    if (c >= 0x20 && c < 0x7f) {
                        if (cur.length() < 512) cur.append((char) c);
                    } else {
                        if (cur.length() >= 4) {
                            Matcher m = p.matcher(cur);
                            if (m.find()) return m.group();
                        }
                        cur.setLength(0);
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    /** Android が NDK として公開しているシステムライブラリ（API 24 以降、これ以外の私的ライブラリは読み込めない） */
    public static final Set<String> NDK_SYSTEM_LIBS = new java.util.HashSet<>(Arrays.asList(
            "libc.so", "libm.so", "libdl.so", "liblog.so", "libandroid.so", "libEGL.so", "libGLESv1_CM.so",
            "libGLESv2.so", "libGLESv3.so", "libOpenSLES.so", "libOpenMAXAL.so", "libjnigraphics.so", "libz.so",
            "libstdc++.so", "libvulkan.so", "libmediandk.so", "libcamera2ndk.so", "libnativewindow.so",
            "libaaudio.so", "libamidi.so", "libsync.so", "libneuralnetworks.so", "libbinder_ndk.so",
            "libicu.so", "libnativehelper.so", "libRS.so", "libwebviewchromium_plat_support.so"));
}
