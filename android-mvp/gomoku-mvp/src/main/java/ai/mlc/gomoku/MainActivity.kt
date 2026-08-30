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
private val Navy = Color(0xff111827)
private val Surface = Color(0xffF7F7FB)
private val Gold = Color(0xffE1B45A)
private val Violet = Color(0xff6956C9)

class MainActivity : ComponentActivity() { override fun onCreate(state: Bundle?) { super.onCreate(state); setContent { GomokuApp() } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun GomokuApp() {
    var board by remember { mutableStateOf(List(N * N) { 0 }) }; var winner by remember { mutableStateOf(0) }; var turns by remember { mutableStateOf(0) }
    var gameMode by remember { mutableStateOf("AI Match") }; var level by remember { mutableStateOf("Balanced") }; var persona by remember { mutableStateOf("Calm Coach") }
    var suggested by remember { mutableStateOf<Int?>(null) }; var thinking by remember { mutableStateOf(false) }; var reviewing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Welcome. Make the first move when you are ready.") }; var settingsOpen by remember { mutableStateOf(false) }; var adOpen by remember { mutableStateOf(false) }
    var interest by remember { mutableStateOf("None") }; var actionJob by remember { mutableStateOf<Job?>(null) }
    val context = LocalContext.current; val settings = remember { CloudSettings(context) }; val cloud = remember { CloudCoachClient(context, settings) }; val service = remember { GomokuModelService(context) }
    val client = remember { LocalLlmClient().apply { initialize() } }; val scope = rememberCoroutineScope(); var localReady by remember { mutableStateOf(service.installed()) }; var downloading by remember { mutableStateOf(false) }
    var cloudConfigured by remember { mutableStateOf(settings.load() != null) }
    val cloudAvailable = cloudConfigured && cloud.isOnline()
    DisposableEffect(Unit) { onDispose { client.cancel(); actionJob?.cancel(); scope.launch { client.close() } } }

    fun coordinate(i: Int) = "${i / N + 1},${i % N + 1}"
    fun localMove(candidates: List<Int>) = when (level) { "Starter" -> candidates.getOrElse(2) { candidates.last() }; "Expert" -> candidates.first(); else -> candidates.getOrElse(1) { candidates.first() } }
    fun refreshHint(position: List<Int>) { suggested = if (gameMode == "AI Match" && winner == 0) GomokuEngine.chooseMove(position, 1) else null }
    fun finish(result: Int) { winner = result; suggested = null; message = when (result) { 1 -> "That was a beautiful five. You kept your shape under pressure."; 2 -> "Good match. I found a late opening—let's review it together."; else -> "A draw. You both held the board with care." } }
    fun restart() { client.cancel(); actionJob?.cancel(); board = List(N * N) { 0 }; winner = 0; turns = 0; thinking = false; reviewing = false; suggested = null; message = if (gameMode == "AI Match") "$persona is ready. Your move." else "Local board ready. Black moves first." }
    fun play(index: Int) {
        if (thinking || winner != 0 || board[index] != 0) return
        val next = board.toMutableList()
        if (gameMode == "Local 2 Players") {
            val piece = if (turns % 2 == 0) 1 else 2; next[index] = piece; board = next; turns += 1
            if (GomokuEngine.hasFive(next, index, piece)) finish(piece) else if (next.none { it == 0 }) finish(3) else message = if (piece == 1) "White to move." else "Black to move."
            return
        }
        next[index] = 1; board = next; turns += 1
        if (GomokuEngine.hasFive(next, index, 1)) { finish(1); return }
        val candidates = GomokuEngine.rankedMoves(next, 2); if (candidates.isEmpty()) { finish(3); return }
        val fact = GomokuEngine.moveInsight(next, candidates.first(), 2); thinking = true; message = if (cloudAvailable) "$persona is choosing a reply…" else "$persona is thinking…"
        actionJob = scope.launch {
            val remote = withContext(Dispatchers.Default) { if (!cloudAvailable) null else withTimeoutOrNull(3_500) { runCatching { cloud.chooseOpponentMove(persona, level, candidates.map(::coordinate), fact) }.getOrNull() } }
            val ai = remote?.coordinate?.let { wanted -> candidates.firstOrNull { coordinate(it) == wanted } } ?: localMove(candidates)
            next[ai] = 2; board = next; turns += 1
            if (GomokuEngine.hasFive(next, ai, 2)) finish(2) else { refreshHint(next); message = remote?.reply?.takeIf { it.isNotBlank() } ?: "I chose a line that keeps the pressure honest." }
            thinking = false
        }
    }
    fun review() {
        if (reviewing) return
        val snapshot = board; val count = snapshot.count { it != 0 }; val insight = GomokuEngine.chooseMove(snapshot, 1)?.let { GomokuEngine.moveInsight(snapshot, it, 1) } ?: "Review the last few attacking and defensive choices."
        val profile = "$level · $persona"; val system = "You are a warm Gomoku coach. Write at most three short English paragraphs: one genuine highlight, one training point, and one next-match action. Use only supplied facts. Never invent coordinates or results."
        val prompt = "Player profile: $profile. Moves: $count. Board fact: $insight. Result: ${resultName(winner)}."
        reviewing = true; message = if (cloudAvailable) "Writing your personal match note…" else "Preparing an offline match note…"
        actionJob = scope.launch {
            val result = withContext(Dispatchers.Default) {
                val online = if (cloudAvailable) runCatching { cloud.review(system, prompt) }.getOrNull() else null
                when { online != null -> GameReview.finish(online, profile, count, insight, winner); localReady -> runCatching { if (client.state != LocalLlmClientState.READY) client.load(service.modelId, service.directory().absolutePath, service.modelLib); var text = ""; client.stream(LocalLlmRequest(service.modelId, listOf(LocalLlmMessage(LocalLlmRole.SYSTEM, system), LocalLlmMessage(LocalLlmRole.USER, prompt)), maxOutputTokens = 40, temperature = .65f)).collect { if (it is LocalLlmEvent.Delta) text += it.text }; GameReview.finish(text, profile, count, insight, winner) }.getOrElse { GameReview.fallback(profile, count, insight, winner) }; else -> GameReview.fallback(profile, count, insight, winner) }
            }
            message = result; reviewing = false
        }
    }
    val ad = DemoAds.forGame(interest, turns)

    Scaffold(containerColor = Surface, topBar = { TopAppBar(title = { Column { Text("Gomoku", fontWeight = FontWeight.Bold, color = Navy); Text(if (gameMode == "AI Match") "Play with an AI opponent" else "Pass-and-play for two", style = MaterialTheme.typography.labelSmall) } }, actions = { TextButton(onClick = { settingsOpen = true }) { Text("Settings") } }) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { listOf("AI Match", "Local 2 Players").forEach { mode -> OutlinedButton(onClick = { gameMode = mode; restart() }, enabled = gameMode != mode, modifier = Modifier.weight(1f)) { Text(if (mode == "AI Match") "Play AI" else "2 Players") } } }
            if (gameMode == "AI Match") Card(colors = CardDefaults.cardColors(containerColor = Color(0xffECE9FF))) { Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text(persona, fontWeight = FontWeight.SemiBold); Text(if (thinking) "Thinking…" else if (cloudAvailable) "Cloud-assisted move selection" else "Private offline move selection", style = MaterialTheme.typography.bodySmall) }; Text(level, color = Violet, fontWeight = FontWeight.Bold) } }
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f).background(Gold).pointerInput(board, thinking, gameMode) { detectTapGestures { point -> val cell = size.width / (N - 1); val row = (point.y / cell).roundToInt().coerceIn(0, N - 1); val col = (point.x / cell).roundToInt().coerceIn(0, N - 1); play(row * N + col) } }) { val cell = size.width / (N - 1); for (line in 0 until N) { drawLine(Color(0xff6B4D2E), Offset(0f, line * cell), Offset(size.width, line * cell), 1.5f); drawLine(Color(0xff6B4D2E), Offset(line * cell, 0f), Offset(line * cell, size.width), 1.5f) }; board.forEachIndexed { i, piece -> if (piece > 0) drawCircle(if (piece == 1) Navy else Color(0xffFFFDF7), cell * .38f, Offset((i % N) * cell, (i / N) * cell)) }; suggested?.let { drawCircle(Color(0x99D84C3B), cell * .22f, Offset((it % N) * cell, (it / N) * cell)) } }
            Card { Column(Modifier.padding(14.dp)) { Text(if (reviewing) "Match note" else if (gameMode == "AI Match") persona else "Board", color = Violet, style = MaterialTheme.typography.labelLarge); Spacer(Modifier.height(4.dp)); Text(message) } }
            if (gameMode == "AI Match") { Text("Match style", style = MaterialTheme.typography.labelLarge); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Starter", "Balanced", "Expert").forEach { value -> OutlinedButton(onClick = { level = value }, enabled = level != value, modifier = Modifier.weight(1f)) { Text(value, maxLines = 1) } } }; Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Calm Coach", "Bold Rival", "Quiet Friend").forEach { value -> OutlinedButton(onClick = { persona = value }, enabled = persona != value, modifier = Modifier.weight(1f)) { Text(value, maxLines = 1) } } } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { Button(onClick = ::review, enabled = !reviewing, modifier = Modifier.weight(1f)) { Text("Review") }; OutlinedButton(onClick = ::restart, modifier = Modifier.weight(1f)) { Text("New match") } }
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xffF0F1F7))) { Column(Modifier.padding(14.dp)) { Text("Sponsored · Demo", style = MaterialTheme.typography.labelSmall, color = Color.Gray); Spacer(Modifier.height(4.dp)); Text(ad.title, fontWeight = FontWeight.SemiBold); Text(ad.body, style = MaterialTheme.typography.bodySmall); TextButton(onClick = { adOpen = true }, contentPadding = PaddingValues(0.dp)) { Text(ad.action) } } }
        }
    }
    if (settingsOpen) SettingsDialog(settings, localReady, downloading, interest, onClose = { settingsOpen = false }, onSaved = { cloudConfigured = settings.load() != null }, onInterest = { interest = it }, onDownload = { scope.launch { downloading = true; runCatching { service.install {}; localReady = true }.onFailure { message = "Offline coach could not be prepared yet." }; downloading = false } })
    if (adOpen) AlertDialog(onDismissRequest = { adOpen = false }, title = { Text(ad.title) }, text = { Text("Demo sponsored page\n\n${ad.body}\n\nNo ad network, tracking, or external link is used in this build.") }, confirmButton = { TextButton(onClick = { adOpen = false }) { Text("Close") } })
}

@Composable private fun SettingsDialog(settings: CloudSettings, localReady: Boolean, downloading: Boolean, interest: String, onClose: () -> Unit, onSaved: () -> Unit, onInterest: (String) -> Unit, onDownload: () -> Unit) {
    var endpoint by remember { mutableStateOf(settings.load()?.endpoint ?: CloudSettings.DEFAULT_ENDPOINT) }; var model by remember { mutableStateOf(settings.load()?.model ?: CloudSettings.DEFAULT_MODEL) }; var key by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text("Settings") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Cloud AI is optional. Your key is encrypted on this device with Android Keystore. Do not enter a shared production key.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(endpoint, { endpoint = it }, label = { Text("API endpoint") }, singleLine = true); OutlinedTextField(model, { model = it }, label = { Text("Model") }, singleLine = true); OutlinedTextField(key, { key = it }, label = { Text("Your API key") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { if (settings.save(endpoint, model, key)) { note = "Cloud AI saved for this device."; key = ""; onSaved() } else note = "Enter an HTTPS endpoint, model, and key." }) { Text("Save") }; TextButton(onClick = { settings.clear(); note = "Cloud AI removed from this device."; onSaved() }) { Text("Remove") } }
        if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.bodySmall)
        Divider(); Text("Sponsored demo preference", style = MaterialTheme.typography.labelLarge); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("None", "Puzzles", "Strategy", "Casual").forEach { value -> OutlinedButton(onClick = { onInterest(value) }, enabled = interest != value) { Text(value) } } }
        if (!localReady) OutlinedButton(onClick = onDownload, enabled = !downloading, modifier = Modifier.fillMaxWidth()) { Text(if (downloading) "Preparing offline coach…" else "Enable offline coach") }
    } }, confirmButton = { TextButton(onClick = onClose) { Text("Done") } })
}

private fun resultName(winner: Int) = when (winner) { 1 -> "player win"; 2 -> "AI win"; 3 -> "draw"; else -> "in progress" }
