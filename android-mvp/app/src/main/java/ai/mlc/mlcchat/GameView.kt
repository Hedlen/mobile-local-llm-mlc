package ai.mlc.mlcchat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController

/** Minimal chess-like board used to validate the local LLM integration.
 * Moves are intentionally lightweight; the model receives the move list and profile
 * and returns coaching/story text. A real chess rules engine can replace this later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameView(navController: NavController, appViewModel: AppViewModel, activity: Activity) {
    var selected by remember { mutableStateOf<Int?>(null) }
    var profile by remember { mutableStateOf("稳健学习型") }
    val moves = remember { mutableStateListOf<String>() }
    val model = appViewModel.modelList.firstOrNull { it.modelInitState.value == ModelInitState.Finished }
    val chat = appViewModel.chatState

    Scaffold(topBar = { TopAppBar(title = { Text("本地棋局助手") }) }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("设备分级", style = MaterialTheme.typography.titleMedium)
                Text(if (model == null) "未加载模型：请返回首页下载/启用模型" else "已选择：${model.modelConfig.modelId}（自动分级/降级）")
                if (model != null && chat.modelName.value != model.modelConfig.modelId) {
                    Button(onClick = { model.startChat() }) { Text("启用本地模型") }
                }
            }
            item {
                Text("玩家画像", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = profile, onValueChange = { profile = it }, label = { Text("风格/目标") }, modifier = Modifier.fillMaxWidth())
            }
            item {
                Board(selected = selected, onCell = { cell ->
                    if (selected == null) selected = cell else {
                        moves += "${selected!! + 1}→${cell + 1}"
                        selected = null
                    }
                })
            }
            item {
                Text("走法：${if (moves.isEmpty()) "尚未落子" else moves.joinToString("  ")}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = moves.isNotEmpty() && chat.chatable(), onClick = {
                        chat.requestGenerate("你是棋类教练。玩家画像：$profile。走法：${moves.joinToString(",")}。给出下一步建议，简洁说明理由，并提供一句情绪鼓励。", activity)
                    }) { Text("分析下一步") }
                    Button(enabled = moves.isNotEmpty() && chat.chatable(), onClick = {
                        chat.requestGenerate("请根据玩家画像‘$profile’和走法‘${moves.joinToString(",")}’，写一段 80 字以内的对局复盘故事，并给出两条提升技术的训练建议。", activity)
                    }) { Text("复盘讲故事") }
                }
            }
            item { Text("提示：棋盘规则为 MVP 占位，重点验证端侧模型的建议、复盘和个性化表达链路。") }
        }
    }
}

@Composable
private fun Board(selected: Int?, onCell: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().aspectRatio(1f)) {
        repeat(8) { row ->
            Row(Modifier.weight(1f)) {
                repeat(8) { col ->
                    val cell = row * 8 + col
                    val dark = (row + col) % 2 == 1
                    Text(if (cell == selected) "●" else "", modifier = Modifier.weight(1f).aspectRatio(1f).background(if (dark) Color(0xffb58863) else Color(0xfff0d9b5)).clickable { onCell(cell) }.padding(8.dp), color = Color.Black)
                }
            }
        }
    }
}
