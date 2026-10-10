package com.yyoossk.apkrevive

import android.app.Application
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yyoossk.apkrevive.core.Arm64Plan
import com.yyoossk.apkrevive.core.ElfInfo
import com.yyoossk.apkrevive.core.ApkAnalyzer
import com.yyoossk.apkrevive.core.ApkRebuilder
import com.yyoossk.apkrevive.core.ApkReport
import com.yyoossk.apkrevive.core.ApkSignerHelper
import com.yyoossk.apkrevive.core.DeviceProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Screen {
    data object Home : Screen
    data class Working(val message: String, val progress: Float? = null) : Screen
    data class Report(val fileName: String, val report: ApkReport) : Screen
    data class Options(
        val fileName: String,
        val report: ApkReport,
        /** Arm64Plan は可変なので、更新のたびに増やして再描画させる */
        val planRev: Int = 0,
        val message: String? = null,
    ) : Screen
    data class Done(
        val outName: String,
        val report: ApkReport,
        val changes: List<String>,
        val verify: ApkSignerHelper.VerifyResult,
        val saved: Boolean = false,
    ) : Screen
    data class Failed(val message: String) : Screen
}

class ReviveViewModel(app: Application) : AndroidViewModel(app) {

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen

    val device = DeviceProfile(
        Build.VERSION.SDK_INT,
        Build.SUPPORTED_64_BIT_ABIS,
        Build.SUPPORTED_32_BIT_ABIS,
    )

    private val workDir get() = File(getApplication<Application>().cacheDir, "work").apply { mkdirs() }
    private val inputFile get() = File(workDir, "input.apk")
    private val unsignedFile get() = File(workDir, "unsigned.apk")
    private val outputFile get() = File(workDir, "output.apk")
    private val replDir get() = File(workDir, "repl").apply { mkdirs() }

    /** 64bit 化の計画（対象外の APK では null） */
    var plan: Arm64Plan? = null
        private set

    fun goHome() {
        workDir.deleteRecursively()
        plan = null
        _screen.value = Screen.Home
    }

    fun openApk(uri: Uri) {
        viewModelScope.launch {
            _screen.value = Screen.Working("APK を読み込んでいます…")
            try {
                val name = withContext(Dispatchers.IO) {
                    val ctx = getApplication<Application>()
                    val display = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "selected.apk"
                    ctx.contentResolver.openInputStream(uri)!!.use { input ->
                        inputFile.outputStream().use { input.copyTo(it) }
                    }
                    display
                }
                _screen.value = Screen.Working("診断しています…")
                val report = withContext(Dispatchers.IO) {
                    ApkAnalyzer.analyze(inputFile, device, File(workDir, "scan"))
                }
                plan = report.arm64SourceAbi?.let { Arm64Plan(it, report.nativeLibs) }
                    ?.takeIf { it.items.isNotEmpty() }
                _screen.value = Screen.Report(name, report)
            } catch (e: Exception) {
                _screen.value = Screen.Failed("読み込みに失敗しました。\n${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun showOptions(s: Screen.Report) {
        _screen.value = Screen.Options(s.fileName, s.report)
    }

    fun backToReport(s: Screen.Options) {
        _screen.value = Screen.Report(s.fileName, s.report)
    }

    /** ユーザーが選んだ 64bit 版 .so を取り込み、ファイル名（または SONAME）で対応づけて検証する */
    fun addReplacements(uris: List<Uri>, s: Screen.Options) {
        val p = plan ?: return
        viewModelScope.launch {
            val notes = mutableListOf<String>()
            withContext(Dispatchers.IO) {
                val ctx = getApplication<Application>()
                for (uri in uris) {
                    val name = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: continue
                    val safe = name.replace(Regex("[^A-Za-z0-9._+-]"), "_")
                    val f = File(replDir, "${System.nanoTime()}_$safe")
                    try {
                        ctx.contentResolver.openInputStream(uri)!!.use { input -> f.outputStream().use { input.copyTo(it) } }
                        val elf = ElfInfo.parse(f)
                        if (!p.assign(name, f, elf)) {
                            notes += "$name：この APK に同じ名前のライブラリがありません"
                            f.delete()
                        }
                    } catch (e: Exception) {
                        notes += "$name：読み込めません（${e.message}）"
                        f.delete()
                    }
                }
            }
            _screen.value = s.copy(planRev = s.planRev + 1, message = notes.joinToString("\n").ifEmpty { null })
        }
    }

    fun removeReplacement(fileName: String, s: Screen.Options) {
        plan?.items?.get(fileName)?.replacement?.delete()
        plan?.remove(fileName)
        _screen.value = s.copy(planRev = s.planRev + 1, message = null)
    }

    fun rebuild(s: Screen.Options, targetSdk: Int, addExported: Boolean, make64: Boolean = false) {
        viewModelScope.launch {
            try {
                val opt = ApkRebuilder.Options().apply {
                    this.targetSdk = targetSdk
                    this.addMissingExported = addExported
                    val p = plan
                    if (make64 && p != null && p.isComplete) this.additions = p.additions()
                }
                _screen.value = Screen.Working("再構築しています…", 0f)
                val result = withContext(Dispatchers.IO) {
                    ApkRebuilder.rebuild(inputFile, unsignedFile, opt) { done, total ->
                        _screen.value = Screen.Working("再構築しています…", done.toFloat() / total)
                    }
                }
                _screen.value = Screen.Working("署名しています（v1 + v2 + v3）…")
                val verify = withContext(Dispatchers.IO) {
                    ApkSignerHelper.sign(unsignedFile, outputFile)
                    unsignedFile.delete()
                    ApkSignerHelper.verify(outputFile)
                }
                val r = s.report
                val base = (r.packageName ?: "app").replace(Regex("[^A-Za-z0-9._-]"), "_")
                val ver = (r.versionName ?: "").replace(Regex("[^A-Za-z0-9._-]"), "_")
                val outName = listOf(base, ver, "revived").filter { it.isNotEmpty() }.joinToString("-") + ".apk"
                val changes = result.changes + "ユーザー専用の鍵で v1 + v2 + v3 署名"
                _screen.value = Screen.Done(outName, r, changes, verify)
            } catch (e: Exception) {
                _screen.value = Screen.Failed("再構築に失敗しました。\n${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** ユーザーが保存先を選んだ（SAF の CreateDocument）。ストレージ権限は使わない。 */
    fun saveTo(uri: Uri, s: Screen.Done) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)!!.use { out ->
                        outputFile.inputStream().use { it.copyTo(out) }
                    }
                }
                _screen.value = s.copy(saved = true)
            } catch (e: Exception) {
                _screen.value = Screen.Failed("保存に失敗しました。\n${e.message}")
            }
        }
    }

    fun fingerprint(): String = runCatching { ApkSignerHelper.certificateFingerprint() }.getOrDefault("-")
}
