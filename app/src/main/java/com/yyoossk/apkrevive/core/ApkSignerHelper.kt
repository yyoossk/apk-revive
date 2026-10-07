package com.yyoossk.apkrevive.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Calendar
import javax.security.auth.x500.X500Principal

/**
 * ユーザー専用の署名鍵で APK に v1 + v2 + v3 署名を付ける。
 * 鍵は Android Keystore 内で生成され、端末の外には出ない（アプリから取り出すこともできない）。
 */
object ApkSignerHelper {
    private const val ALIAS = "apk_revive_user_signer"
    private const val KEYSTORE = "AndroidKeyStore"

    data class VerifyResult(val ok: Boolean, val v1: Boolean, val v2: Boolean, val v3: Boolean, val errors: List<String>)

    private fun ensureKey(): Pair<PrivateKey, X509Certificate> {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (!ks.containsAlias(ALIAS)) {
            val start = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val end = Calendar.getInstance().apply { add(Calendar.YEAR, 30) }
            val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA1, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setCertificateSubject(X500Principal("CN=APK Revive User"))
                .setCertificateSerialNumber(BigInteger.valueOf(System.currentTimeMillis()))
                .setCertificateNotBefore(start.time)
                .setCertificateNotAfter(end.time)
                .build()
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE).run {
                initialize(spec)
                generateKeyPair()
            }
        }
        val key = ks.getKey(ALIAS, null) as PrivateKey
        val cert = ks.getCertificate(ALIAS) as X509Certificate
        return key to cert
    }

    fun sign(unsigned: File, out: File) {
        val (key, cert) = ensureKey()
        val signer = ApkSigner.SignerConfig.Builder("REVIVE", key, listOf(cert)).build()
        ApkSigner.Builder(listOf(signer))
            .setInputApk(unsigned)
            .setOutputApk(out)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .setCreatedBy("APK Revive")
            .build()
            .sign()
    }

    fun verify(apk: File): VerifyResult {
        val r = ApkVerifier.Builder(apk).build().verify()
        return VerifyResult(
            ok = r.isVerified,
            v1 = r.isVerifiedUsingV1Scheme,
            v2 = r.isVerifiedUsingV2Scheme,
            v3 = r.isVerifiedUsingV3Scheme,
            errors = r.errors.map { it.toString() }.take(5),
        )
    }

    /** 署名鍵の SHA-256 指紋（ユーザーに表示するため） */
    fun certificateFingerprint(): String {
        val (_, cert) = ensureKey()
        val d = java.security.MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        return d.joinToString(":") { "%02X".format(it) }.take(47) + "…"
    }
}
