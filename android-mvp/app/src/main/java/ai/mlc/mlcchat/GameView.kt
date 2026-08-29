package ai.mlc.mlcchat

import android.app.Activity
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

private const val N = 15

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameView(navController: NavController, appViewModel: AppViewModel, activity: Activity) {
    var board by remember { mutableStateOf(List(N * N) { 0 }) }
    var winner by remember { mutableStateOf(0) }
    var profile by remember { mutableStateOf("稳健学习型") }
    val moves = remember { mutableStateListOf<String>() }
    val model = appViewModel.modelList.firstOrNull { it.modelInitState.value == ModelInitState.Finished }
    val chat = appViewModel.chatState
    fun play(index: Int) {
        if (winner != 0 || board[index] != 0) return
        val next = board.toMutableList(); next[index] = 1; moves += "你:${index / N + 1},${index % N + 1}"
        if (hasFive(next, index, 1)) { board = next; winner = 1; return }
        val ai = next.indices.filter { next[it] == 0 }.minByOrNull { kotlin.math.abs(it / N - 7) + kotlin.math.abs(it % N - 7) }
        if (ai != null) { next[ai] = 2; moves += "AI:${ai / N + 1},${ai % N + 1}"; if (hasFive(next, ai, 2)) winner = 2 } else winner = 3
        board = next
    }
    Scaffold(topBar = { TopAppBar(title = { Text("五子棋 · 本地 AI 助手") }) }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(if (winner == 0) "你执黑先行  ·  AI 执白" else when (winner) { 1 -> "你赢了！"; 2 -> "AI 获胜，再来一局？"; else -> "和棋" }, style = MaterialTheme.typography.titleMedium) }
            item { GomokuBoard(board, Modifier.fillMaxWidth().aspectRatio(1f), ::play); TextButton(onClick = { board = List(N * N) { 0 }; moves.clear(); winner = 0 }) { Text("重新开始") } }
            item { OutlinedTextField(profile, { profile = it }, label = { Text("玩家风格/训练目标") }, modifier = Modifier.fillMaxWidth()) }
            item { Text(if (model == null) "未启用本地模型：请返回首页下载模型" else "模型档位：${model.modelConfig.modelId}（自动分级/降级）"); if (model != null && chat.modelName.value != model.modelConfig.modelId) Button({ model.startChat() }) { Text("启用本地模型") } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(enabled = moves.isNotEmpty() && chat.chatable(), onClick = { chat.requestGenerate("你是五子棋引擎教练。必须针对当前局面给出具体落点，不要泛泛而谈。棋盘坐标为行1-15、列1-15；黑=你，白=AI。当前棋谱：${moves.joinToString(",")}。请严格输出：推荐落点=行,列；落子颜色；直接威胁/防守理由（不超过30字）；一句鼓励。", activity) }) { Text("分析下一步") }; Button(enabled = moves.isNotEmpty() && chat.chatable(), onClick = { chat.requestGenerate("根据五子棋当前棋谱‘${moves.joinToString(",")}’和玩家画像‘$profile’，写 80 字以内的具体复盘故事，指出一个关键落子坐标，并给两条可执行训练建议。", activity) }) { Text("复盘讲故事") } } }
            item { val latest = chat.messages.lastOrNull { it.role == MessageRole.Assistant }?.text.orEmpty(); if (latest.isNotBlank() || chat.report.value.isNotBlank()) Card { Column(Modifier.padding(12.dp)) { Text("本地模型反馈", style = MaterialTheme.typography.titleMedium); Text(latest); Text(chat.report.value, style = MaterialTheme.typography.labelSmall) } } }
        }
    }
}

@Composable
private fun GomokuBoard(board: List<Int>, modifier: Modifier, onTap: (Int) -> Unit) {
    Canvas(modifier.background(Color(0xffd9a85b)).pointerInput(Unit) { detectTapGestures { p -> val cell = size.width / (N - 1); val c = (p.x / cell).roundToInt().coerceIn(0, N - 1); val r = (p.y / cell).roundToInt().coerceIn(0, N - 1); onTap(r * N + c) } }) {
        val cell = size.width / (N - 1)
        for (i in 0 until N) { drawLine(Color(0xff5b3a1f), Offset(0f, i * cell), Offset(size.width, i * cell), 1f); drawLine(Color(0xff5b3a1f), Offset(i * cell, 0f), Offset(i * cell, size.width), 1f) }
        board.forEachIndexed { i, v -> if (v != 0) { val p = Offset((i % N) * cell, (i / N) * cell); drawCircle(if (v == 1) Color(0xff202124) else Color(0xfff5f5f5), cell * .38f, p); if (v == 2) drawCircle(Color(0xff888888), cell * .38f, p, style = androidx.compose.ui.graphics.drawscope.Stroke(1f)) } }
    }
}

private fun hasFive(board: List<Int>, index: Int, player: Int): Boolean { val r = index / N; val c = index % N; return listOf(1 to 0, 0 to 1, 1 to 1, 1 to -1).any { (dr, dc) -> 1 + count(board, r, c, dr, dc, player) + count(board, r, c, -dr, -dc, player) >= 5 } }
private fun count(b: List<Int>, r0: Int, c0: Int, dr: Int, dc: Int, p: Int): Int { var r = r0 + dr; var c = c0 + dc; var n = 0; while (r in 0 until N && c in 0 until N && b[r * N + c] == p) { n++; r += dr; c += dc }; return n }
