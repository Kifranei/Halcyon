package com.ella.music.ui.folder

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderBreadcrumbTest {
    @Test
    fun breadcrumbsSplitTheHierarchyAndKeepClickablePaths() {
        val crumbs = "/storage/emulated/0/Music/待整理".folderBreadcrumbs("根目录")
        assertEquals(listOf("storage", "emulated", "0", "Music", "待整理"), crumbs.map { it.label })
        assertEquals("/storage/emulated/0/Music", crumbs[3].path)
        assertEquals("/", "/".folderBreadcrumbs("根目录").single().path)
    }

    @Test
    fun visitingAParentKeepsTheDeeperTrail() {
        val deep = "/storage/emulated/0/Music/待整理/日本动画/CLANNAD 歌曲 精选"
        val parent = "/storage/emulated/0/Music/待整理/日本动画"
        assertEquals(deep, folderBreadcrumbDisplayPath(parent, deep))
        assertEquals(
            "/storage/emulated/0/Music/待整理/日本动画/CLANNAD 歌曲 精选/extra",
            folderBreadcrumbDisplayPath("/storage/emulated/0/Music/待整理/日本动画/CLANNAD 歌曲 精选/extra", deep)
        )
        assertEquals("/storage/emulated/0/Download", folderBreadcrumbDisplayPath("/storage/emulated/0/Download", deep))
    }
}
