package ai.mlc.gomoku

data class DemoAd(val title: String, val body: String, val action: String)

object DemoAds {
    fun forGame(interest: String, moveCount: Int): DemoAd = when (interest) {
        "益智" -> DemoAd("五分钟残局挑战", "下一局前，来一道轻松的逻辑小题。", "查看演示")
        "策略" -> DemoAd("把下一手想得更远", "用一个短课程认识常见棋形与攻防节奏。", "查看演示")
        "休闲" -> DemoAd("换一种轻松玩法", "发现一款没有压力、随时可停的休闲小游戏。", "查看演示")
        else -> if (moveCount >= 24) DemoAd("休息一下再继续", "这局已经很投入了，下一局前试试可选的小挑战。", "查看演示")
        else DemoAd("按你的节奏来", "这里有一项为本次体验准备的本地演示内容。", "查看演示")
    }
}
