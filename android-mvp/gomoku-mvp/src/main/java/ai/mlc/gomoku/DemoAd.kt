package ai.mlc.gomoku

data class DemoAd(val title: String, val body: String, val action: String)

object DemoAds {
    fun forGame(interest: String, moveCount: Int): DemoAd = when (interest) {
        "Puzzles" -> DemoAd("A five-minute puzzle break", "Try a calm logic challenge between matches.", "Explore puzzles")
        "Strategy" -> DemoAd("Sharpen your next move", "A short strategy course built around pattern recognition.", "View lesson")
        "Casual" -> DemoAd("A lighter kind of play", "Discover a relaxing, no-pressure game for a quick reset.", "Take a look")
        else -> if (moveCount >= 24) DemoAd("A calm break is ready", "Long match? Try a short, optional puzzle before your next game.", "See demo")
        else DemoAd("Play your way", "Discover a thoughtful game selected for this session.", "See demo")
    }
}
