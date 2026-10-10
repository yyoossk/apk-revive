package com.yyoossk.apkrevive.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yyoossk.apkrevive.ReviveViewModel
import com.yyoossk.apkrevive.Screen
import com.yyoossk.apkrevive.core.ApkReport
import com.yyoossk.apkrevive.core.Arm64Plan
import com.yyoossk.apkrevive.core.NativeLib

private const val APK_MIME = "application/vnd.android.package-archive"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviveApp(screen: Screen, vm: ReviveViewModel) {
    val canGoBack = screen is Screen.Report || screen is Screen.Options || screen is Screen.Done || screen is Screen.Failed
    BackHandler(enabled = canGoBack) {
        if (screen is Screen.Options) vm.backToReport(screen) else vm.goHome()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (screen) {
                            is Screen.Report -> "診断結果"
                            is Screen.Options -> "修正内容"
                            is Screen.Done -> "完了"
                            else -> "APKリバイブ"
                        }
                    )
                },
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = {
                            if (screen is Screen.Options) vm.backToReport(screen) else vm.goHome()
                        }) { Icon(Icons.Filled.ArrowBack, contentDescription = "戻る") }
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (screen) {
                Screen.Home -> HomeScreen(vm)
                is Screen.Working -> WorkingScreen(screen)
                is Screen.Report -> ReportScreen(screen, vm)
                is Screen.Options -> OptionsScreen(screen, vm)
                is Screen.Done -> DoneScreen(screen, vm)
                is Screen.Failed -> FailedScreen(screen, vm)
            }
        }
    }
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content(); Spacer(Modifier.height(24.dp)) }
}

// ---------------- 利用条件 ----------------

@Composable
fun TermsScreen(onAccept: () -> Unit) {
    var checked by remember { mutableStateOf(false) }
    Scaffold { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("APKリバイブへようこそ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("古いアプリを、最近の Android にインストールできる形に作り直すツールです。")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ご利用の前に", fontWeight = FontWeight.Bold)
                    Bullet("自分で作ったアプリ、または個人で正当に入手し利用する権利のあるアプリにだけ使ってください。")
                    Bullet("広告の削除・課金やライセンス確認の回避など、アプリの改造機能はありません。")
                    Bullet("作り直したアプリは署名が変わるため、元のアプリのアンインストールが必要で、データは引き継がれません。")
                    Bullet("処理はすべて端末内で行われ、ファイルが外部に送信されることはありません。")
                    Bullet("動作を保証するものではありません。")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = { checked = it })
                Text("上記を理解し、同意します")
            }
            Button(onClick = onAccept, enabled = checked, modifier = Modifier.fillMaxWidth()) { Text("はじめる") }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row { Text("・"); Text(text, style = MaterialTheme.typography.bodyMedium) }
}

// ---------------- ホーム ----------------

@Composable
private fun HomeScreen(vm: ReviveViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.openApk(uri)
    }
    val d = vm.device
    Page {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("古いアプリを、いまの Android へ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("APK を選ぶと、動かない原因を診断して、必要最小限の修正で作り直します。", style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = { picker.launch(arrayOf(APK_MIME, "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("APK ファイルを選ぶ") }
            }
        }
        SectionCard("この端末") {
            InfoRow("Android", "API ${d.sdkInt}")
            InfoRow("インストールに必要な targetSdk", if (d.minInstallableTargetSdk() > 0) "${d.minInstallableTargetSdk()} 以上" else "制限なし")
            InfoRow("CPU", if (d.is64BitOnly()) "64bit専用" else "32bit / 64bit 両対応")
        }
        SectionCard("できること") {
            Text("・targetSdk の引き上げ（インストールできる最低ラインまで）", style = MaterialTheme.typography.bodyMedium)
            Text("・新しい署名方式（v2 / v3）での再署名", style = MaterialTheme.typography.bodyMedium)
            Text("・zipalign 相当の整列、android:exported の補完", style = MaterialTheme.typography.bodyMedium)
            Text("・32bit 専用アプリの 64bit 化（ライブラリの 64bit 版を用意できる場合）", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text("できないこと", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text("・ゲームエンジン本体などアプリ固有コードの 64bit 化　・アプリの改造", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ---------------- 処理中 ----------------

@Composable
private fun WorkingScreen(s: Screen.Working) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (s.progress != null) {
            LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
        } else {
            CircularProgressIndicator()
        }
        Spacer(Modifier.height(16.dp))
        Text(s.message)
    }
}

// ---------------- 診断結果 ----------------

@Composable
private fun ReportScreen(s: Screen.Report, vm: ReviveViewModel) {
    val r = s.report
    Page {
        AppInfoCard(s.fileName, r)
        Text("見つかった問題", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        r.issues.forEach { IssueCard(it) }
        if (r.hasUnfixableError) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    "このアプリは再構築してもこの端末では動きません。",
                    Modifier.padding(16.dp), fontWeight = FontWeight.Bold,
                )
            }
        }
        Button(
            onClick = { vm.showOptions(s) },
            enabled = !r.isSplitApk,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (r.hasUnfixableError) "それでも再構築する" else "修正内容を選ぶ") }
    }
}

@Composable
private fun AppInfoCard(fileName: String, r: ApkReport) {
    SectionCard(r.packageName ?: fileName) {
        Text(fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        InfoRow("バージョン", listOfNotNull(r.versionName, r.versionCode?.let { "($it)" }).joinToString(" ").ifEmpty { "-" })
        InfoRow("minSdk / targetSdk", "${r.minSdk ?: "-"} / ${r.targetSdk ?: "未指定(${r.effectiveTargetSdk})"}")
        InfoRow("CPU (ABI)", if (r.abis.isEmpty()) "ネイティブコードなし" else r.abis.joinToString(", "))
        InfoRow(
            "署名",
            listOfNotNull("v1".takeIf { r.hasV1Signature }, "v2".takeIf { r.hasV2Signature }, "v3".takeIf { r.hasV3Signature })
                .joinToString(" + ").ifEmpty { "なし" },
        )
    }
}

@Composable
private fun IssueCard(i: ApkReport.Issue) {
    val (bg, icon, tint) = when (i.severity) {
        ApkReport.Severity.ERROR -> Triple(MaterialTheme.colorScheme.errorContainer, Icons.Filled.Close, MaterialTheme.colorScheme.error)
        ApkReport.Severity.WARNING -> Triple(MaterialTheme.colorScheme.tertiaryContainer, Icons.Filled.Warning, MaterialTheme.colorScheme.tertiary)
        ApkReport.Severity.INFO -> Triple(MaterialTheme.colorScheme.surfaceVariant, Icons.Filled.Info, MaterialTheme.colorScheme.primary)
    }
    Card(colors = CardDefaults.cardColors(containerColor = bg)) {
        Row(Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(i.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(
                        if (i.fixable) "修正できます" else "直せません",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (i.fixable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(i.detail, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ---------------- 修正内容 ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionsScreen(s: Screen.Options, vm: ReviveViewModel) {
    val r = s.report
    val current = r.effectiveTargetSdk
    val choices = listOf(r.recommendedTargetSdk, 28, 30, 33, 35).filter { it >= current }.distinct().sorted()
    var target by remember { mutableIntStateOf(r.recommendedTargetSdk) }
    var addExported by remember { mutableStateOf(true) }
    var make64 by remember { mutableStateOf(true) }
    val plan = vm.plan
    val planReady = plan != null && plan.isComplete
    Page {
        if (plan != null) {
            Arm64Section(s, vm, plan, make64 && planReady, onToggle = { make64 = it })
        }
        SectionCard("targetSdk") {
            Text("現在: $current", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { v ->
                    FilterChip(
                        selected = target == v,
                        onClick = { target = v },
                        label = { Text(if (v == r.recommendedTargetSdk) "$v（推奨）" else "$v") },
                    )
                }
            }
            val hint = when {
                target == current -> "targetSdk は変更しません。"
                target <= 24 -> "インストールできる最低ラインです。アプリの動作への影響がいちばん少ない設定です。"
                target < 30 -> "一部の動作（ファイルの扱い・通知など）が変わる可能性があります。"
                else -> "ストレージや権限の仕組みが大きく変わるため、正しく動かない可能性が高くなります。"
            }
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (target >= 31 && r.componentsMissingExported > 0) {
            SectionCard("android:exported の補完") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "intent-filter を持つ ${r.componentsMissingExported} 個のコンポーネントに exported=\"true\" を追加（targetSdk 31 以上で必須）",
                        Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(checked = addExported, onCheckedChange = { addExported = it })
                }
            }
        }
        SectionCard("常に行う処理") {
            LockedRow("古い署名を外して v1 + v2 + v3 で再署名")
            LockedRow("resources.arsc を非圧縮で格納")
            LockedRow("zipalign 相当の整列（ネイティブライブラリは 16KB 境界）")
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Text(
                "アプリのプログラム本体（dex）には手を加えません。",
                Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            onClick = { vm.rebuild(s, target, addExported, make64 && planReady) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (make64 && planReady) "64bit 化して再構築する" else "再構築する") }
    }
}

// ---------------- 64bit 化 ----------------

@Composable
private fun Arm64Section(
    s: Screen.Options,
    vm: ReviveViewModel,
    plan: Arm64Plan,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.addReplacements(uris, s)
    }
    SectionCard("64bit 化（ライブラリの差し替え）") {
        Text(
            "${plan.sourceAbi} 用のライブラリを、あなたが用意した arm64-v8a 用の .so で差し替えます。" +
                "コードを変換するのではなく、同じライブラリの 64bit 版を入れる方式です。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("${plan.readyCount()} / ${plan.items.size} 個そろっています", fontWeight = FontWeight.Medium)
        LinearProgressIndicator(
            progress = { if (plan.items.isEmpty()) 0f else plan.readyCount().toFloat() / plan.items.size },
            modifier = Modifier.fillMaxWidth(),
        )
        plan.items.values.forEach { LibRow(it, onRemove = { vm.removeReplacement(it.source.fileName, s) }) }
        if (s.message != null) {
            Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (plan.hasBlocker()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    "差し替えできないライブラリが含まれています。このアプリは 64bit 化できない見込みです。",
                    Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        OutlinedButton(
            onClick = { picker.launch(arrayOf("application/octet-stream", "application/x-sharedlib", "*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("64bit 版の .so を選ぶ（複数可）") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (plan.isComplete) "64bit 化する（32bit 版も残します）" else "すべてそろうと 64bit 化できます",
                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            )
            Switch(checked = enabled, onCheckedChange = onToggle, enabled = plan.isComplete)
        }
    }
}

@Composable
private fun LibRow(it: Arm64Plan.Item, onRemove: () -> Unit) {
    val lib = it.source
    val (badge, color) = when (lib.kind) {
        NativeLib.Kind.SWAPPABLE -> "差し替え可" to MaterialTheme.colorScheme.primary
        NativeLib.Kind.NDK_RUNTIME -> "NDK" to MaterialTheme.colorScheme.primary
        NativeLib.Kind.ENGINE_MAYBE -> "要検証" to MaterialTheme.colorScheme.tertiary
        NativeLib.Kind.APP_CODE -> "差し替え不可" to MaterialTheme.colorScheme.error
        NativeLib.Kind.UNKNOWN -> "不明" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusIcon = when (it.status) {
        Arm64Plan.Status.OK -> Icons.Filled.CheckCircle
        Arm64Plan.Status.WARN -> Icons.Filled.Warning
        Arm64Plan.Status.ERROR -> Icons.Filled.Close
        Arm64Plan.Status.MISSING -> null
    }
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(lib.fileName, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            val sub = listOfNotNull(lib.label, lib.version, "JNI ${lib.elf?.javaSymbols?.size ?: 0}個").joinToString(" · ")
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(badge, style = MaterialTheme.typography.labelSmall, color = color)
        if (statusIcon != null) {
            Spacer(Modifier.width(6.dp))
            Icon(
                statusIcon, contentDescription = null, modifier = Modifier.size(20.dp),
                tint = when (it.status) {
                    Arm64Plan.Status.OK -> MaterialTheme.colorScheme.primary
                    Arm64Plan.Status.WARN -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.error
                },
            )
        }
    }
    if (it.status == Arm64Plan.Status.MISSING) {
        lib.hint?.let { h -> Text(h, style = MaterialTheme.typography.bodySmall) }
    } else {
        it.problems.forEach { p -> Text("・$p", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = onRemove) { Text("選び直す") }
    }
}

@Composable
private fun LockedRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------- 完了 ----------------

@Composable
private fun DoneScreen(s: Screen.Done, vm: ReviveViewModel) {
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(APK_MIME)) { uri ->
        if (uri != null) vm.saveTo(uri, s)
    }
    Page {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("再構築できました", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(s.outName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
        SectionCard("行った変更") {
            s.changes.forEach { Text("・$it", style = MaterialTheme.typography.bodyMedium) }
        }
        SectionCard("署名の検証") {
            InfoRow("結果", if (s.verify.ok) "OK" else "NG")
            InfoRow("v1 / v2 / v3", listOf(s.verify.v1, s.verify.v2, s.verify.v3).joinToString(" / ") { if (it) "✓" else "–" })
            InfoRow("鍵の指紋", vm.fingerprint())
            s.verify.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        Button(
            onClick = { saver.launch(s.outName) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (s.saved) "別の場所にも保存する" else "保存する") }
        if (s.saved) {
            SectionCard("インストールのしかた") {
                Text("1. 元のアプリが入っている場合はアンインストール", style = MaterialTheme.typography.bodyMedium)
                Text("2.「ファイル」アプリで保存した APK を開く", style = MaterialTheme.typography.bodyMedium)
                Text("3. 初回は「この提供元のアプリを許可」をオンにする（ファイルアプリに対して）", style = MaterialTheme.typography.bodyMedium)
                Text("4. Play プロテクトの確認が出たら内容を確かめて進む", style = MaterialTheme.typography.bodyMedium)
            }
        }
        OutlinedButton(onClick = { vm.goHome() }, modifier = Modifier.fillMaxWidth()) { Text("別の APK を処理する") }
    }
}

// ---------------- エラー ----------------

@Composable
private fun FailedScreen(s: Screen.Failed, vm: ReviveViewModel) {
    Page {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Text(s.message, Modifier.padding(16.dp))
        }
        Button(onClick = { vm.goHome() }, modifier = Modifier.fillMaxWidth()) { Text("最初に戻る") }
    }
}

// ---------------- 共通部品 ----------------

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Color.Unspecified)
    }
}
