package ai.mlc.gomoku

import org.junit.Assert.*
import org.junit.Test

class GomokuEngineTest {
    @Test fun detectsFiveAndBlocksImmediateLoss() {
        val board = MutableList(GomokuEngine.SIZE * GomokuEngine.SIZE) { 0 }
        repeat(4) { board[7 * 15 + 4 + it] = 1 }
        assertEquals(7 * 15 + 3, GomokuEngine.chooseMove(board))
        board[7 * 15 + 8] = 1
        assertTrue(GomokuEngine.hasFive(board, 7 * 15 + 8, 1))
    }

    @Test fun takesWinningMoveBeforeBlocking() {
        val board = MutableList(225) { 0 }
        repeat(4) { board[5 * 15 + it] = 2; board[8 * 15 + it] = 1 }
        assertEquals(5 * 15 + 4, GomokuEngine.chooseMove(board))
    }

    @Test fun explainsImmediateThreatWithBoardFacts() {
        val board = MutableList(GomokuEngine.SIZE * GomokuEngine.SIZE) { 0 }
        repeat(4) { board[7 * 15 + it] = 2 }
        assertTrue(GomokuEngine.moveInsight(board, 7 * 15 + 4, 1).contains("防住"))
    }
}
