package com.lihoo.socialcrop.util

/**
 * 离线「中文主字幕 → 英文副字幕」翻译（v1.7.0）。
 *
 * 内置常用词/短语词库 + 正向最大匹配分词，纯 Kotlin、零依赖、零权限、不联网，
 * 与关于页「全部图片处理在本机完成」的隐私承诺一致。
 * 面向电影字幕这类短句；复杂长句是逐词直译，副行在参数页仍可手动修改。
 */
object SubtitleTranslator {

    private val PUNCT = mapOf(
        '，' to ",", '。' to ".", '！' to "!", '？' to "?",
        '；' to ";", '：' to ":", '、' to ",", '「' to "\"",
        '」' to "\"", '《' to "\"", '》' to "\"", '“' to "\"",
        '”' to "\"", '…' to "...", '—' to "-", '～' to "~",
        '（' to "(", '）' to ")"
    )

    /** 词库按「长词优先」匹配；空译文表示该字略去（的/了/着…） */
    private val DICT: Map<String, String> = mapOf(
        // 常用短语
        "不是" to ", not", "不会" to "won't", "不能" to "can't", "不要" to "don't",
        "没有" to "there is no", "可以" to "can", "一定" to "surely", "已经" to "already",
        "正在" to "now", "将会" to "will", "永远不会" to "will never", "永远" to "forever",
        "一起" to "together", "其实" to "actually", "因为" to "because", "所以" to "so",
        "如果" to "if", "虽然" to "although", "即使" to "even if", "只要" to "as long as",
        "只有" to "only", "无论" to "no matter", "然而" to "however", "但是" to "but",
        "而且" to "and", "还是" to "or", "就是" to "is", "都是" to "are all",
        "也是" to "is also", "属于" to "belongs to", "值得" to "worth", "从此" to "from now on",
        "终于" to "finally", "忽然" to "suddenly", "依然" to "still", "仍然" to "still",
        "也许" to "perhaps", "或许" to "maybe", "大概" to "probably", "当然" to "of course",
        "毕竟" to "after all", "一切" to "everything", "所有" to "all", "每个" to "every",
        "每次" to "every time", "某个" to "someone", "一些" to "some", "一点" to "a little",
        "一场" to "a", "一次" to "once", "一种" to "a kind of", "一个人" to "someone alone",
        "知道" to "know", "觉得" to "feel", "以为" to "thought", "记得" to "remember",
        "忘记" to "forget", "相信" to "believe", "害怕" to "fear", "喜欢" to "like",
        "讨厌" to "hate", "想念" to "miss", "拥抱" to "embrace", "离开" to "leave",
        "抵达" to "arrive", "出发" to "set out", "回来" to "come back", "回头" to "look back",
        "继续" to "go on", "放弃" to "give up", "改变" to "change", "成为" to "become",
        "拥有" to "own", "失去" to "lose", "得到" to "gain", "遇见" to "meet",
        "陪伴" to "accompany", "守护" to "guard", "发光" to "shine", "燃烧" to "burn",
        "照亮" to "light up", "奔跑" to "run", "停留" to "stay", "等待" to "wait",
        "寻找" to "seek", "看见" to "see", "听见" to "hear", "开始" to "begin",
        "结束" to "end", "落下" to "fall", "升起" to "rise", "为什么" to "why",
        "什么" to "what", "怎么" to "how", "哪里" to "where", "谁" to "who",
        "时候" to "the moment", "今天" to "today", "明天" to "tomorrow", "昨天" to "yesterday",
        "安静地" to "quietly", "慢慢地" to "slowly", "轻轻地" to "gently",
        "夜色" to "the night", "星光" to "starlight", "赶路人" to "the traveler",
        "赶路" to "hurry on", "不问" to "asks no", "回头路" to "the way back",

        // 名词 / 意象
        "人生" to "Life", "旷野" to "a wilderness", "轨道" to "a track", "生活" to "life",
        "世界" to "the world", "时间" to "time", "岁月" to "years", "未来" to "the future",
        "过去" to "the past", "现在" to "now", "此刻" to "this moment", "远方" to "the distance",
        "旅程" to "the journey", "路上" to "on the road", "答案" to "the answer",
        "问题" to "the question", "故事" to "the story", "梦想" to "a dream", "希望" to "hope",
        "自由" to "freedom", "孤独" to "solitude", "勇气" to "courage", "温柔" to "tenderness",
        "热爱" to "love", "清晨" to "the morning", "黄昏" to "the dusk", "日出" to "sunrise",
        "日落" to "sunset", "星空" to "the stars", "星辰" to "the stars", "大海" to "the sea",
        "山海" to "mountains and seas", "烟火" to "fireworks", "人间" to "the world",
        "城市" to "the city", "故乡" to "hometown", "天空" to "the sky", "月亮" to "the moon",
        "太阳" to "the sun", "风景" to "the scenery", "照片" to "the photo",
        "画面" to "the frame", "电影" to "the film", "字幕" to "subtitles", "名字" to "a name",
        "声音" to "a voice", "沉默" to "silence", "告别" to "farewell", "相遇" to "a meeting",
        "重逢" to "a reunion", "终点" to "the end", "起点" to "the start",
        "方向" to "a direction", "选择" to "a choice", "意义" to "meaning",
        "温度" to "warmth", "痕迹" to "traces", "背影" to "a fading figure",
        "少年" to "the young", "孩子" to "a child", "大人" to "grown-ups",
        "朋友" to "friends", "自己" to "yourself", "别人" to "others",
        "我们" to "we", "你们" to "you", "他们" to "they", "这里" to "here",
        "那里" to "there", "四季" to "the seasons", "季节" to "the season",
        "春天" to "spring", "夏天" to "summer", "秋天" to "autumn", "冬天" to "winter",
        "夜晚" to "the night", "白天" to "the day", "分钟" to "minutes", "小时" to "hours",

        // 单字 / 虚词
        "是" to "is", "在" to "in", "有" to "have", "走" to "walk", "跑" to "run",
        "我" to "I", "你" to "you", "他" to "he", "她" to "she", "它" to "it",
        "这" to "this", "那" to "that", "和" to "and", "与" to "and", "或" to "or",
        "但" to "but", "而" to "and", "也" to "also", "都" to "all", "就" to "then",
        "才" to "only then", "还" to "still", "又" to "again", "很" to "very",
        "最" to "most", "更" to "more", "太" to "too", "只" to "only", "被" to "by",
        "让" to "let", "给" to "to", "从" to "from", "到" to "to", "向" to "toward",
        "为" to "for", "以" to "with", "于" to "in", "之" to "of", "其" to "its",
        "此" to "this", "每" to "every", "不" to "not", "没" to "no", "别" to "don't",
        "请" to "please", "来" to "come", "去" to "go", "上" to "up", "下" to "down",
        "里" to "in", "外" to "out", "前" to "before", "后" to "after", "中" to "in",
        "间" to "between", "时" to "when", "会" to "will", "能" to "can", "要" to "want",
        "想" to "want", "说" to "say", "做" to "do", "看" to "look", "听" to "listen",
        "总" to "always", "常" to "often", "再" to "again", "已" to "already",
        "将" to "will", "正" to "just", "刚" to "just", "爱" to "love", "光" to "light",
        "夜" to "the night", "风" to "the wind", "雨" to "the rain", "雪" to "snow",
        "山" to "mountains", "海" to "the sea", "云" to "clouds", "月" to "the moon",
        "花" to "flowers", "树" to "trees", "路" to "the road", "门" to "the door",
        "窗" to "the window", "家" to "home", "街" to "the street", "梦" to "a dream",
        "美" to "beautiful", "好" to "good", "坏" to "bad", "大" to "big", "小" to "small",
        "长" to "long", "短" to "short", "远" to "far", "近" to "near", "新" to "new",
        "旧" to "old", "冷" to "cold", "暖" to "warm", "亮" to "bright", "暗" to "dark",
        "轻" to "light", "重" to "heavy", "慢" to "slow", "快" to "fast", "真" to "true",
        "假" to "false", "空" to "empty", "满" to "full", "深" to "deep", "浅" to "shallow",
        "勇敢" to "brave", "安静" to "quiet", "热闹" to "lively", "平凡" to "ordinary",
        "特别" to "special", "美好" to "beautiful", "年" to "years", "天" to "days",
        "日" to "days", "秒" to "seconds", "色" to "color", "星" to "star",
        "赶" to "hurry", "问" to "ask", "吧" to "", "吗" to "", "呢" to "",
        "啊" to "", "呀" to "", "哦" to "", "啦" to "", "嗯" to "",
        "的" to "", "了" to "", "着" to "", "过" to "", "地" to "", "得" to "",
        "个" to "", "些" to "", "种" to "", "次" to "", "们" to "", "把" to ""
    )

    private val MAX_LEN: Int = DICT.keys.maxOf { it.length }

    fun translate(input: String): String {
        val text = input.trim()
        if (text.isEmpty()) return ""
        val parts = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                PUNCT.containsKey(c) -> {
                    parts.add(PUNCT.getValue(c))
                    i++
                }
                isAscii(c) -> {
                    val start = i
                    while (i < text.length && isAscii(text[i])) i++
                    parts.add(text.substring(start, i))
                }
                else -> {
                    var hit: String? = null
                    val maxLen = minOf(MAX_LEN, text.length - i)
                    for (len in maxLen downTo 1) {
                        val v = DICT[text.substring(i, i + len)]
                        if (v != null) {
                            hit = v
                            i += len
                            break
                        }
                    }
                    if (hit == null) {
                        parts.add(c.toString())
                        i++
                    } else if (hit.isNotEmpty()) {
                        parts.add(hit)
                    }
                }
            }
        }

        val sb = StringBuilder()
        var needSpace = false
        for (p in parts) {
            val piece = p.trim()
            if (piece.isEmpty()) continue
            val startsPunct = piece[0] in ",.;:!?)]}\"'"
            if (sb.isNotEmpty() && needSpace && !startsPunct) sb.append(' ')
            sb.append(piece)
            needSpace = piece != "\"" && piece != "("
        }
        val joined = sb.toString().trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\s+([,.!?;:])")) { it.groupValues[1] }
        if (joined.isEmpty()) return ""
        return joined.replaceFirstChar { it.uppercaseChar() }
    }

    private fun isAscii(c: Char): Boolean =
        c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '\'' || c == '-'
}
