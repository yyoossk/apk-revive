package com.yyoossk.apkrevive.core;

import java.util.ArrayList;
import java.util.List;

/** APK の診断結果。Android API に依存しない純粋な Java クラス。 */
public final class ApkReport {

    public enum Severity { ERROR, WARNING, INFO }

    public static final class Issue {
        public final Severity severity;
        public final String title;
        public final String detail;
        /** このアプリの再構築で直せるか */
        public final boolean fixable;

        public Issue(Severity severity, String title, String detail, boolean fixable) {
            this.severity = severity;
            this.title = title;
            this.detail = detail;
            this.fixable = fixable;
        }
    }

    public String packageName;
    public String versionName;
    public Integer versionCode;
    public Integer minSdk;
    public Integer targetSdk;
    /** 実際に使われる targetSdk（未指定なら minSdk、どちらも無ければ 1） */
    public int effectiveTargetSdk;
    public final List<String> abis = new ArrayList<>();
    /** 64bit 化の元になる ABI のライブラリ一覧（対象外なら空） */
    public final List<NativeLib> nativeLibs = new ArrayList<>();
    /** 64bit 化の元にする ABI（armeabi-v7a など）。対象外なら null */
    public String arm64SourceAbi;
    public boolean hasV1Signature;
    public boolean hasV2Signature;
    public boolean hasV3Signature;
    public boolean isSplitApk;
    public Boolean extractNativeLibs;
    /** intent-filter を持つのに android:exported が無いコンポーネント数 */
    public int componentsMissingExported;
    public int usesPermissionCount;
    public final List<Issue> issues = new ArrayList<>();
    /** この端末でインストールに最低限必要な targetSdk（制限なしなら 0） */
    public int deviceMinTargetSdk;
    /** 推奨する再構築後の targetSdk */
    public int recommendedTargetSdk;
    /** 再構築しても動かせない致命的な問題があるか */
    public boolean hasUnfixableError;

    public boolean hasNativeLibs() {
        return !abis.isEmpty();
    }
}
