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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.porter.local.llm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

private const val N = GomokuEngine.SIZE
private val Ink = Color(0xff25232A)
private val Plum = Color(0xff6750A4)
private val Wood = Color(0xffDCAF62)

class MainActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) { super.onCreate(state); setContent { GomokuApp() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun GomokuApp() {
    var board by remember { mutableStateOf(List(N * N) { 0 }) }
    var winner by remember { mutableStateOf(0) }
    var level by remember { mutableStateOf("进阶") }
    var persona by remember { mutableStateOf("耐心教练") }
    var suggested by remember { mutableStateOf<Int?>(null) }
    var aiThinking by remember { mutableStateOf(false) }
    var reviewing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("我会认真和你下这一局。你先落子吧。") }
    var moveCount by remember { mutableStateOf(0) }
    var actionJob by remember { mutableStateOf<Job?>(null) }
    val context = LocalContext.current
    val service = remember { GomokuModelService(context) }
    val cloud = remember { CloudCoachClient(context) }
    val client = remember { LocalLlmClient().apply { initialize() } }
    val scope = rememberCoroutineScope()
    var localReady by remember { mutableStateOf(service.installed()) }
    var downloading by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { client.cancel(); actionJob?.cancel(); scope.launch { client.close() } } }
    val cloudAvailable = cloud.isOnline() && cloud.isConfigured()

    fun coordinate(index: Int) = "${index / N + 1},${index % N + 1}"
    fun localMove(candidates: List<Int>): Int = when (level) {
        "初学" -> candidates.getOrElse(2) { candidates.last() }
        "高手" -> candidates.first()
        else -> candidates.getOrElse(1) { candidates.first() }
    }
    fun localReply(insight: String) = when (persona) {
        "热血对手" -> "这一步很有冲劲。$insight"
        "安静棋友" -> "我看见了这个连接点。$insight"
        else -> "这一手值得肯定。$insight"
    }
    fun refreshSuggestion(position: List<Int>) {
        if (winner != 0) { suggested = null; return }
        suggested = GomokuEngine.chooseMove(position, 1)
    }
    fun finishGame(result: Int) {
        winner = result; suggested = null
        message = when (result) {
            1 -> "漂亮的五连！这一局的关键，是你始终保住了自己的连接。"
            2 -> "这局我抓住了关键机会。想不想一起复盘，看看下一局怎样反制？"
            else -> "棋盘已满，和棋也说明你守住了局面。"
        }
    }
    fun play(index: Int) {
        if (aiThinking || winner != 0 || board[index] != 0) return
        val next = board.toMutableList(); next[index] = 1; board = next; moveCount += 1
        if (GomokuEngine.hasFive(next, index, 1)) { finishGame(1); return }
        val candidates = GomokuEngine.rankedMoves(next, 2)
        if (candidates.isEmpty()) { finishGame(3); return }
        aiThinking = true; message = if (cloudAvailable) "$persona 正在构思这一手…" else "$persona 正在思考…"
        val fact = GomokuEngine.moveInsight(next, candidates.first(), 2)
        actionJob = scope.launch {
            val cloudDecision = withContext(Dispatchers.Default) {
                if (!cloudAvailable) null else withTimeoutOrNull(3_500) {
                    runCatching { cloud.chooseOpponentMove(persona, level, candidates.map(::coordinate), fact) }.getOrNull()
                }
            }
            val ai = cloudDecision?.coordinate?.let { wanted -> candidates.firstOrNull { coordinate(it) == wanted } } ?: localMove(candidates)
            next[ai] = 2; board = next; moveCount += 1
            if (GomokuEngine.hasFive(next, ai, 2)) finishGame(2)
            else {
                refreshSuggestion(next)
                message = cloudDecision?.reply?.takeIf { it.isNotBlank() } ?: localReply(GomokuEngine.moveInsight(next, ai, 2))
            }
            aiThinking = false
        }
    }
    fun review() {
        if (reviewing) return
        val snapshot = board; val count = snapshot.count { it != 0 }
        val insight = GomokuEngine.chooseMove(snapshot, 1)?.let { GomokuEngine.moveInsight(snapshot, it, 1) } ?: "本局结束后重点回看最后几手的攻防取舍。"
        val profile = "$level·$persona"; val result = resultName(winner)
        val system = "你是温暖、专业的五子棋教练。只依据事实写三段以内复盘：亮点、一个训练点、下一局建议。不得编造坐标、胜负或棋形。"
        val prompt = "玩家档位与偏好：$profile；已落子数：$count；局面事实：$insight；赛果：$result。"
        reviewing = true; message = if (cloudAvailable) "正在生成你的专属赛后信…" else "离线棋友正在整理本局复盘…"
        actionJob = scope.launch {
            val text = withContext(Dispatchers.Default) {
                val online = if (cloudAvailable) runCatching { cloud.review(system, prompt) }.getOrNull() else null
                when {
                    online != null -> "云端复盘\n" + GameReview.finish(online, profile, count, insight, winner)
                    localReady -> runCatching {
                        if (client.state != LocalLlmClientState.READY) client.load(service.modelId, service.directory().absolutePath, service.modelLib)
                        var out = ""; client.stream(LocalLlmRequest(service.modelId, listOf(LocalLlmMessage(LocalLlmRole.SYSTEM, system), LocalLlmMessage(LocalLlmRole.USER, prompt)), maxOutputTokens = 40, temperature = .65f)).collect { if (it is LocalLlmEvent.Delta) out += it.text }
                        "离线复盘\n" + GameReview.finish(out, profile, count, insight, winner)
                    }.getOrElse { "棋局回信\n" + GameReview.fallback(profile, count, insight, winner) }
                    else -> "棋局回信\n" + GameReview.fallback(profile, count, insight, winner)
                }
            }
            message = text; reviewing = false
        }
    }
    fun restart() {
        client.cancel(); actionJob?.cancel(); board = List(N * N) { 0 }; winner = 0; suggested = null; aiThinking = false; reviewing = false; moveCount = 0
        message = "$persona 已就位。新的一局，慢慢来。"
    }

    Scaffold(containerColor = Color(0xffFBF8FF), topBar = { TopAppBar(title = { Column { Text("棋友", fontWeight = FontWeight.Bold); Text(if (cloudAvailable) "云端 AI 对局 · 离线可继续" else "本地 AI 对局 · 随时可玩", style = MaterialTheme.typography.labelSmall) } }) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xffEEE8F8)), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("$persona · $level", fontWeight = FontWeight.SemiBold); Text(if (aiThinking) "AI 正在思考" else if (winner == 0) "轮到 ${if (moveCount % 2 == 0) "你" else "AI"}" else "对局已结束", style = MaterialTheme.typography.bodySmall) }
                Text(if (cloudAvailable) "云端增强" else "离线可用", color = Plum, style = MaterialTheme.typography.labelLarge)
            } }
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f).background(Wood).pointerInput(board, aiThinking) { detectTapGestures { tap -> val cell = size.width / (N - 1); play((tap.y / cell).roundToInt().coerceIn(0, N - 1) * N + (tap.x / cell).roundToInt().coerceIn(0, N - 1)) } }) {
                val cell = size.width / (N - 1)
                for (line in 0 until N) { drawLine(Color(0xff77512A), Offset(0f, line * cell), Offset(size.width, line * cell), strokeWidth = 1.5f); drawLine(Color(0xff77512A), Offset(line * cell, 0f), Offset(line * cell, size.width), strokeWidth = 1.5f) }
                board.forEachIndexed { index, piece -> if (piece > 0) drawCircle(if (piece == 1) Ink else Color(0xffFAFAFA), cell * .38f, Offset((index % N) * cell, (index / N) * cell)) }
                suggested?.let { drawCircle(Color(0x99E85D3F), cell * .22f, Offset((it % N) * cell, (it / N) * cell)) }
            }
            Card(modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text(if (reviewing) "AI 正在写给你…" else persona, color = Plum, style = MaterialTheme.typography.labelLarge); Spacer(Modifier.height(4.dp)); Text(message) } }
            Text("选择对局风格", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("初学", "进阶", "高手").forEach { value -> OutlinedButton(onClick = { level = value }, enabled = level != value, modifier = Modifier.weight(1f)) { Text(value) } } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("耐心教练", "热血对手", "安静棋友").forEach { value -> OutlinedButton(onClick = { persona = value }, enabled = persona != value, modifier = Modifier.weight(1f)) { Text(value, maxLines = 1) } } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { Button(onClick = ::review, enabled = !reviewing, modifier = Modifier.weight(1f)) { Text("复盘") }; OutlinedButton(onClick = ::restart, modifier = Modifier.weight(1f)) { Text("新对局") } }
            if (!localReady) OutlinedButton(onClick = { scope.launch { downloading = true; runCatching { service.install {}; localReady = true }.onFailure { message = "离线棋友暂不可用，但你仍可继续与本地棋局 AI 对弈。" }; downloading = false } }, enabled = !downloading, modifier = Modifier.fillMaxWidth()) { Text(if (downloading) "准备离线棋友…" else "下载离线棋友") }
        }
    }
}

private fun resultName(winner: Int) = when (winner) { 1 -> "玩家胜"; 2 -> "AI 胜"; 3 -> "和棋"; else -> "进行中" }
