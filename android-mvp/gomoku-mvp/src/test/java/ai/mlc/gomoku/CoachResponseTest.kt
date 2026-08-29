package ai.mlc.gomoku

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachResponseTest {
    @Test fun repetitiveModelOutputFallsBack() {
        val raw = "黑棋占据了优势，但白棋也有潜力。".repeat(8)
        val result = CoachResponse.finish(raw, "6,7", "稳健学习型")
        assertTrue(result.contains("推荐落点：6,7"))
        assertTrue(result.contains("关键连接点"))
        assertFalse(result.contains(raw))
    }

    @Test fun conciseUsefulOutputIsKept() {
        val result = CoachResponse.finish("这里能同时连接两侧棋子。也能压缩白棋的发展空间。稳住节奏！", "8,8", "进攻型")
        assertTrue(result.contains("连接两侧"))
        assertTrue(result.length < 150)
    }

    @Test fun inventedCoordinateFallsBack() {
        val result = CoachResponse.finish("你的策略很稳健。下一步应考虑7,8，这里更安全。", "7,7", "稳健学习型")
        assertTrue(result.contains("推荐落点：7,7"))
        assertFalse(result.contains("7,8"))
        assertTrue(result.contains("关键连接点"))
    }

    @Test fun truncatedOutputFallsBack() {
        val result = CoachResponse.finish("先限制白棋的发展。然后保持自己的连续", "7,7", "稳健学习型")
        assertTrue(result.contains("关键连接点"))
    }
}
