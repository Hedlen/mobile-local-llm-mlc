package ai.mlc.gomoku

object CoachResponse {
    fun finish(raw: String, coordinate: String, profile: String): String {
        val cleaned = raw
            .replace(Regex("推荐落点[：:]?\\s*[^，。；;\\n]*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        val sentences = cleaned
            .split(Regex("(?<=[。！？!?])"))
            .map { it.trim() }
            .filter { it.length in 4..55 }
            .distinctBy { it.replace(Regex("[，。！？!? ]"), "") }
            .take(3)
        val mentionsCoordinate = Regex("\\d{1,2}\\s*[,，]\\s*\\d{1,2}").containsMatchIn(cleaned)
        val completeEnding = cleaned.lastOrNull() in setOf('。', '！', '？', '!', '?')
        val repeated = sentences.size < 2 || cleaned.length > 140 || mentionsCoordinate || !completeEnding || hasRepeatedPhrase(cleaned)
        val explanation = if (repeated) fallback(profile) else sentences.joinToString("")
        return "推荐落点：$coordinate（棋盘红点）\n$explanation"
    }

    private fun hasRepeatedPhrase(text: String): Boolean {
        if (text.length < 32) return false
        for (length in 12..24) {
            val seen = HashSet<String>()
            for (start in 0..text.length - length) {
                val part = text.substring(start, start + length)
                if (!seen.add(part)) return true
            }
        }
        return false
    }

    private fun fallback(profile: String): String =
        "这一步优先争夺关键连接点，同时限制白棋向中心延伸。保持你的${profile.take(8)}节奏：先看对手威胁，再形成自己的连续棋形。"
}
