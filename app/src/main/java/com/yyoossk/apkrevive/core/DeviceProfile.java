package com.yyoossk.apkrevive.core;

import java.util.Arrays;
import java.util.List;

/** 診断に使う端末情報。テストしやすいよう Android の Build クラスからは切り離している。 */
public final class DeviceProfile {
    public final int sdkInt;
    public final List<String> abis64;
    public final List<String> abis32;

    public DeviceProfile(int sdkInt, String[] abis64, String[] abis32) {
        this.sdkInt = sdkInt;
        this.abis64 = Arrays.asList(abis64);
        this.abis32 = Arrays.asList(abis32);
    }

    /**
     * この OS がインストールを許可する最低 targetSdk。
     * Android 14 (API 34) は 23 未満、Android 15 (API 35) 以降は 24 未満を拒否する。
     */
    public int minInstallableTargetSdk() {
        if (sdkInt >= 35) return 24;
        if (sdkInt >= 34) return 23;
        return 0;
    }

    public boolean is64BitOnly() {
        return !abis64.isEmpty() && abis32.isEmpty();
    }

    public boolean supportsAbi(String abi) {
        return abis64.contains(abi) || abis32.contains(abi);
    }
}
