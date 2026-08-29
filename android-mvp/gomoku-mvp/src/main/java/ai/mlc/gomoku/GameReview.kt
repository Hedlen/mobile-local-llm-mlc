package ai.mlc.gomoku

object GameReview {
    fun fallback(profile: String, moveCount: Int, insight: String, winner: Int): String {
        val outcome = when (winner) { 1 -> "这一局你拿下了胜利。"; 2 -> "这一局对手抓住了关键机会。"; 3 -> "棋盘已满，本局和棋。"; else -> "本局仍在进行，当前局面值得继续观察。" }
        return "$outcome 已进行 $moveCount 手。$insight 作为${profile.take(8)}玩家，下一局优先在每次落子前检查对手的直接威胁。"
    }

    fun finish(raw: String, profile: String, moveCount: Int, insight: String, winner: Int): String {
        val text = raw.replace(Regex("\\s+"), " ").trim()
        val invalid = text.length !in 12..220 || text.lastOrNull() !in setOf('。', '！', '？', '!', '?') ||
            Regex("\\d{1,2}\\s*[,，]\\s*\\d{1,2}").containsMatchIn(text)
        return if (invalid) fallback(profile, moveCount, insight, winner) else text
    }
}
