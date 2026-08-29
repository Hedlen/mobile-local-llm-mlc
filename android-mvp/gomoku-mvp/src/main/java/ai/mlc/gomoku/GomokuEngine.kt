package ai.mlc.gomoku

object GomokuEngine {
    const val SIZE = 15
    fun hasFive(board: List<Int>, index: Int, player: Int): Boolean {
        val row = index / SIZE; val col = index % SIZE
        return listOf(1 to 0, 0 to 1, 1 to 1, 1 to -1).any { (dr, dc) ->
            1 + count(board, row, col, dr, dc, player) + count(board, row, col, -dr, -dc, player) >= 5
        }
    }
    fun chooseMove(board: List<Int>, player: Int = 2): Int? {
        val opponent = if (player == 1) 2 else 1
        val empty = board.indices.filter { board[it] == 0 }
        empty.firstOrNull { wins(board, it, player) }?.let { return it }
        empty.firstOrNull { wins(board, it, opponent) }?.let { return it }
        return empty.maxByOrNull { score(board, it, player) + score(board, it, opponent) * 0.9 - distance(it) * .04 }
    }
    private fun wins(board: List<Int>, index: Int, player: Int): Boolean = board.toMutableList().also { it[index] = player }.let { hasFive(it, index, player) }
    private fun score(board: List<Int>, index: Int, player: Int): Double {
        val row = index / SIZE; val col = index % SIZE
        return listOf(1 to 0, 0 to 1, 1 to 1, 1 to -1).sumOf { (dr, dc) ->
            val run = count(board, row, col, dr, dc, player) + count(board, row, col, -dr, -dc, player)
            when (run) { 4 -> 10000.0; 3 -> 1000.0; 2 -> 100.0; 1 -> 12.0; else -> 1.0 }
        }
    }
    private fun distance(index: Int) = kotlin.math.abs(index / SIZE - 7) + kotlin.math.abs(index % SIZE - 7)
    private fun count(board: List<Int>, r0: Int, c0: Int, dr: Int, dc: Int, player: Int): Int {
        var r = r0 + dr; var c = c0 + dc; var result = 0
        while (r in 0 until SIZE && c in 0 until SIZE && board[r * SIZE + c] == player) { result++; r += dr; c += dc }
        return result
    }
}
