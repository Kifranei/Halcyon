package com.ella.music.data.netease

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteaseCommentsTest {
    // Trimmed from a live /eapi/v2/resource/comments response (sortType 3, R_SO_4_2637558926).
    private val latestPage = """
        {"code":200,"data":{"totalCount":78836,"hasMore":true,"cursor":"1790513190125","sortType":3,"comments":[
        {"commentId":9652402274,"threadId":"R_SO_4_2637558926","content":"apt","time":1790677910081,"timeStr":"12分钟前",
         "likedCount":0,"replyCount":0,"parentCommentId":0,"liked":false,
         "user":{"userId":1580430951,"nickname":"帅比诚-","avatarUrl":"http://p4.music.126.net/NdSDg9VWdf03Vt9luevt1w==/109951163526768977.jpg",
          "vipType":11,"avatarDetail":null,"vipRights":{"associator":{"vipCode":100,"rights":true,
          "iconUrl":"https://p6.music.126.net/obj/wonDlsKUwrLClGjCm8Kx/32582188099/3419/2b65/d241/bd664461c263a2dfdbf631bb9848ee3e.png"},
          "musicPackage":null,"redVipLevel":3}},
         "ipLocation":{"ip":null,"location":"河北","userId":1580430951},"beReplied":null,
         "showFloorComment":{"replyCount":0,"comments":null,"showReplyCount":false}},
        {"commentId":9652187341,"threadId":"R_SO_4_2637558926","content":"APT什么意思 没人找得到我","time":1790634188529,"timeStr":"06:23",
         "likedCount":0,"replyCount":1,"parentCommentId":0,"liked":false,
         "user":{"userId":261887623,"nickname":"喵喵喵喵23333","avatarUrl":"http://p4.music.126.net/fx83g2L7Z9qhBBG_WZ9rog==/109951173485213085.jpg",
          "vipType":11,"avatarDetail":null,"vipRights":null},
         "ipLocation":{"ip":null,"location":"美国","userId":261887623},"beReplied":null,
         "showFloorComment":{"replyCount":1,"comments":null,"showReplyCount":true}}]}}
    """.trimIndent()

    // Trimmed from a live /eapi/resource/comment/floor/get response (parent 4956438, R_SO_4_186016).
    private val floorPage = """
        {"code":200,"data":{"ownerComment":null,"comments":[
        {"commentId":1436921389,"threadId":null,"content":"我","time":1554031500549,"timeStr":"2019-03-31","likedCount":3094,
         "replyCount":null,"parentCommentId":4956438,"liked":false,
         "user":{"userId":392768483,"nickname":"湘江去北","avatarUrl":"http://p3.music.126.net/pzPlxcVhZ5UEXZwLMMqhIg==/109951172579119347.jpg","vipRights":null},
         "ipLocation":{"ip":null,"location":"","userId":null},
         "beReplied":[{"user":{"userId":548345,"nickname":"蛋蛋是圆D"},"beRepliedCommentId":4956438,
          "content":"高一听的，那时候遇到了孩儿他妈，然后就这么幸福下来了"}],"showFloorComment":null},
        {"commentId":1520681006,"threadId":null,"content":"一定非常幸福","time":1560355051380,"timeStr":"2019-06-12","likedCount":583,
         "replyCount":null,"parentCommentId":4956438,"liked":false,
         "user":{"userId":1301675937,"nickname":"有没有好好睡觉呀","avatarUrl":"http://p3.music.126.net/z2-s1r1IMn632d5sHHjx4A==/109951164643043470.jpg",
          "vipRights":{"associator":null,"musicPackage":null,"redVipLevel":0}},
         "ipLocation":{"ip":null,"location":"","userId":null},
         "beReplied":[{"user":{"userId":343495147,"nickname":"秋刀鱼014"},"beRepliedCommentId":1508268434,
          "content":"五年了。兄弟还好吗？"}],"showFloorComment":null}],
        "hasMore":true,"totalCount":14,"time":1560355051380}}
    """.trimIndent()

    @Test fun parsesCommentPageAndPagingState() {
        val page = parseNeteaseCommentPage(JSONObject(latestPage), requestedSort = 3, pageNo = 1)
        assertEquals(78836L, page.totalCount)
        assertTrue(page.hasMore)
        assertEquals("1790513190125", page.cursor)
        assertEquals(3, page.sortType)
        assertEquals(2, page.comments.size)
        val first = page.comments[0]
        assertEquals(9652402274L, first.id)
        assertEquals("apt", first.content)
        assertEquals(1790677910081L, first.timeMs)
        assertEquals("12分钟前", first.timeStr)
        assertEquals("河北", first.ipLocation)
        assertEquals("帅比诚-", first.user.nickname)
        assertTrue(first.user.avatarUrl.startsWith("https://p4.music.126.net/"))
        assertTrue(first.user.vipIconUrl.endsWith(".png"))
        assertEquals(3, first.user.vipLevel)
        assertEquals(0, first.replyCount)
        assertNull(first.replyTo)
        val second = page.comments[1]
        assertEquals(1, second.replyCount)
        assertEquals("", second.user.vipIconUrl)
        assertEquals("美国", second.ipLocation)
    }

    @Test fun serverReportedSortTypeWinsForNextPage() {
        // Anonymous Recommend requests are served as Hot; page 2 must be asked as Hot.
        val root = JSONObject("""{"code":200,"data":{"totalCount":5,"hasMore":true,"cursor":"normalHot#20","sortType":2,"comments":[{"commentId":1,"content":"x"}]}}""")
        val page = parseNeteaseCommentPage(root, requestedSort = NeteaseCommentSort.Recommend.apiValue, pageNo = 1)
        assertEquals(NeteaseCommentSort.Hot.apiValue, page.sortType)
        assertEquals("normalHot#20", page.cursor)
    }

    @Test fun emptyPageStopsPaging() {
        val page = parseNeteaseCommentPage(JSONObject("""{"code":200,"data":{"totalCount":0,"hasMore":true,"comments":[]}}"""), 2, 1)
        assertFalse(page.hasMore)
        assertTrue(page.comments.isEmpty())
        assertEquals("", page.cursor)
    }

    @Test fun floorRepliesHideOwnerTargetButKeepNestedTarget() {
        val floor = parseNeteaseFloorPage(JSONObject(floorPage))
        assertEquals(14, floor.totalCount)
        assertTrue(floor.hasMore)
        assertEquals(1560355051380L, floor.nextTime)
        assertEquals(2, floor.replies.size)
        assertNull(floor.replies[0].replyTo)
        assertEquals("", floor.replies[0].ipLocation)
        assertEquals(3094L, floor.replies[0].likedCount)
        val nested = floor.replies[1].replyTo!!
        assertEquals(1508268434L, nested.commentId)
        assertEquals("秋刀鱼014", nested.nickname)
        assertEquals("五年了。兄弟还好吗？", nested.content)
        assertEquals(0, floor.replies[1].user.vipLevel)
    }

    @Test fun threadIdAndSortValues() {
        assertEquals("R_SO_4_186016", neteaseSongThreadId("186016"))
        assertEquals(listOf(1, 2, 3), NeteaseCommentSort.entries.map { it.apiValue })
        assertEquals(NeteaseCommentSort.Latest, NeteaseCommentSort.fromApiValue(3))
    }

    @Test fun parsesLikedFlag() {
        val root = JSONObject("""{"code":200,"data":{"comments":[
            {"commentId":1,"content":"a","likedCount":7,"liked":true},
            {"commentId":2,"content":"b","likedCount":0,"liked":false},
            {"commentId":3,"content":"c"}]}}""")
        val page = parseNeteaseCommentPage(root, requestedSort = 2, pageNo = 1)
        assertEquals(listOf(true, false, false), page.comments.map { it.liked })
        assertFalse(parseNeteaseCommentPage(JSONObject(latestPage), 3, 1).comments.any { it.liked })
    }

    @Test fun optimisticLikeMovesCountAndNeverGoesNegative() {
        val comment = parseNeteaseComment(JSONObject("""{"commentId":5,"content":"x","likedCount":0,"liked":false}"""))!!
        val liked = comment.withLiked(true)
        assertTrue(liked.liked)
        assertEquals(1L, liked.likedCount)
        assertEquals(comment, liked.withLiked(false))
        assertEquals(liked, liked.withLiked(true))
        assertEquals(0L, comment.copy(liked = true).withLiked(false).likedCount)
    }

    // Shape of /api/resource/comments/add (NeteaseCloudMusicApi docs): the new comment under `comment`.
    @Test fun parsesCreatedTopLevelComment() {
        val root = JSONObject("""{"code":200,"comment":{"user":{"userId":32953014,"nickname":"me",
            "avatarUrl":"http://p1.music.126.net/a.jpg","vipRights":null,"vipType":0},
            "content":"test","status":0,"commentId":1535550516319,"time":1535550516319,
            "richContent":null,"beReplied":null,"expressionUrl":null}}""")
        val created = parseNeteaseCreatedComment(root)!!
        assertEquals(1535550516319L, created.id)
        assertEquals("test", created.content)
        assertEquals("me", created.user.nickname)
        assertTrue(created.user.avatarUrl.startsWith("https://"))
        assertEquals(0L, created.likedCount)
        assertEquals(0L, created.parentCommentId)
        assertFalse(created.liked)
        assertNull(created.replyTo)
    }

    @Test fun createdReplyHidesFloorOwnerPrefixButKeepsNestedTarget() {
        val direct = JSONObject("""{"code":200,"comment":{"commentId":900,"content":"hi","time":1,
            "user":{"userId":1,"nickname":"me"},
            "beReplied":[{"user":{"userId":2,"nickname":"owner"},"beRepliedCommentId":4956438,"content":"floor"}]}}""")
        val reply = parseNeteaseCreatedComment(direct, parentCommentId = 4956438L)!!
        assertEquals(4956438L, reply.parentCommentId)
        assertNull(reply.replyTo)
        // Filling in parentCommentId must not mutate the response object.
        assertFalse(direct.getJSONObject("comment").has("parentCommentId"))

        val nested = JSONObject("""{"code":200,"data":{"comment":{"commentId":901,"content":"hey",
            "user":{"userId":1,"nickname":"me"},
            "beReplied":[{"user":{"userId":3,"nickname":"other"},"beRepliedCommentId":1508268434,"content":"q"}]}}}""")
        val nestedReply = parseNeteaseCreatedComment(nested, parentCommentId = 4956438L)!!
        assertEquals("other", nestedReply.replyTo?.nickname)
        assertEquals(1508268434L, nestedReply.replyTo?.commentId)
    }

    @Test fun createdCommentMissingYieldsNull() {
        assertNull(parseNeteaseCreatedComment(JSONObject("""{"code":200}""")))
        assertNull(parseNeteaseCreatedComment(JSONObject("""{"code":200,"comment":{"content":"no id"}}""")))
    }

    @Test fun commentLengthCountsCodePointsAndClipsSafely() {
        val emoji = String(Character.toChars(0x1F600))
        assertEquals(2, neteaseCommentLength(emoji + "a"))
        val clipped = clipNeteaseComment(emoji.repeat(NETEASE_COMMENT_MAX_LENGTH + 5))
        assertEquals(NETEASE_COMMENT_MAX_LENGTH, neteaseCommentLength(clipped))
        assertEquals(NETEASE_COMMENT_MAX_LENGTH * 2, clipped.length)
        assertEquals("short", clipNeteaseComment("short"))
    }
}
