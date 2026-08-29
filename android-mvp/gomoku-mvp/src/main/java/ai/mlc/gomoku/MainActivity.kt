package ai.mlc.gomoku

import android.os.Bundle
import com.porter.local.llm.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val N = GomokuEngine.SIZE
class MainActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) { super.onCreate(state); setContent { GomokuApp() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun GomokuApp() {
    var board by remember { mutableStateOf(List(N * N) { 0 }) }; var winner by remember { mutableStateOf(0) }
    var analyze by remember { mutableStateOf(true) }; var profile by remember { mutableStateOf("稳健学习型") }; var advice by remember { mutableStateOf("勾选 AI 分析后，每次落子会显示局面建议。") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val service = remember { GomokuModelService(context) }; val scope = rememberCoroutineScope(); val client = remember { LocalLlmClient().apply { initialize() } }
    var modelReady by remember { mutableStateOf(service.installed()) }; var status by remember { mutableStateOf(if (modelReady) "本地 Qwen2.5 0.5B 已就绪" else "请先下载本地模型") }
    var suggested by remember { mutableStateOf<Int?>(null) }; var analyzing by remember { mutableStateOf(false) }; var analysisJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) { onDispose { client.cancel(); analysisJob?.cancel(); scope.launch { client.close() } } }
    fun tap(i: Int) {
        if (winner != 0 || analyzing || board[i] != 0) return
        val next = board.toMutableList(); next[i] = 1; suggested = null
        if (GomokuEngine.hasFive(next, i, 1)) { board = next; winner = 1; advice = "五连！你赢了。"; return }
        val ai = GomokuEngine.chooseMove(next)
        if (ai != null) { next[ai] = 2; if (GomokuEngine.hasFive(next, ai, 2)) winner = 2 } else winner = 3
        board = next
        if (analyze && modelReady && winner == 0) {
            val coaching = GomokuEngine.chooseMove(next, 1)
            suggested = coaching
            val coordinate = coaching?.let { "${it / N + 1},${it % N + 1}" } ?: "无"
            analyzing = true; advice = "推荐落点：$coordinate（棋盘红点）\n正在用本地模型生成解释…"
            analysisJob = scope.launch {
                runCatching {
                    if (client.state != LocalLlmClientState.READY) client.load(service.modelId, service.directory().absolutePath, service.modelLib)
                    var out = ""
                    client.stream(LocalLlmRequest(service.modelId, listOf(
                        LocalLlmMessage(LocalLlmRole.SYSTEM, "你是友善的五子棋教练。规则引擎已完成棋步计算。不要输出坐标，只写两句简短中文：说明兼顾进攻和防守，再结合玩家风格鼓励。"),
                        LocalLlmMessage(LocalLlmRole.USER, "玩家风格：$profile。请给本回合一句战术提醒和一句鼓励。"),
                    ), maxOutputTokens = 28, temperature = .2f)).collect { event -> if (event is LocalLlmEvent.Delta) out += event.text }
                    advice = CoachResponse.finish(out, coordinate, profile)
                }.onFailure { advice = CoachResponse.finish("", coordinate, profile) }
                analyzing = false
            }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("五子棋 · 本地 AI 助手") }) }) { p -> Column(Modifier.padding(p).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) { Text(if (winner == 0) "你执黑 · AI 执白" else "对局结束"); Row { Text("AI 分析"); Switch(analyze, { analyze = it }) } }
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xffd9a85b)).pointerInput(board, analyzing) { detectTapGestures { q -> val cell = size.width / (N - 1); val c = (q.x / cell).roundToInt().coerceIn(0, N - 1); val r = (q.y / cell).roundToInt().coerceIn(0, N - 1); tap(r * N + c) } }) { val cell = size.width / (N - 1); for (i in 0 until N) { drawLine(Color(0xff5b3a1f), Offset(0f, i * cell), Offset(size.width, i * cell)); drawLine(Color(0xff5b3a1f), Offset(i * cell, 0f), Offset(i * cell, size.width)) }; board.forEachIndexed { i, v -> if (v > 0) drawCircle(if (v == 1) Color(0xff202124) else Color.White, cell * .38f, Offset((i % N) * cell, (i / N) * cell)) }; suggested?.let { drawCircle(Color(0x99e53935), cell * .24f, Offset((it % N) * cell, (it / N) * cell)) } }
        OutlinedTextField(profile, { profile = it }, label = { Text("玩家风格") }, modifier = Modifier.fillMaxWidth())
        Text(status)
        if (!modelReady) Button(onClick = { scope.launch { status = "下载中…"; runCatching { service.install { status = it }; modelReady = true; status = "本地模型已就绪" }.onFailure { status = "下载失败：${it.message}" } } }, modifier = Modifier.fillMaxWidth()) { Text("下载 Qwen2.5 0.5B") }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text("AI 局面分析", style = MaterialTheme.typography.titleMedium); Text(advice) } }
        Button(onClick = { client.cancel(); analysisJob?.cancel(); analyzing = false; board = List(N * N) { 0 }; winner = 0; suggested = null; advice = "新棋局已开始。你执黑先行。" }, modifier = Modifier.fillMaxWidth()) { Text("重新开始") }
        Text("模型测试与 Benchmark 已独立为另一款 App；本应用专注对弈和实时分析。")
    } }
}
