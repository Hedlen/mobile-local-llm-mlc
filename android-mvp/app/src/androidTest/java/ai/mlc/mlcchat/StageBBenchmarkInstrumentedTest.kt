package ai.mlc.mlcchat

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.GsonBuilder
import com.porter.local.llm.LocalLlmClient
import com.porter.local.llm.LocalLlmEvent
import com.porter.local.llm.LocalLlmFinishReason
import com.porter.local.llm.LocalLlmMessage
import com.porter.local.llm.LocalLlmRequest
import com.porter.local.llm.LocalLlmRole
import com.porter.local.llm.LocalLlmUsage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StageBBenchmarkInstrumentedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val appFiles = context.getExternalFilesDir(null)!!
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val models = listOf(
        ModelSpec(
            id = "local/qwen2.5-0.5b-instruct@1",
            modelLib = "qwen2_q4f16_1_ec234c98ba1f1f6d014a60148428520a",
            weightBytes = 277_996_288,
        ),
        ModelSpec(
            id = "local/qwen2.5-1.5b-instruct@1",
            modelLib = "qwen2_q4f16_1_2f70fbed04df977598974fec7f80e3ef",
            weightBytes = 868_547_584,
        ),
        ModelSpec(
            id = "local/qwen2.5-3b-instruct@1",
            modelLib = "qwen2_q4f16_1_871e4109ecb6b6505671c1f4689b9224",
            weightBytes = 1_736_187_904,
        ),
    )

    @Test
    fun benchmarkIsolatedRound() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val modelIndex = arguments.getString("modelIndex")?.toIntOrNull() ?: 0
        val roundIndex = arguments.getString("roundIndex")?.toIntOrNull() ?: 1
        val kind = arguments.getString("kind") ?: "stability"
        val maxTokens = arguments.getString("maxTokens")?.toIntOrNull() ?: 12
        val model = models[modelIndex]
        val directory = File(appFiles, model.id)
        val output = File(
            appFiles,
            "benchmark/isolated/${model.fileTag}-${kind}-${roundIndex.toString().padStart(2, '0')}.json",
        )
        output.parentFile!!.mkdirs()
        assertTrue("Missing ${model.id}", File(directory, "mlc-chat-config.json").isFile)

        val temperatureBefore = batteryTemperature()
        val startedAt = System.currentTimeMillis()
        val client = LocalLlmClient().apply { initialize() }
        var loadMillis: Long? = null
        var round: RoundRecord? = null
        try {
            val loadStarted = nowMillis()
            client.load(model.id, directory.absolutePath, model.modelLib)
            loadMillis = nowMillis() - loadStarted
            round = runRound(client, model.id, kind, roundIndex, maxTokens)
            assertTrue(round.finishReason.isSuccessful())
        } finally {
            client.runCatching { unload() }
            client.close()
            output.writeText(
                gson.toJson(
                    IsolatedRoundReport(
                        model = model.id,
                        kind = kind,
                        index = roundIndex,
                        startedAtEpochMillis = startedAt,
                        finishedAtEpochMillis = System.currentTimeMillis(),
                        loadMillis = loadMillis,
                        round = round,
                        totalPssKbAfter = currentPssKb(),
                        temperatureBeforeCelsius = temperatureBefore,
                        temperatureAfterCelsius = batteryTemperature(),
                    ),
                ),
            )
        }
    }

    @Test
    fun benchmarkIsolatedCancellation() = runBlocking<Unit> {
        val modelIndex = InstrumentationRegistry.getArguments().getString("modelIndex")?.toIntOrNull() ?: 2
        val model = models[modelIndex]
        val directory = File(appFiles, model.id)
        assertTrue("Missing ${model.id}", File(directory, "mlc-chat-config.json").isFile)
        val client = LocalLlmClient().apply { initialize() }
        try {
            client.load(model.id, directory.absolutePath, model.modelLib)
            runCancellation(client, model.id)
        } finally {
            client.runCatching { unload() }
            client.close()
        }
    }

    @Test
    fun benchmarkQwen05B() = benchmarkSingleModel(models[0])

    @Test
    fun benchmarkQwen15B() = benchmarkSingleModel(models[1])

    @Test
    fun benchmarkQwen3B() = benchmarkSingleModel(models[2])

    private fun benchmarkSingleModel(model: ModelSpec) = runBlocking {
        val output = File(appFiles, "benchmark/stage-b-${model.fileTag}.json")
        val report = StageBReport(
            device = DeviceRecord(
                manufacturer = android.os.Build.MANUFACTURER,
                model = android.os.Build.MODEL,
                androidApi = android.os.Build.VERSION.SDK_INT,
                abis = android.os.Build.SUPPORTED_ABIS.toList(),
            ),
            startedAtEpochMillis = System.currentTimeMillis(),
            temperatureBeforeCelsius = batteryTemperature(),
        )
        output.parentFile!!.mkdirs()
        persist(output, report)

        var client: LocalLlmClient? = null
        try {
            val directory = File(appFiles, model.id)
            assertTrue("Missing ${model.id}", File(directory, "mlc-chat-config.json").isFile)
            client = LocalLlmClient().apply { initialize() }
            val loadStarted = nowMillis()
            client.load(model.id, directory.absolutePath, model.modelLib)
            val result = ModelBenchmark(
                id = model.id,
                modelLib = model.modelLib,
                weightBytes = model.weightBytes,
                loadMillis = nowMillis() - loadStarted,
            )

            runRound(client, model.id, "warmup", 0, 16)
            client.reset()
            repeat(3) { index ->
                result.measuredRounds += runRound(client, model.id, "measured", index + 1, 64)
                client.reset()
            }
            repeat(20) { index ->
                val round = runRound(client, model.id, "stability", index + 1, 12)
                result.stabilityRounds += round
                assertTrue(round.finishReason.isSuccessful())
                client.reset()
            }
            result.cancellation = runCancellation(client, model.id)
            result.totalPssKbAfter = currentPssKb()
            result.temperatureAfterCelsius = batteryTemperature()
            report.models += result
        } finally {
            client?.runCatching { unload() }
            client?.close()
            report.finishedAtEpochMillis = System.currentTimeMillis()
            report.temperatureAfterCelsius = batteryTemperature()
            persist(output, report)
        }
    }

    private suspend fun runRound(
        client: LocalLlmClient,
        model: String,
        kind: String,
        index: Int,
        maxTokens: Int,
    ): RoundRecord {
        val started = nowMillis()
        var firstTokenMillis: Long? = null
        var text = ""
        var usage: LocalLlmUsage? = null
        var finishReason = LocalLlmFinishReason.UNKNOWN
        client.stream(request(model, maxTokens)).collect { event ->
            when (event) {
                is LocalLlmEvent.Delta -> {
                    if (firstTokenMillis == null) firstTokenMillis = nowMillis() - started
                    text += event.text
                }
                is LocalLlmEvent.Usage -> usage = event.usage
                is LocalLlmEvent.Completed -> finishReason = event.finishReason
                is LocalLlmEvent.Cancelled -> finishReason = LocalLlmFinishReason.CANCELLED
                is LocalLlmEvent.Started -> Unit
            }
        }
        assertTrue("$model produced no text", text.isNotBlank())
        return RoundRecord(
            kind = kind,
            index = index,
            firstTokenMillis = firstTokenMillis,
            totalMillis = nowMillis() - started,
            outputCharacters = text.length,
            finishReason = finishReason,
            prefillTokensPerSecond = usage?.prefillTokensPerSecond,
            decodeTokensPerSecond = usage?.decodeTokensPerSecond,
        )
    }

    private suspend fun runCancellation(client: LocalLlmClient, model: String): CancellationRecord {
        val startedSignal = CompletableDeferred<Unit>()
        var terminal = LocalLlmFinishReason.UNKNOWN
        val started = nowMillis()
        val job = kotlinx.coroutines.CoroutineScope(Dispatchers.Default).async {
            client.stream(request(model, 256)).collect { event ->
                when (event) {
                    is LocalLlmEvent.Started -> startedSignal.complete(Unit)
                    is LocalLlmEvent.Cancelled -> terminal = LocalLlmFinishReason.CANCELLED
                    is LocalLlmEvent.Completed -> terminal = event.finishReason
                    else -> Unit
                }
            }
        }
        withTimeout(30_000) { startedSignal.await() }
        delay(150)
        val cancelStarted = nowMillis()
        client.cancel()
        withTimeout(30_000) { job.await() }
        assertEquals(LocalLlmFinishReason.CANCELLED, terminal)
        client.reset()
        return CancellationRecord(
            terminal = terminal,
            cancelToTerminalMillis = nowMillis() - cancelStarted,
            requestLifetimeMillis = nowMillis() - started,
        )
    }

    private fun request(model: String, maxTokens: Int) = LocalLlmRequest(
        model = model,
        messages = listOf(
            LocalLlmMessage(LocalLlmRole.SYSTEM, "You are a concise local game companion."),
            LocalLlmMessage(LocalLlmRole.USER, "Encourage the player after a difficult round in one sentence."),
        ),
        temperature = 0.2f,
        topP = 0.9f,
        seed = 42,
        maxOutputTokens = maxTokens,
    )

    private fun batteryTemperature(): Float? {
        val intent: Intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val raw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        return if (raw == Int.MIN_VALUE) null else raw / 10f
    }

    private fun currentPssKb(): Int = Debug.MemoryInfo().also(Debug::getMemoryInfo).totalPss
    private fun persist(output: File, report: StageBReport) = output.writeText(gson.toJson(report))
    private fun nowMillis(): Long = SystemClock.elapsedRealtimeNanos() / 1_000_000
}

private fun LocalLlmFinishReason.isSuccessful() =
    this == LocalLlmFinishReason.STOP || this == LocalLlmFinishReason.LENGTH

data class ModelSpec(val id: String, val modelLib: String, val weightBytes: Long) {
    val fileTag: String get() = when {
        "0.5b" in id -> "qwen-0.5b"
        "1.5b" in id -> "qwen-1.5b"
        else -> "qwen-3b"
    }
}
data class DeviceRecord(val manufacturer: String, val model: String, val androidApi: Int, val abis: List<String>)
data class RoundRecord(
    val kind: String,
    val index: Int,
    val firstTokenMillis: Long?,
    val totalMillis: Long,
    val outputCharacters: Int,
    val finishReason: LocalLlmFinishReason,
    val prefillTokensPerSecond: Float?,
    val decodeTokensPerSecond: Float?,
)
data class CancellationRecord(
    val terminal: LocalLlmFinishReason,
    val cancelToTerminalMillis: Long,
    val requestLifetimeMillis: Long,
)
data class ModelBenchmark(
    val id: String,
    val modelLib: String,
    val weightBytes: Long,
    val loadMillis: Long,
    val measuredRounds: MutableList<RoundRecord> = mutableListOf(),
    val stabilityRounds: MutableList<RoundRecord> = mutableListOf(),
    var cancellation: CancellationRecord? = null,
    var totalPssKbAfter: Int? = null,
    var temperatureAfterCelsius: Float? = null,
)
data class SwitchRecord(val model: String, val loadMillis: Long, val finishReason: LocalLlmFinishReason)
data class IsolatedRoundReport(
    val model: String,
    val kind: String,
    val index: Int,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long,
    val loadMillis: Long?,
    val round: RoundRecord?,
    val totalPssKbAfter: Int,
    val temperatureBeforeCelsius: Float?,
    val temperatureAfterCelsius: Float?,
)
data class StageBReport(
    val schemaVersion: Int = 1,
    val device: DeviceRecord,
    val startedAtEpochMillis: Long,
    val temperatureBeforeCelsius: Float?,
    var finishedAtEpochMillis: Long? = null,
    var temperatureAfterCelsius: Float? = null,
    val models: MutableList<ModelBenchmark> = mutableListOf(),
    val switches: MutableList<SwitchRecord> = mutableListOf(),
)
