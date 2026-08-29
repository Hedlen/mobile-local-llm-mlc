package ai.mlc.gomoku

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameReviewTest {
    @Test fun coordinateHallucinationUsesFactBasedFallback() {
        val result = GameReview.finish("下一步下在7,8就能获胜。", "稳健型", 18, "这里必须防住对手的直接五连。", 0)
        assertFalse(result.contains("7,8"))
        assertTrue(result.contains("直接五连"))
    }

    @Test fun conciseCompletedReviewIsKept() {
        val result = GameReview.finish("你在中盘保持了有效连接。下一局优先检查对手的直接威胁。", "学习型", 20, "连接", 0)
        assertTrue(result.contains("中盘"))
    }
}
