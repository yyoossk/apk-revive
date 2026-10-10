package com.yyoossk.apkrevive.core;

/** APK 内の 1 つのネイティブライブラリの診断結果。 */
public final class NativeLib {

    /** 64bit 化の見込み */
    public enum Kind {
        /** 汎用のオープンソースライブラリ。同じバージョンの 64bit 版があれば差し替えられる */
        SWAPPABLE,
        /** NDK の C++ ランタイム。NDK から 64bit 版を用意できる */
        NDK_RUNTIME,
        /** スクリプトで動くエンジン。差し替えられる可能性がある（要検証） */
        ENGINE_MAYBE,
        /** ゲーム／アプリ固有のコードを含むため差し替えできない */
        APP_CODE,
        /** 判定できない。JNI 関数が一致する 64bit 版があれば差し替えられる */
        UNKNOWN
    }

    /** zip 内のパス（例: lib/armeabi-v7a/libfoo.so） */
    public String path;
    public String abi;
    public String fileName;
    public long size;
    public ElfInfo elf;
    public Kind kind = Kind.UNKNOWN;
    /** 判定したライブラリ名（例: SQLCipher） */
    public String label;
    /** 見つかったバージョン文字列（無ければ null） */
    public String version;
    /** 64bit 版の入手先などのヒント */
    public String hint;
}
