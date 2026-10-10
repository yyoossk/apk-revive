package com.yyoossk.apkrevive.core;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 32bit 専用 APK を「ユーザーが用意した 64bit 版 .so で差し替えて」arm64-v8a に対応させる計画。
 * 64bit 化はライブラリ単位の差し替えで、コードの変換は行わない。
 */
public final class Arm64Plan {

    public enum Status { MISSING, OK, WARN, ERROR }

    public static final class Item {
        public final NativeLib source;
        public File replacement;
        public ElfInfo replacementElf;
        public Status status = Status.MISSING;
        public final List<String> problems = new ArrayList<>();

        Item(NativeLib source) { this.source = source; }
    }

    public static final String TARGET_ABI = "arm64-v8a";
    public final String sourceAbi;
    public final Map<String, Item> items = new LinkedHashMap<>();

    public Arm64Plan(String sourceAbi, List<NativeLib> libs) {
        this.sourceAbi = sourceAbi;
        for (NativeLib l : libs) {
            if (sourceAbi.equals(l.abi)) items.put(l.fileName, new Item(l));
        }
    }

    /** 64bit 化の対象になるか（ARM 32bit だけを持ち、arm64-v8a が無い） */
    public static String pickSourceAbi(List<String> abis) {
        if (abis.contains(TARGET_ABI)) return null;
        if (abis.contains("armeabi-v7a")) return "armeabi-v7a";
        if (abis.contains("armeabi")) return "armeabi";
        return null;
    }

    /** 差し替えファイルを割り当てて検証する。名前が一致する項目が無ければ false。 */
    public boolean assign(String fileName, File file, ElfInfo elf) {
        Item it = items.get(fileName);
        if (it == null && elf.soname != null) it = items.get(elf.soname);
        if (it == null) return false;
        it.replacement = file;
        it.replacementElf = elf;
        validateAll();
        return true;
    }

    public void remove(String fileName) {
        Item it = items.get(fileName);
        if (it != null) {
            it.replacement = null;
            it.replacementElf = null;
        }
        validateAll();
    }

    public void validateAll() {
        Set<String> provided = new TreeSet<>(items.keySet());
        for (Item it : items.values()) validate(it, provided);
    }

    static void validate(Item it, Set<String> provided) {
        it.problems.clear();
        if (it.replacement == null) {
            it.status = Status.MISSING;
            return;
        }
        ElfInfo src = it.source.elf, dst = it.replacementElf;
        boolean error = false, warn = false;
        if (!dst.is64 || dst.machine != ElfInfo.EM_AARCH64) {
            it.problems.add("arm64-v8a 用ではありません（" + (dst.is64 ? "64bit" : "32bit") + " / " + dst.abiName() + "）");
            error = true;
        }
        if (src != null) {
            Set<String> missing = new TreeSet<>(src.javaSymbols);
            missing.removeAll(dst.javaSymbols);
            if (!missing.isEmpty()) {
                it.problems.add("JNI 関数が " + missing.size() + " 個足りません（例: " + missing.iterator().next() + "）。バージョン違いの可能性があります");
                error = true;
            }
            if (src.hasJniOnLoad && !dst.hasJniOnLoad) {
                it.problems.add("JNI_OnLoad がありません");
                error = true;
            }
            int extra = dst.javaSymbols.size() - (src.javaSymbols.size() - missing.size());
            if (missing.isEmpty() && extra > 0) {
                it.problems.add("元より JNI 関数が " + extra + " 個多いです。バージョンが違うと動作しない場合があります");
                warn = true;
            }
        }
        for (String n : dst.needed) {
            if (!NativeLibCatalog.NDK_SYSTEM_LIBS.contains(n) && !provided.contains(n)) {
                it.problems.add(n + " に依存していますが、APK にも Android の公開ライブラリにもありません");
                error = true;
            }
        }
        if (dst.minLoadAlign > 0 && dst.minLoadAlign < 16384) {
            it.problems.add("16KB ページの端末（Android 15 以降の一部）では読み込めません（整列 " + dst.minLoadAlign + " バイト）");
            warn = true;
        }
        it.status = error ? Status.ERROR : warn ? Status.WARN : Status.OK;
    }

    /** すべてのライブラリに問題のない差し替えがそろっているか（WARN は許可） */
    public boolean isComplete() {
        if (items.isEmpty()) return false;
        for (Item it : items.values()) {
            if (it.status == Status.MISSING || it.status == Status.ERROR) return false;
        }
        return true;
    }

    public int readyCount() {
        int n = 0;
        for (Item it : items.values()) if (it.status == Status.OK || it.status == Status.WARN) n++;
        return n;
    }

    /** 差し替えできないと分かっているライブラリが含まれるか */
    public boolean hasBlocker() {
        for (Item it : items.values()) if (it.source.kind == NativeLib.Kind.APP_CODE) return true;
        return false;
    }

    /** 再構築時に APK へ追加するエントリ（zip 内パス → ファイル） */
    public Map<String, File> additions() {
        Map<String, File> m = new LinkedHashMap<>();
        for (Item it : items.values()) {
            if (it.replacement != null) m.put("lib/" + TARGET_ABI + "/" + it.source.fileName, it.replacement);
        }
        return m;
    }
}
