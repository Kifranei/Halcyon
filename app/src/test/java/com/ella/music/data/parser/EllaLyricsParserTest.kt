package com.ella.music.data.parser

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.data.model.primaryEndMs
import org.junit.Assert.assertEquals
import org.junit.Test

class EllaLyricsParserTest {
    @Test
    fun enhancedLrcKeepsExplicitSpacesAcrossCjkAndLatinTokens() {
        val result = EllaLyricsParser.parse(
            """
            [00:12.829]<00:12.829>二<00:13.538>人を<00:14.198>近<00:15.009>付<00:15.306>け<00:15.720>る<00:16.024>よ<00:16.431> Day<00:17.144> by<00:17.857> day<00:18.591>
            """.trimIndent()
        )

        assertEquals("二人を近付けるよ Day by day", result.lyrics.single().text)
        assertEquals(
            listOf("二", "人を", "近", "付", "け", "る", "よ", " Day", " by", " day"),
            result.lyrics.single().words.map { it.text }
        )
    }

    @Test
    fun nearbyTimedCreditLinesStayIndependentInsteadOfBecomingTranslations() {
        val result = EllaLyricsParser.parse(
            """
            [00:00.000]狂风卷奔云飙 - 黄国俊/江得胜
            [00:00.210]词：徐进良
            [00:00.420]曲：郭子
            [00:07.500]狂风卷奔云飙
            """.trimIndent()
        )

        assertEquals(
            listOf("狂风卷奔云飙 - 黄国俊/江得胜", "词：徐进良", "曲：郭子", "狂风卷奔云飙"),
            result.lyrics.map { it.text }
        )
        assertEquals(listOf(null, null, null, null), result.lyrics.map { it.translation })
    }

    @Test
    fun placeholderOnlyTimedLinesAreIgnored() {
        val result = LrcParser.parse(
            """
            [00:00.539]花篝り (篝火) - 滴草由实 (しずくさ ゆみ)
            [00:00.539]//
            [00:04.097]词：滴草由実
            [00:04.097]//
            [00:05.785]曲：大野愛果
            [00:05.785]//
            """.trimIndent()
        )

        assertEquals(
            listOf("花篝り (篝火) - 滴草由实 (しずくさ ゆみ)", "词：滴草由実", "曲：大野愛果"),
            result.lyrics.map { it.text }
        )
        assertEquals(listOf(539L, 4_097L, 5_785L), result.lyrics.map { it.timeMs })
    }

    @Test
    fun translationHeaderAndPlaceholderBlockAreIgnored() {
        val result = LrcParser.parse(
            """
            [04:16.712](I need your love)
            [trans:]
            [00:00.724]//
            [00:07.960]//
            [00:10.204]//
            """.trimIndent()
        )

        assertEquals(listOf("(I need your love)"), result.lyrics.map { it.text })
        assertEquals(listOf(256_712L), result.lyrics.map { it.timeMs })
    }

    @Test
    fun untimedLinesDoNotAttachToPreviousLyricLine() {
        val result = LrcParser.parse(
            """
            [00:01.00]第一句
            [trans:]
            无时间戳翻译
            [00:03.00]第二句
            """.trimIndent()
        )

        assertEquals(listOf("第一句", "第二句"), result.lyrics.map { it.text })
        assertEquals(listOf(null, null), result.lyrics.map { it.translation })
    }

    @Test
    fun synchronizedCreditAndCopyrightLinesArePreserved() {
        val result = LrcParser.parse(
            """
            [00:01.00]QQ音乐享有本翻译作品的著作权
            [00:02.00]作词：Someone
            [00:03.00]正常歌词
            """.trimIndent()
        )

        assertEquals(
            listOf("QQ音乐享有本翻译作品的著作权", "作词：Someone", "正常歌词"),
            result.lyrics.map { it.text }
        )
        assertEquals(listOf(1_000L, 2_000L, 3_000L), result.lyrics.map { it.timeMs })
    }

    @Test
    fun sameTimestampWordLineAndBlankPronunciationAttachTranslation() {
        val result = LrcParser.parse(
            """
            [00:41.373] <00:41.373>wake <00:41.949>me <00:42.502>up <00:43.040>
            [00:41.373]
            [00:41.373]叫醒我
            """.trimIndent()
        )

        assertEquals(1, result.lyrics.size)
        assertEquals("wake me up", result.lyrics.single().text)
        assertEquals("叫醒我", result.lyrics.single().translation)
        assertEquals(listOf("wake ", "me ", "up"), result.lyrics.single().words.map { it.text })
    }

    @Test
    fun appleMusicTtmlKeepsPerKanjiFuriganaTimings() {
        val result = EllaLyricsParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" itunes:timing="Word">
              <head>
                <metadata>
                  <iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal">
                    <transliterations>
                      <transliteration xml:lang="en-Latn">
                        <text for="L8">
                          <span begin="0:18.718" end="0:19.254">かぜ</span>
                          <span begin="0:19.459" end="0:19.775">か</span>
                        </text>
                        <text for="L17">
                          <span begin="0:59.806" end="0:59.907">み</span>
                          <span begin="0:59.907" end="1:00.444">まも</span>
                        </text>
                      </transliteration>
                    </transliterations>
                  </iTunesMetadata>
                </metadata>
              </head>
              <body>
                <div>
                  <p begin="0:17.601" end="0:20.913" itunes:key="L8">
                    <span begin="0:17.601" end="0:18.114">OK!</span>
                    <span begin="0:18.718" end="0:19.254">風</span>
                    <span begin="0:19.254" end="0:19.459">が</span>
                    <span begin="0:19.459" end="0:19.775">変</span>
                    <span begin="0:19.775" end="0:20.179">わっ</span>
                    <span begin="0:20.179" end="0:20.506">て</span>
                    <span begin="0:20.506" end="0:20.913">も</span>
                  </p>
                  <p begin="0:59.292" end="1:03.362" itunes:key="L17">
                    <span begin="0:59.292" end="0:59.493">みん</span>
                    <span begin="0:59.493" end="0:59.595">な</span>
                    <span begin="0:59.595" end="0:59.806">を</span>
                    <span begin="0:59.806" end="0:59.907">見</span>
                    <span begin="0:59.907" end="1:00.444">守</span>
                    <span begin="0:59.907" end="1:00.457">っ</span>
                    <span begin="1:00.457" end="1:03.362">てくれてるよ</span>
                  </p>
                </div>
              </body>
            </tt>
            """.trimIndent()
        )

        val wind = result.lyrics.first { it.text.contains("風") }
        assertEquals(listOf("かぜ", "か"), wind.pronunciationWords.map { it.text })
        val watch = result.lyrics.first { it.text.contains("見守") }
        assertEquals(listOf("み", "まも"), watch.pronunciationWords.map { it.text })
    }

    @Test
    fun sameTimestampKanaCompanionIsPronunciationNotTranslation() {
        val result = LrcParser.parse(
            """
            [00:12.00]OK! 風が 変わっても
            [00:12.00]かぜか
            [00:12.00]OK! 就算风向改变
            """.trimIndent()
        )

        assertEquals(1, result.lyrics.size)
        assertEquals("OK! 風が 変わっても", result.lyrics.single().text)
        assertEquals("かぜか", result.lyrics.single().pronunciation)
        assertEquals("OK! 就算风向改变", result.lyrics.single().translation)
    }

    @Test
    fun sameTimestampWordLineRomanizationAndTranslationAreMerged() {
        val result = LrcParser.parse(
            """
            [00:21.853] <00:21.853>覚<00:22.261>醒 <00:22.719>READY <00:23.379>OK <00:23.935>
            [00:21.853]ka ku se i READY OK
            [00:21.853]该觉醒了 Ready，ok？
            """.trimIndent()
        )

        assertEquals(1, result.lyrics.size)
        assertEquals("覚醒 READY OK", result.lyrics.single().text)
        assertEquals("ka ku se i READY OK", result.lyrics.single().pronunciation)
        assertEquals("该觉醒了 Ready，ok？", result.lyrics.single().translation)
    }

    @Test
    fun sameTimestampJapaneseWordLineKeepsChineseAsTranslation() {
        val result = LrcParser.parse(
            """
            [00:00.698]揺[00:01.546]籃[00:02.762]の[00:03.541]う[00:04.652]た[00:05.182]を[00:05.669][00:06.452]カ[00:07.165]ナ[00:07.485]リ[00:07.972]ヤ[00:08.701]が[00:09.501]歌[00:10.604]う[00:11.132]よ[00:11.677]
            [00:00.698]树上的金丝雀 轻唱着摇篮曲[00:12.508]
            """.trimIndent()
        )

        assertEquals(1, result.lyrics.size)
        assertEquals("揺籃のうたをカナリヤが歌うよ", result.lyrics.single().text)
        assertEquals("树上的金丝雀 轻唱着摇篮曲", result.lyrics.single().translation)
        assertEquals("揺", result.lyrics.single().words.first().text)
    }

    @Test
    fun kugouKrcWordTimingAndTranslationAreParsed() {
        val result = LrcParser.parse(
            """
            [language:eyJjb250ZW50IjpbeyJ0eXBlIjoxLCJseXJpY0NvbnRlbnQiOltbIuS9oOWlveS4lueVjCJdXX1dfQ==]
            [1000,2000]<0,500,0>Hel<500,500,0>lo
            """.trimIndent()
        )

        assertEquals(1, result.lyrics.size)
        assertEquals("Hello", result.lyrics.single().text)
        assertEquals("你好世界", result.lyrics.single().translation)
        assertEquals(listOf("Hel", "lo"), result.lyrics.single().words.map { it.text })
        assertEquals(listOf(1000L, 1500L), result.lyrics.single().words.map { it.startMs })
    }

    @Test
    fun ttmlPreservesLatinWordSpacesAndDropsPlaceholders() {
        val result = LrcParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="00:00.000" end="00:04.000">
                    <span begin="00:00.000" end="00:01.000">That</span>
                    <span begin="00:01.000" end="00:02.000">we</span>
                    <span begin="00:02.000" end="00:03.000">shoot</span>
                    <span begin="00:03.000" end="00:04.000">across</span>
                    <span ttm:role="x-translation">我们划过天际</span>
                  </p>
                  <p begin="00:05.000" end="00:06.000">//</p>
                </div>
              </body>
            </tt>
            """.trimIndent()
        )

        assertEquals(1, result.lyrics.size)
        assertEquals("That we shoot across", result.lyrics.single().text)
        assertEquals("我们划过天际", result.lyrics.single().translation)
        assertEquals(listOf("That", " we", " shoot", " across"), result.lyrics.single().words.map { it.text })
    }

    @Test
    fun ttmlKeepsSplitLatinSyllablesInsideWordsJoined() {
        val result = LrcParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="00:03.470" end="00:05.920">
                    <span begin="00:03.470" end="00:03.566">I</span> <span begin="00:03.611" end="00:03.766">know</span> <span begin="00:03.766" end="00:03.912">that</span> <span begin="00:03.939" end="00:04.103">I&apos;m</span> <span begin="00:04.103" end="00:04.241">a</span> <span begin="00:04.241" end="00:04.621">hand</span><span begin="00:04.621" end="00:04.914">ful,</span> <span begin="00:04.945" end="00:05.229">ba</span><span begin="00:05.255" end="00:05.507">by,</span> <span begin="00:05.590" end="00:05.920">uh</span><span ttm:role="x-translation">我知道我是个麻烦精 宝贝 啊</span>
                  </p>
                  <p begin="00:06.097" end="00:08.515">
                    <span begin="00:06.097" end="00:06.201">I</span> <span begin="00:06.249" end="00:06.397">know</span> <span begin="00:06.434" end="00:06.557">I</span> <span begin="00:06.587" end="00:06.740">ne</span><span begin="00:06.740" end="00:06.919">ver</span> <span begin="00:06.919" end="00:07.221">think</span> <span begin="00:07.249" end="00:07.462">be</span><span begin="00:07.462" end="00:07.851">fore</span> <span begin="00:07.878" end="00:08.111">I</span> <span begin="00:08.168" end="00:08.515">jump</span><span ttm:role="x-translation">我知道我不会三思而后行</span>
                  </p>
                </div>
              </body>
            </tt>
            """.trimIndent()
        )

        assertEquals(
            listOf(
                "I know that I'm a handful, baby, uh",
                "I know I never think before I jump"
            ),
            result.lyrics.map { it.text }
        )
        assertEquals(
            listOf(
                "我知道我是个麻烦精 宝贝 啊",
                "我知道我不会三思而后行"
            ),
            result.lyrics.map { it.translation }
        )
    }

    @Test
    fun ttmlPreservesLeadingSpacesEmbeddedInTimedSpans() {
        val result = LrcParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="00:00.313" end="00:01.968">
                    <span begin="00:00.313" end="00:00.776">Runnin'</span><span begin="00:00.776" end="00:00.976"> through</span><span begin="00:00.976" end="00:01.208"> this</span><span begin="00:01.208" end="00:01.611"> strange</span><span begin="00:01.611" end="00:01.968"> life</span>
                    <span ttm:role="x-translation">在奇怪的生活里奔波</span>
                  </p>
                  <p begin="00:01.979" end="00:03.714">
                    <span begin="00:01.979" end="00:02.429">Chasin'</span><span begin="00:02.429" end="00:02.645"> all</span><span begin="00:02.645" end="00:02.893"> them</span><span begin="00:02.893" end="00:03.328"> green</span><span begin="00:03.328" end="00:03.714"> lights</span>
                    <span ttm:role="x-translation">追逐一个又一个绿灯</span>
                  </p>
                </div>
              </body>
            </tt>
            """.trimIndent()
        )

        assertEquals(
            listOf("Runnin' through this strange life", "Chasin' all them green lights"),
            result.lyrics.map { it.text }
        )
        assertEquals(
            listOf(
                listOf("Runnin'", " through", " this", " strange", " life"),
                listOf("Chasin'", " all", " them", " green", " lights")
            ),
            result.lyrics.map { line -> line.words.map { it.text } }
        )
    }

    @Test
    fun elrcAgentPrefixesAreHiddenAndKeptAsAlignment() {
        val result = LrcParser.parse(
            """
            [00:01.000]<00:01.000>v1:<00:01.100>Hello <00:01.600>again
            [00:02.000]<00:02.000>v2:<00:02.100>Answer <00:02.600>line
            """.trimIndent()
        )

        assertEquals(listOf("Hello again", "Answer line"), result.lyrics.map { it.text })
        assertEquals(listOf("v1", "v2"), result.lyrics.map { it.agent })
        assertEquals(listOf("Hello ", "again"), result.lyrics.first().words.map { it.text })
    }

    @Test
    fun elrcStandaloneSpaceTokensBecomeDisplaySpaces() {
        val result = LrcParser.parse(
            """
            [00:04.722]Love[00:05.201] [00:05.201]hits[00:05.838] [00:05.838]hard[00:06.297] [00:06.297]I[00:06.716] [00:06.716]know[00:07.511]
            """.trimIndent()
        )

        assertEquals(1, result.lyrics.size)
        assertEquals("Love hits hard I know", result.lyrics.single().text)
        assertEquals(
            listOf("Love", " hits", " hard", " I", " know"),
            result.lyrics.single().words.map { it.text }
        )
    }

    @Test
    fun duetPrimaryEndMsPreservesOverlapAcrossAgents() {
        val first = LyricLine(
            timeMs = 1_000L,
            text = "パッと花火が",
            words = listOf(LyricWord("パッと花火が", 1_000L, 2_400L)),
            agent = "v1"
        )
        val second = LyricLine(
            timeMs = 1_800L,
            text = "パッと花火が",
            words = listOf(LyricWord("パッと花火が", 1_800L, 2_800L)),
            agent = "v2"
        )

        assertEquals(2_400L, first.primaryEndMs(nextLine = second))
    }

    @Test
    fun ttmlBackgroundParenthesesAreTrimmedAndTranslationSeparated() {
        val result = LrcParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="00:01.000" end="00:03.000">
                    <span begin="00:01.000" end="00:02.000">To get respect from</span>
                    <span ttm:role="x-translation">他人的尊重</span>
                    <span ttm:role="x-bg" begin="00:02.000" end="00:03.000">
                      <span begin="00:02.000" end="00:03.000">(Baby</span>
                      <span ttm:role="x-translation">宝贝</span>
                    </span>
                  </p>
                </div>
              </body>
            </tt>
            """.trimIndent()
        )

        assertEquals(1, result.lyrics.size)
        assertEquals("To get respect from", result.lyrics[0].text)
        assertEquals("他人的尊重", result.lyrics[0].translation)
        assertEquals("Baby", result.lyrics[0].backgroundText)
        assertEquals("宝贝", result.lyrics[0].backgroundTranslation)
        assertEquals(listOf("Baby"), result.lyrics[0].backgroundWords.map { it.text })
    }

    @Test
    fun ttmlTranslationPreservesIntentionalCjkSpaces() {
        val result = LrcParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="00:01.000" end="00:03.000">
                    <span begin="00:01.000" end="00:02.000">We will overcome.</span>
                    <span begin="00:02.000" end="00:03.000"> Your salvation has begun</span>
                    <span ttm:role="x-translation">我们会征服一切 你的救赎才刚刚开始</span>
                  </p>
                </div>
              </body>
            </tt>
            """.trimIndent()
        )

        assertEquals(1, result.lyrics.size)
        assertEquals("我们会征服一切 你的救赎才刚刚开始", result.lyrics.single().translation)
    }

    @Test
    fun lrcUntimedTranslationPreservesIntentionalCjkSpaces() {
        val result = LrcParser.parse(
            """
            [00:01.000]We will overcome. Your salvation has begun
            我们会征服一切 你的救赎才刚刚开始
            """.trimIndent()
        )

        assertEquals(1, result.lyrics.size)
        assertEquals("我们会征服一切 你的救赎才刚刚开始", result.lyrics.single().translation)
    }

    @Test
    fun ellaTtmlFallbackTrimsStandaloneBackgroundParentheses() {
        val result = EllaLyricsParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="00:03.000" end="00:04.000">
                    <span ttm:role="x-bg" begin="00:03.000" end="00:04.000">
                      <span begin="00:03.000" end="00:04.000">(Yeah</span>
                    </span>
                  </p>
                </div>
              </body>
            </tt>
            """.trimIndent()
        )

        requireNotNull(result)
        assertEquals(1, result.lyrics.size)
        assertEquals("Yeah", result.lyrics[0].backgroundText)
        assertEquals(listOf("Yeah"), result.lyrics[0].backgroundWords.map { it.text })
    }
}
