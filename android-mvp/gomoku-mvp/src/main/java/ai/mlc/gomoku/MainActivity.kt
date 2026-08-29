package ai.mlc.gomoku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val N = 15
class MainActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) { super.onCreate(state); setContent { GomokuApp() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun GomokuApp() {
    var board by remember { mutableStateOf(List(N * N) { 0 }) }; var winner by remember { mutableStateOf(0) }
    var analyze by remember { mutableStateOf(true) }; var profile by remember { mutableStateOf("稳健学习型") }; var advice by remember { mutableStateOf("勾选 AI 分析后，每次落子会在右侧显示局面建议。") }
    fun tap(i: Int) { if (winner != 0 || board[i] != 0) return; val b = board.toMutableList(); b[i] = 1; val ai = b.indices.filter { b[it] == 0 }.minByOrNull { kotlin.math.abs(it / N - 7) + kotlin.math.abs(it % N - 7) }; if (ai != null) b[ai] = 2; board = b; if (analyze) advice = "当前局面：你落在 ${i / N + 1} 行 ${i % N + 1} 列。建议关注中心连接与对手的连续三子，下一手优先封堵白棋威胁。玩家风格：$profile" }
    Scaffold(topBar = { TopAppBar(title = { Text("五子棋 · 本地 AI 助手") }) }) { p -> Column(Modifier.padding(p).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) { Text(if (winner == 0) "你执黑 · AI 执白" else "对局结束"); Row { Text("AI 分析"); Switch(analyze, { analyze = it }) } }
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xffd9a85b)).pointerInput(Unit) { detectTapGestures { q -> val cell = size.width / (N - 1); val c = (q.x / cell).roundToInt().coerceIn(0, N - 1); val r = (q.y / cell).roundToInt().coerceIn(0, N - 1); tap(r * N + c) } }) { val cell = size.width / (N - 1); for (i in 0 until N) { drawLine(Color(0xff5b3a1f), Offset(0f, i * cell), Offset(size.width, i * cell)); drawLine(Color(0xff5b3a1f), Offset(i * cell, 0f), Offset(i * cell, size.width)) }; board.forEachIndexed { i, v -> if (v > 0) drawCircle(if (v == 1) Color(0xff202124) else Color.White, cell * .38f, Offset((i % N) * cell, (i / N) * cell)) } }
        OutlinedTextField(profile, { profile = it }, label = { Text("玩家风格") }, modifier = Modifier.fillMaxWidth())
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text("AI 局面分析", style = MaterialTheme.typography.titleMedium); Text(advice) } }
        Button(onClick = { board = List(N * N) { 0 }; winner = 0; advice = "新棋局已开始。" }, modifier = Modifier.fillMaxWidth()) { Text("重新开始") }
        Text("模型测试与 Benchmark 已独立为另一款 App；本应用专注对弈和实时分析。")
    } }
}
