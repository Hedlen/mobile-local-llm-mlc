package ai.mlc.gomoku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.porter.local.llm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val N = GomokuEngine.SIZE

class MainActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) { super.onCreate(state); setContent { GomokuApp() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun GomokuApp() {
    var board by remember { mutableStateOf(List(N * N) { 0 }) }; var winner by remember { mutableStateOf(0) }
    var analyze by remember { mutableStateOf(true) }; var profile by remember { mutableStateOf("稳健学习型") }; var level by remember { mutableStateOf("进阶") }
    var advice by remember { mutableStateOf("实时推荐由棋局引擎生成；联网时云端复盘优先，离线时自动使用本地模型。") }
    var suggested by remember { mutableStateOf<Int?>(null) }; var reviewing by remember { mutableStateOf(false) }; var downloading by remember { mutableStateOf(false) }; var reviewJob by remember { mutableStateOf<Job?>(null) }
    val context = LocalContext.current; val service = remember { GomokuModelService(context) }; val cloud = remember { CloudCoachClient(context) }; val scope = rememberCoroutineScope(); val client = remember { LocalLlmClient().apply { initialize() } }
    var modelReady by remember { mutableStateOf(service.installed()) }; var status by remember { mutableStateOf(if (modelReady) "本地 Qwen2.5 1.5B 已就绪" else "下载 1.5B 后可使用本地复盘") }
    DisposableEffect(Unit) { onDispose { client.cancel(); reviewJob?.cancel(); scope.launch { client.close() } } }

    fun updateSuggestion(position: List<Int>) {
        if (!analyze || winner != 0) return
        suggested = GomokuEngine.chooseMove(position, 1)
        val point = suggested ?: return
        advice = "推荐落点：${point / N + 1},${point % N + 1}（棋盘红点）\n${GomokuEngine.moveInsight(position, point, 1)}\n$level·$profile：稳住节奏，这一手是在为后续主动权铺路。"
    }
    fun play(index: Int) {
        if (winner != 0 || board[index] != 0) return
        val next = board.toMutableList(); next[index] = 1
        if (GomokuEngine.hasFive(next, index, 1)) { board = next; winner = 1; suggested = null; advice = "五连！你赢了。现在可以用本地 AI 复盘总结这一局。"; return }
        val ai = GomokuEngine.chooseMove(next)
        if (ai != null) { next[ai] = 2; if (GomokuEngine.hasFive(next, ai, 2)) winner = 2 } else winner = 3
        board = next
        if (winner != 0) { suggested = null; advice = if (winner == 2) "AI 五连获胜。现在可以用本地 AI 复盘找出关键转折。" else "和棋。现在可以用本地 AI 复盘总结这一局。" } else updateSuggestion(next)
    }
    fun review() {
        if (reviewing) return
        val position = board; val point = GomokuEngine.chooseMove(position, 1)
        val insight = point?.let { GomokuEngine.moveInsight(position, it, 1) } ?: "棋局已结束，重点回看最后几手的攻防取舍。"
        val moveCount = position.count { it != 0 }; val capturedWinner = winner; val capturedProfile = "$level·$profile"
        val system = "你是温暖、专业的五子棋复盘教练。只依据给定局面事实，写三段以内的简短复盘：本局亮点、一个可训练点、下一局可执行建议。不得虚构坐标、胜负或棋形。"
        val prompt = "玩家等级与风格：$capturedProfile；已落子数：$moveCount；局面事实：$insight；赛果：${resultName(capturedWinner)}。"
        reviewing = true; advice = if (cloud.isOnline() && cloud.isConfigured()) "云端 AI 正在生成个性化复盘；网络异常会自动转本地模型。" else "本地 Qwen2.5 1.5B 正在复盘；棋盘仍可继续操作。"
        reviewJob = scope.launch {
            val result = withContext(Dispatchers.Default) {
                val cloudReview = if (cloud.isOnline() && cloud.isConfigured()) runCatching { cloud.review(system, prompt) }.getOrNull() else null
                if (cloudReview != null) "云端 AI 复盘\n" + GameReview.finish(cloudReview, capturedProfile, moveCount, insight, capturedWinner)
                else if (modelReady) runCatching {
                    if (client.state != LocalLlmClientState.READY) client.load(service.modelId, service.directory().absolutePath, service.modelLib)
                    var output = ""
                    client.stream(LocalLlmRequest(service.modelId, listOf(LocalLlmMessage(LocalLlmRole.SYSTEM, system), LocalLlmMessage(LocalLlmRole.USER, prompt)), maxOutputTokens = 40, temperature = 0.65f, topP = 0.9f)).collect { event -> if (event is LocalLlmEvent.Delta) output += event.text }
                    "本地 AI 复盘\n" + GameReview.finish(output, capturedProfile, moveCount, insight, capturedWinner)
                }.getOrElse { "棋局教练\n" + GameReview.fallback(capturedProfile, moveCount, insight, capturedWinner) }
                else "棋局教练\n" + GameReview.fallback(capturedProfile, moveCount, insight, capturedWinner)
            }
            advice = result; reviewing = false
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("五子棋 · 本地 AI 助手") }) }) { padding -> Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) { Text(if (winner == 0) "你执黑 · AI 执白" else "对局结束"); Row { Text("实时建议"); Switch(analyze, { analyze = it }) } }
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xffd9a85b)).pointerInput(board) { detectTapGestures { tap -> val cell = size.width / (N - 1); val col = (tap.x / cell).roundToInt().coerceIn(0, N - 1); val row = (tap.y / cell).roundToInt().coerceIn(0, N - 1); play(row * N + col) } }) { val cell = size.width / (N - 1); for (line in 0 until N) { drawLine(Color(0xff5b3a1f), Offset(0f, line * cell), Offset(size.width, line * cell)); drawLine(Color(0xff5b3a1f), Offset(line * cell, 0f), Offset(line * cell, size.width)) }; board.forEachIndexed { index, piece -> if (piece > 0) drawCircle(if (piece == 1) Color(0xff202124) else Color.White, cell * .38f, Offset((index % N) * cell, (index / N) * cell)) }; suggested?.let { drawCircle(Color(0x99e53935), cell * .24f, Offset((it % N) * cell, (it / N) * cell)) } }
        OutlinedTextField(profile, { profile = it }, label = { Text("玩家风格") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("初学", "进阶", "高手").forEach { choice -> Button(onClick = { level = choice }, enabled = level != choice) { Text(choice) } } }
        Text(status); Text(if (cloud.isOnline() && cloud.isConfigured()) "AI 通道：云端优先，端侧兜底" else "AI 通道：端侧优先（云端令牌未配置或网络不可用）")
        if (!modelReady) Button(onClick = { scope.launch { downloading = true; status = "下载 1.5B 模型中…"; runCatching { service.install { status = it }; modelReady = true; status = "本地 Qwen2.5 1.5B 已就绪" }.onFailure { status = "下载失败：${it.message}" }; downloading = false } }, enabled = !downloading, modifier = Modifier.fillMaxWidth()) { Text(if (downloading) "1.5B 下载中…" else "下载 Qwen2.5 1.5B（约 870 MB）") }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text("AI 局面分析", style = MaterialTheme.typography.titleMedium); Text(advice) } }
        Button(onClick = ::review, enabled = !reviewing && (modelReady || (cloud.isOnline() && cloud.isConfigured())), modifier = Modifier.fillMaxWidth()) { Text(if (reviewing) "AI 复盘中…" else "智能复盘（云端优先 / 本地兜底）") }
        Button(onClick = { client.cancel(); reviewJob?.cancel(); reviewing = false; board = List(N * N) { 0 }; winner = 0; suggested = null; advice = "新棋局已开始。你执黑先行；实时棋局不会占用大模型。" }, modifier = Modifier.fillMaxWidth()) { Text("重新开始") }
        Text("实时落子由棋局搜索引擎完成；1.5B 仅在你主动复盘时后台运行，以控制卡顿、功耗和温度。")
    } }
}

private fun resultName(winner: Int) = when (winner) { 1 -> "玩家胜"; 2 -> "AI 胜"; 3 -> "和棋"; else -> "进行中" }
