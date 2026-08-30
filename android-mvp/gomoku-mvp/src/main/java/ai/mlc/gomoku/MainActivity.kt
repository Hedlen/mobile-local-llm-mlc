package ai.mlc.gomoku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.porter.local.llm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min
import kotlin.math.roundToInt

private const val N = GomokuEngine.SIZE
private val Ink = Color(0xff25352D)
private val Jade = Color(0xff197B5B)
private val Paper = Color(0xffF7F1E6)
private val Board = Color(0xffD9A85E)
private val Accent = Color(0xffC95036)

class MainActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContent { AppEntry() }
    }
}

@Composable private fun AppEntry() {
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1150); showSplash = false }
    MaterialTheme(colorScheme = lightColorScheme(primary = Jade, secondary = Accent, background = Paper, surface = Color.White)) {
        if (showSplash) SplashScreen() else ProductRoot()
    }
}

@Composable private fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(Paper), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.gomoku_launcher_art), "弈境五子棋", Modifier.size(142.dp).clip(MaterialTheme.shapes.extraLarge), contentScale = ContentScale.Crop)
            Spacer(Modifier.height(18.dp))
            Text("弈境五子棋", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
            Text("一局一弈，恰到好处", color = Jade)
        }
    }
}

@Composable private fun ProductRoot() {
    val context = LocalContext.current
    val profile = remember { UserProfile(context) }
    var signedIn by remember { mutableStateOf(profile.isSignedIn()) }
    if (!signedIn) LoginScreen { name -> profile.signIn(name); signedIn = true } else GomokuHome(profile) { profile.signOut(); signedIn = false }
}

@Composable private fun LoginScreen(onLogin: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Paper).padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(painterResource(R.drawable.gomoku_launcher_art), null, Modifier.size(104.dp).clip(MaterialTheme.shapes.large), contentScale = ContentScale.Crop)
            Text("欢迎来到弈境", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
            Text("落子、思考、慢慢变强", color = Color.Gray)
            OutlinedTextField(name, { name = it }, label = { Text("棋手昵称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onLogin(name) }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("进入棋局") }
            Text("这是本地身份入口。账号同步与第三方登录将在服务端版本接入。", style = MaterialTheme.typography.bodySmall, color = Color.Gray, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun GomokuHome(profile: UserProfile, onSignOut: () -> Unit) {
    var board by remember { mutableStateOf(List(N * N) { 0 }) }
    var winner by remember { mutableStateOf(0) }
    var turns by remember { mutableStateOf(0) }
    var mode by remember { mutableStateOf("AI 对局") }
    var level by remember { mutableStateOf("均衡") }
    var persona by remember { mutableStateOf("耐心教练") }
    var hint by remember { mutableStateOf<Int?>(null) }
    var thinking by remember { mutableStateOf(false) }
    var reviewing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("${profile.name()}，黑棋先行。") }
    var settingsOpen by remember { mutableStateOf(false) }
    var adOpen by remember { mutableStateOf(false) }
    var interest by remember { mutableStateOf("不展示") }
    var actionJob by remember { mutableStateOf<Job?>(null) }
    val context = LocalContext.current
    val settings = remember { CloudSettings(context) }
    val cloud = remember { CloudCoachClient(context, settings) }
    val service = remember { GomokuModelService(context) }
    val client = remember { LocalLlmClient().apply { initialize() } }
    val scope = rememberCoroutineScope()
    var localReady by remember { mutableStateOf(service.installed()) }
    var downloading by remember { mutableStateOf(false) }
    var cloudConfigured by remember { mutableStateOf(settings.load() != null) }
    val cloudAvailable = cloudConfigured && cloud.isOnline()

    DisposableEffect(Unit) { onDispose { client.cancel(); actionJob?.cancel(); scope.launch { client.close() } } }
    fun coordinate(i: Int) = "${i / N + 1},${i % N + 1}"
    fun localMove(candidates: List<Int>) = when (level) { "入门" -> candidates.getOrElse(2) { candidates.last() }; "高手" -> candidates.first(); else -> candidates.getOrElse(1) { candidates.first() } }
    fun refreshHint(position: List<Int>) { hint = if (mode == "AI 对局" && winner == 0) GomokuEngine.chooseMove(position, 1) else null }
    fun finish(result: Int) { winner = result; hint = null; message = when (result) { 1 -> "五子连珠！这局下得漂亮。"; 2 -> "AI 先成五了，点“智能复盘”找回关键一手。"; else -> "棋盘已满，势均力敌。" } }
    fun restart() { client.cancel(); actionJob?.cancel(); board = List(N * N) { 0 }; winner = 0; turns = 0; thinking = false; reviewing = false; hint = null; message = if (mode == "AI 对局") "$persona 已就位，黑棋先行。" else "双人对局开始，黑棋先行。" }
    fun play(index: Int) {
        if (thinking || winner != 0 || board[index] != 0) return
        val next = board.toMutableList()
        if (mode == "双人对局") {
            val piece = if (turns % 2 == 0) 1 else 2
            next[index] = piece; board = next; turns += 1
            if (GomokuEngine.hasFive(next, index, piece)) finish(piece) else if (next.none { it == 0 }) finish(3) else message = if (piece == 1) "白棋落子" else "黑棋落子"
            return
        }
        next[index] = 1; board = next; turns += 1
        if (GomokuEngine.hasFive(next, index, 1)) { finish(1); return }
        val candidates = GomokuEngine.rankedMoves(next, 2)
        if (candidates.isEmpty()) { finish(3); return }
        val fact = GomokuEngine.moveInsight(next, candidates.first(), 2)
        thinking = true; message = if (cloudAvailable) "${persona}正在选择应手…" else "${persona}正在思考…"
        actionJob = scope.launch {
            val remote = withContext(Dispatchers.Default) { if (!cloudAvailable) null else withTimeoutOrNull(3_500) { runCatching { cloud.chooseOpponentMove(persona, level, candidates.map(::coordinate), fact) }.getOrNull() } }
            val ai = remote?.coordinate?.let { wanted -> candidates.firstOrNull { coordinate(it) == wanted } } ?: localMove(candidates)
            next[ai] = 2; board = next; turns += 1
            if (GomokuEngine.hasFive(next, ai, 2)) finish(2) else { refreshHint(next); message = remote?.reply?.takeIf { it.isNotBlank() } ?: "我守住了这一线，轮到你了。" }
            thinking = false
        }
    }
    fun review() {
        if (reviewing) return
        val snapshot = board; val count = snapshot.count { it != 0 }
        val insight = GomokuEngine.chooseMove(snapshot, 1)?.let { GomokuEngine.moveInsight(snapshot, it, 1) } ?: "关注最后几手的攻防转换。"
        val player = "${profile.name()}，$level，$persona"
        val system = "你是温暖、专业的五子棋教练。只根据给出的事实，用中文写三句以内复盘：一个亮点、一个训练点、下一局行动。不得编造坐标、胜负或棋局事实。"
        val prompt = "棋手：$player。落子数：$count。局面事实：$insight。结果：${resultName(winner)}。"
        reviewing = true; message = if (cloudAvailable) "AI 正在整理这局的关键点…" else "正在准备本地复盘…"
        actionJob = scope.launch {
            val result = withContext(Dispatchers.Default) {
                val online = if (cloudAvailable) runCatching { cloud.review(system, prompt) }.getOrNull() else null
                when {
                    online != null -> GameReview.finish(online, player, count, insight, winner)
                    localReady -> runCatching { if (client.state != LocalLlmClientState.READY) client.load(service.modelId, service.directory().absolutePath, service.modelLib); var text = ""; client.stream(LocalLlmRequest(service.modelId, listOf(LocalLlmMessage(LocalLlmRole.SYSTEM, system), LocalLlmMessage(LocalLlmRole.USER, prompt)), maxOutputTokens = 72, temperature = .65f)).collect { if (it is LocalLlmEvent.Delta) text += it.text }; GameReview.finish(text, player, count, insight, winner) }.getOrElse { GameReview.fallback(player, count, insight, winner) }
                    else -> GameReview.fallback(player, count, insight, winner)
                }
            }
            message = result; reviewing = false
        }
    }
    val ad = DemoAds.forGame(interest, turns)
    Scaffold(containerColor = Paper, topBar = {
        TopAppBar(title = { Column { Text("弈境五子棋", fontWeight = FontWeight.Bold, color = Ink); Text("你好，${profile.name()}", style = MaterialTheme.typography.labelSmall, color = Color.Gray) } }, actions = { TextButton(onClick = { settingsOpen = true }) { Text("设置") } })
    }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("AI 对局", "双人对局").forEach { item -> FilterChip(selected = mode == item, onClick = { if (mode != item) { mode = item; restart() } }, label = { Text(item) }, modifier = Modifier.weight(1f)) } }
            Card(colors = CardDefaults.cardColors(containerColor = if (mode == "AI 对局") Color(0xffE4F1EA) else Color.White)) { Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(if (mode == "AI 对局") persona else "面对面双人", fontWeight = FontWeight.SemiBold); Text(if (thinking) "正在落子" else if (mode == "AI 对局" && cloudAvailable) "云端辅助 · 本地规则校验" else "本地棋局，随时可玩", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }; Text(if (mode == "AI 对局") level else "黑白轮流", color = Jade, fontWeight = FontWeight.Bold) } }
            GomokuBoard(board, hint, thinking) { play(it) }
            Card { Column(Modifier.padding(14.dp)) { Text(if (reviewing) "智能复盘" else "对局提示", color = Jade, style = MaterialTheme.typography.labelLarge); Spacer(Modifier.height(4.dp)); Text(message) } }
            if (mode == "AI 对局") AiControls(level, persona, { level = it }, { persona = it })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = ::review, enabled = !reviewing, modifier = Modifier.weight(1f)) { Text("智能复盘") }; OutlinedButton(onClick = ::restart, modifier = Modifier.weight(1f)) { Text("重新开始") } }
            if (interest != "不展示") Card(colors = CardDefaults.cardColors(containerColor = Color(0xffFFF4DB))) { Column(Modifier.padding(13.dp)) { Text("赞助内容 · 演示", style = MaterialTheme.typography.labelSmall, color = Color.Gray); Text(ad.title, fontWeight = FontWeight.SemiBold); Text(ad.body, style = MaterialTheme.typography.bodySmall); TextButton(onClick = { adOpen = true }, contentPadding = PaddingValues(0.dp)) { Text(ad.action) } } }
        }
    }
    if (settingsOpen) SettingsDialog(settings, localReady, downloading, interest, onClose = { settingsOpen = false }, onSaved = { cloudConfigured = settings.load() != null }, onInterest = { interest = it }, onDownload = { scope.launch { downloading = true; runCatching { service.install {}; localReady = true }.onFailure { message = "离线 AI 暂时未准备好。" }; downloading = false } }, onSignOut = onSignOut)
    if (adOpen) AlertDialog(onDismissRequest = { adOpen = false }, title = { Text(ad.title) }, text = { Text("${ad.body}\n\n当前为本地模拟页：没有广告 SDK、追踪或外部跳转。") }, confirmButton = { TextButton(onClick = { adOpen = false }) { Text("知道了") } })
}

@Composable private fun GomokuBoard(board: List<Int>, hint: Int?, thinking: Boolean, onPlay: (Int) -> Unit) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium).background(Board).pointerInput(board, thinking) { detectTapGestures { point ->
        val side = min(size.width, size.height).toFloat(); val margin = side * .06f; val cell = (side - margin * 2f) / (N - 1); val row = ((point.y - margin) / cell).roundToInt().coerceIn(0, N - 1); val col = ((point.x - margin) / cell).roundToInt().coerceIn(0, N - 1); onPlay(row * N + col)
    } }) {
        val side = min(size.width, size.height); val margin = side * .06f; val cell = (side - margin * 2f) / (N - 1)
        for (line in 0 until N) { val p = margin + line * cell; drawLine(Color(0xff6D4928), Offset(margin, p), Offset(side - margin, p), 1.6f); drawLine(Color(0xff6D4928), Offset(p, margin), Offset(p, side - margin), 1.6f) }
        board.forEachIndexed { i, piece -> if (piece > 0) { val center = Offset(margin + (i % N) * cell, margin + (i / N) * cell); drawCircle(if (piece == 1) Color(0xff1D2421) else Color(0xffFFFDF7), cell * .39f, center); if (piece == 2) drawCircle(Color(0xffBEA88A), cell * .39f, center, style = Stroke(1.4f)) } }
        hint?.let { drawCircle(Accent.copy(alpha = .78f), cell * .16f, Offset(margin + (it % N) * cell, margin + (it / N) * cell)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AiControls(level: String, persona: String, onLevel: (String) -> Unit, onPersona: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("AI 对手", style = MaterialTheme.typography.labelLarge); Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf("入门", "均衡", "高手").forEach { value -> FilterChip(selected = level == value, onClick = { onLevel(value) }, label = { Text(value) }, modifier = Modifier.weight(1f)) } }; Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf("耐心教练", "热血对手", "安静棋友").forEach { value -> FilterChip(selected = persona == value, onClick = { onPersona(value) }, label = { Text(value) }, modifier = Modifier.weight(1f)) } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SettingsDialog(settings: CloudSettings, localReady: Boolean, downloading: Boolean, interest: String, onClose: () -> Unit, onSaved: () -> Unit, onInterest: (String) -> Unit, onDownload: () -> Unit, onSignOut: () -> Unit) {
    var endpoint by remember { mutableStateOf(settings.load()?.endpoint ?: CloudSettings.DEFAULT_ENDPOINT) }; var model by remember { mutableStateOf(settings.load()?.model ?: CloudSettings.DEFAULT_MODEL) }; var key by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text("设置") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("云端 AI 可选。你的密钥会由 Android Keystore 加密后仅保存在本机，请勿填写共享的生产密钥。", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(endpoint, { endpoint = it }, label = { Text("API 地址") }, singleLine = true); OutlinedTextField(model, { model = it }, label = { Text("模型名称") }, singleLine = true); OutlinedTextField(key, { key = it }, label = { Text("你的 API Key") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { if (settings.save(endpoint, model, key)) { note = "云端 AI 已保存到本机"; key = ""; onSaved() } else note = "请填写 HTTPS 地址、模型名称和密钥" }) { Text("保存") }; TextButton(onClick = { settings.clear(); note = "已移除本机云端配置"; onSaved() }) { Text("移除") } }
        if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.bodySmall, color = Jade)
        Divider(); Text("赞助内容偏好", style = MaterialTheme.typography.labelLarge); Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("不展示", "益智", "策略", "休闲").forEach { value -> OutlinedButton(onClick = { onInterest(value) }, enabled = interest != value) { Text(value) } } }
        if (!localReady) OutlinedButton(onClick = onDownload, enabled = !downloading, modifier = Modifier.fillMaxWidth()) { Text(if (downloading) "正在准备离线 AI…" else "下载离线 AI") }
        TextButton(onClick = onSignOut) { Text("退出当前本地身份", color = Accent) }
    } }, confirmButton = { TextButton(onClick = onClose) { Text("完成") } })
}

private fun resultName(winner: Int) = when (winner) { 1 -> "玩家胜"; 2 -> "AI 胜"; 3 -> "和棋"; else -> "进行中" }
