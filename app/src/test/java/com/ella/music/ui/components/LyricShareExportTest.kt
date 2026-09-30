package com.ella.music.ui.components
import android.app.Application
import android.graphics.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LyricShareExportTest {
 @Test fun spotifyExportUsesTheConfiguredBrandInTheRenderedFooter() {
  val original=content(LyricShareCardStyle.Spotify,3)
  val custom=original.copy(brandText="Aurora Music")
  val originalLayout=calculateLyricShareLayout(original)
  val customLayout=calculateLyricShareLayout(custom)
  assertEquals("♫ Halcyon",originalLayout.footerText)
  assertEquals("♫ Aurora Music",customLayout.footerText)
  val a=renderLyricShareCardBitmap(original,originalLayout,null)
  val b=renderLyricShareCardBitmap(custom,customLayout,null)
  try {
   val height=160
   val oldPixels=IntArray(a.width*height)
   val newPixels=IntArray(b.width*height)
   a.getPixels(oldPixels,0,a.width,0,a.height-height,a.width,height)
   b.getPixels(newPixels,0,b.width,0,b.height-height,b.width,height)
   assertFalse("The exported footer must visibly change with the custom brand",oldPixels.contentEquals(newPixels))
   assertEquals(a.getPixel(a.width/2,a.height/2),b.getPixel(b.width/2,b.height/2))
  } finally { a.recycle();b.recycle() }
 }

 private fun content(style:LyricShareCardStyle,count:Int)=LyricShareCardContent(
  title="夜明け / Blinding Lights",artist="Sample Artist",annotation="",footerText="Shared via Halcyon",
  blocks=(1..count).map { ShareLyricBlock("Verse $it · I've been on my own for long enough",listOf("第 $it 句 · 约定之处繁樱盛放")) },
  backgroundColors=listOf(Color.rgb(145,30,26),Color.rgb(60,14,26)),style=style)
 @Test fun everyStyleExportsAllSelectedLinesWithEnoughHeight() {
  for(style in LyricShareCardStyle.entries) {
   val content=content(style,24);val layout=calculateLyricShareLayout(content)
   assertEquals(style.name,24,layout.lyricBlocks.size)
   assertTrue(style.name,layout.adaptiveCanvasHeight>1920)
   val last=layout.lyricBlocks.last()
   assertEquals(content.blocks.last().primary,last.primary.layout.text.toString())
   assertEquals(content.blocks.last().secondary.single(),last.secondary.single().layout.text.toString())
   assertTrue(layout.lyricBlocks.all { it.primary.layout.getEllipsisCount(it.primary.layout.lineCount-1)==0 })
  }
 }
 @Test fun contentBuilderDoesNotCapSelectedBlocks() {
  val context=RuntimeEnvironment.getApplication()
  val lines=(1..40).map { com.ella.music.data.model.LyricLine(timeMs=it*1000L,text="Line $it") }
  val content=buildLyricShareCardContent(context,null,lines,emptyList(),"","",includeTranslation=false,includePronunciation=false)
  assertEquals(40,content.blocks.size)
 }
 @Test fun neteaseBrandIsPresentForLocalAndOnlineSongsAndSupportsCustomNames() {
  val context=RuntimeEnvironment.getApplication()
  val line=com.ella.music.data.model.LyricLine(0,"A line")
  for(source in listOf("","netease")) {
   val song=com.ella.music.data.model.Song(1,"Title","Artist","Album",1,1000,"/test.flac","test.flac",onlineSource=source)
   val content=buildLyricShareCardContent(context,song,listOf(line),emptyList(),"","",style=LyricShareCardStyle.NetEase)
   assertEquals("Halcyon",content.brandText)
   val custom=buildLyricShareCardContent(context,song,listOf(line),emptyList(),""," @My player ",style=LyricShareCardStyle.NetEase)
   assertEquals("My player",custom.brandText)
  }
 }
 @Test fun realTagStyleAndEditorialStylesRenderInspectableCards() {
  val directory=File("build/reports/lyric-card-previews").apply { mkdirs() }
  val cover=Bitmap.createBitmap(900,900,Bitmap.Config.ARGB_8888)
  val canvas=Canvas(cover);val paint=Paint().apply { shader=LinearGradient(0f,0f,900f,900f,Color.rgb(29,47,56),Color.rgb(180,116,95),Shader.TileMode.CLAMP) }
  canvas.drawRect(0f,0f,900f,900f,paint)
  paint.shader=null;paint.color=Color.rgb(232,201,140);canvas.drawCircle(460f,340f,150f,paint)
  for(style in LyricShareCardStyle.entries) {
   val content=content(style,3);val layout=calculateLyricShareLayout(content)
   if(style==LyricShareCardStyle.Historical127) {
    assertNotNull(layout.historical);assertEquals(92f,layout.safePadding,.01f);assertEquals(88f,layout.coverRect.top,.01f)
   }
   val bitmap=renderLyricShareCardBitmap(content,layout,cover)
   assertEquals(layout.adaptiveCanvasHeight,bitmap.height)
   if(style==LyricShareCardStyle.Historical127) {
    assertEquals(0,Color.alpha(bitmap.getPixel(0,0)))
    assertTrue(Color.alpha(bitmap.getPixel(bitmap.width/2,bitmap.height/2))>0)
   }
   File(directory,"${style.name}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
   bitmap.recycle()
  }
  cover.recycle()
 }
}
