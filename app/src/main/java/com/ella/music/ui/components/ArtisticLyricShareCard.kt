package com.ella.music.ui.components

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.ceil

/** Independently drawn editorial templates; preview and export share the exact same geometry. */
internal data class ArtisticShareCardLayout(
    val background: Int, val foreground: Int, val accent: Int,
    val metadataLeft: Float, val bodyTop: Float,
    val photo: RectF, val photoRadius: Float, val centered: Boolean
)

private fun cardText(text: String, size: Float, width: Int, color: Int, font: Typeface?,
                     bold: Boolean = false, center: Boolean = false, mono: Boolean = false): StaticLayout {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color
        typeface = Typeface.create(if (mono) Typeface.MONOSPACE else font ?: Typeface.DEFAULT,
            if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
        .setIncludePad(false).setAlignment(if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(size * .1f, 1f).build()
}

internal fun calculateArtisticShareCard(content: LyricShareCardContent, width: Int, font: Typeface?): LyricShareCardLayout {
    val s = width / 1080f
    val style = content.style
    val dark = style in listOf(LyricShareCardStyle.Spotify, LyricShareCardStyle.Cinematic,
        LyricShareCardStyle.Vinyl, LyricShareCardStyle.Cyberpunk, LyricShareCardStyle.CD)
    val bg = when (style) {
        LyricShareCardStyle.Spotify -> content.backgroundColors.firstOrNull()?.let { c ->
            Color.rgb((Color.red(c)*.55f).toInt(), (Color.green(c)*.55f).toInt(), (Color.blue(c)*.55f).toInt())
        } ?: Color.rgb(125,26,24)
        LyricShareCardStyle.Cinematic -> Color.rgb(16,16,18)
        LyricShareCardStyle.Vinyl, LyricShareCardStyle.CD -> Color.rgb(24,25,29)
        LyricShareCardStyle.Cyberpunk -> Color.rgb(15,13,37)
        LyricShareCardStyle.Calligraphy, LyricShareCardStyle.AncientBook -> Color.rgb(244,237,220)
        LyricShareCardStyle.Polaroid -> Color.rgb(228,223,213)
        LyricShareCardStyle.StickyNote -> Color.rgb(255,235,149)
        else -> Color.rgb(249,248,244)
    }
    val fg = if (dark) Color.WHITE else Color.rgb(26,28,31)
    val accent = when (style) {
        LyricShareCardStyle.Cyberpunk -> Color.rgb(65,255,222)
        LyricShareCardStyle.Swiss, LyricShareCardStyle.Calligraphy, LyricShareCardStyle.AncientBook -> Color.rgb(174,42,37)
        else -> if (dark) Color.rgb(205,205,205) else Color.rgb(104,105,100)
    }
    val pad = (if (style == LyricShareCardStyle.Spotify) 50f else 80f)*s
    val centered = style in listOf(LyricShareCardStyle.Cinematic, LyricShareCardStyle.Polaroid,
        LyricShareCardStyle.Vinyl, LyricShareCardStyle.CD)
    val photo = when (style) {
        LyricShareCardStyle.Magazine -> RectF(pad,230*s,width-pad,610*s)
        LyricShareCardStyle.Cinematic -> RectF(0f,0f,width.toFloat(),480*s)
        LyricShareCardStyle.Polaroid -> RectF(135*s,85*s,width-135*s,895*s)
        LyricShareCardStyle.Vinyl, LyricShareCardStyle.CD -> RectF(230*s,85*s,width-230*s,705*s)
        LyricShareCardStyle.Calligraphy, LyricShareCardStyle.AncientBook, LyricShareCardStyle.Receipt,
        LyricShareCardStyle.Journal, LyricShareCardStyle.Minimal, LyricShareCardStyle.Swiss,
        LyricShareCardStyle.StickyNote, LyricShareCardStyle.Ticket -> RectF()
        else -> RectF(pad,50*s,pad+104*s,154*s)
    }
    val metaLeft = if (style == LyricShareCardStyle.Polaroid) 135*s else if (!centered && !photo.isEmpty && style != LyricShareCardStyle.Magazine) photo.right+40*s else pad
    val metaWidth = if (style == LyricShareCardStyle.Polaroid) width-270*s else if (centered || photo.isEmpty || style == LyricShareCardStyle.Magazine) width-pad*2 else width-metaLeft-pad
    val titleSize = when (style) {
        LyricShareCardStyle.Swiss -> 68f
        LyricShareCardStyle.Calligraphy, LyricShareCardStyle.AncientBook -> 52f
        else -> 36f
    }*s
    val title = cardText(content.title, titleSize, metaWidth.toInt(), fg, font, true, centered)
    val artist = cardText(listOf(content.artist, content.annotation).filter { it.isNotBlank() }.joinToString(" · "),
        28*s,metaWidth.toInt(),accent,font,center=centered)
    val titleTop = when(style) {
        LyricShareCardStyle.Magazine -> 132*s
        LyricShareCardStyle.Cinematic -> 540*s
        LyricShareCardStyle.Polaroid -> 945*s
        LyricShareCardStyle.Vinyl, LyricShareCardStyle.CD -> 760*s
        LyricShareCardStyle.Receipt -> 120*s
        LyricShareCardStyle.Ticket -> 140*s
        LyricShareCardStyle.Swiss -> 150*s
        else -> 60*s
    }
    val artistTop = titleTop + title.height + 12*s
    if (style == LyricShareCardStyle.Magazine) photo.offset(0f, maxOf(0f, artistTop+artist.height+45*s-photo.top))
    val bodyTop = if(style == LyricShareCardStyle.Magazine) photo.bottom+60*s
        else artistTop+artist.height+(if (style==LyricShareCardStyle.Spotify) 95f else 65f)*s
    val primarySize = when(style) {
        LyricShareCardStyle.Spotify -> 70f
        LyricShareCardStyle.Magazine -> 62f
        LyricShareCardStyle.Cinematic -> 54f
        LyricShareCardStyle.Calligraphy, LyricShareCardStyle.AncientBook -> 58f
        LyricShareCardStyle.Receipt, LyricShareCardStyle.Journal -> 44f
        else -> 56f
    }*s
    val mono = style == LyricShareCardStyle.Receipt || style==LyricShareCardStyle.Ticket
    val chosenFont = if (style in listOf(LyricShareCardStyle.Calligraphy,LyricShareCardStyle.AncientBook) && font==null) Typeface.SERIF else font
    val blocks = content.blocks.map { block ->
        MeasuredShareLyricBlock(
            MeasuredTextBlock(cardText(block.primary,primarySize,(width-pad*2).toInt(),fg,chosenFont,
                bold=style !in listOf(LyricShareCardStyle.Calligraphy,LyricShareCardStyle.Journal,LyricShareCardStyle.Receipt),center=centered,mono=mono),14*s),
            block.secondary.map { MeasuredTextBlock(cardText(it,primarySize*.48f,(width-pad*2).toInt(),accent,chosenFont,center=centered,mono=mono),8*s) },
            24*s
        )
    }
    val bodyHeight = blocks.sumOf { (it.primary.layout.height+it.primary.gapAfter+it.secondary.sumOf { t -> (t.layout.height+t.gapAfter).toDouble() }+it.gapAfter).toDouble() }.toFloat()
    val minimum = when(style) { LyricShareCardStyle.Spotify -> width; LyricShareCardStyle.Polaroid -> (1550*s).toInt(); else -> (900*s).toInt() }
    val h = maxOf(minimum,ceil(bodyTop+bodyHeight+150*s).toInt())
    val footer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color=accent;textSize=(if(style==LyricShareCardStyle.Spotify)38f else 24f)*s
        typeface=chosenFont ?: Typeface.DEFAULT
    }
    val footerText = if(style==LyricShareCardStyle.Spotify) {
        android.text.TextUtils.ellipsize("♫ ${content.brandText}", footer,
            (width-pad*2).coerceAtLeast(1f), android.text.TextUtils.TruncateAt.END).toString()
    } else content.footerText
    return LyricShareCardLayout(width,h,if(style==LyricShareCardStyle.Spotify)60*s else 0f,14*s,
        pad,bodyTop,h-100*s,100*s,photo,title,artist,titleTop,artistTop,blocks,footer,footerText,h-65*s,style,
        artistic=ArtisticShareCardLayout(bg,fg,accent,metaLeft,bodyTop,photo,14*s,centered))
}

internal fun renderArtisticShareCard(content: LyricShareCardContent, layout: LyricShareCardLayout,
                                    art: ArtisticShareCardLayout, cover: Bitmap?): Bitmap {
    val bitmap=Bitmap.createBitmap(layout.canvasWidth,layout.adaptiveCanvasHeight,Bitmap.Config.ARGB_8888)
    val c=Canvas(bitmap);val w=layout.canvasWidth.toFloat();val h=bitmap.height.toFloat();val s=w/1080f
    val p=Paint(Paint.ANTI_ALIAS_FLAG);val style=content.style;val pad=layout.safePadding
    if(layout.cardRadius>0) c.clipPath(Path().apply { addRoundRect(RectF(0f,0f,w,h),layout.cardRadius,layout.cardRadius,Path.Direction.CW) })
    c.drawColor(art.background)
    fun line(x1:Float,y1:Float,x2:Float,y2:Float,color:Int=art.accent,stroke:Float=2*s) {
        p.shader=null;p.color=color;p.strokeWidth=stroke;c.drawLine(x1,y1,x2,y2,p)
    }
    fun text(value:String,x:Float,y:Float,size:Int,color:Int=art.accent) {
        p.shader=null;p.style=Paint.Style.FILL;p.color=color;p.textSize=size*s;p.typeface=layout.titleLayout.paint.typeface;c.drawText(value,x,y,p)
    }
    when(style) {
        LyricShareCardStyle.Spotify -> {
            p.shader=LinearGradient(0f,0f,w,h,art.background,Color.rgb((Color.red(art.background)*1.15f).toInt().coerceAtMost(255),(Color.green(art.background)*1.15f).toInt().coerceAtMost(255),(Color.blue(art.background)*1.15f).toInt().coerceAtMost(255)),Shader.TileMode.CLAMP)
            c.drawRect(0f,0f,w,h,p);p.shader=null
        }
        LyricShareCardStyle.Magazine -> { text("LYRICS",pad,100*s,92,art.foreground);line(pad,art.photo.top-18*s,w-pad,art.photo.top-18*s,art.foreground,3*s) }
        LyricShareCardStyle.Swiss -> {
            p.color=art.accent;c.drawRect(0f,0f,24*s,h,p);text("01 / HALCYON",pad,100*s,28,art.accent)
            line(pad,art.bodyTop-28*s,w-pad,art.bodyTop-28*s,art.foreground,5*s)
        }
        LyricShareCardStyle.Calligraphy -> {
            p.color=Color.argb(18,20,25,20);c.drawOval(RectF(w*.60f,200*s,w*1.10f,900*s),p)
            p.color=art.accent;c.drawRect(w-154*s,h-124*s,w-86*s,h-56*s,p);text("音",w-142*s,h-73*s,42,Color.WHITE)
        }
        LyricShareCardStyle.AncientBook -> {
            p.style=Paint.Style.STROKE;p.strokeWidth=3*s;p.color=art.accent
            c.drawRect(28*s,28*s,w-28*s,h-28*s,p);c.drawRect(39*s,39*s,w-39*s,h-39*s,p);p.style=Paint.Style.FILL
            line(pad,art.bodyTop-30*s,w-pad,art.bodyTop-30*s)
        }
        LyricShareCardStyle.Polaroid -> {
            p.color=Color.WHITE;p.setShadowLayer(20*s,0f,10*s,Color.argb(60,0,0,0))
            c.drawRect(100*s,50*s,w-100*s,layout.artistTop+layout.artistLayout.height+40*s,p);p.clearShadowLayer()
            p.color=Color.argb(90,221,198,144);c.drawRect(w*.36f,30*s,w*.64f,100*s,p)
        }
        LyricShareCardStyle.Receipt -> {
            text("HALCYON / LYRICS",pad,78*s,28);line(pad,art.bodyTop-30*s,w-pad,art.bodyTop-30*s)
            for(i in 0..35) { p.color=art.background;c.drawCircle(i*w/35f,0f,12*s,p);c.drawCircle(i*w/35f,h,12*s,p) }
            for(i in 0..85) { val x=pad+i*8*s; if(x<w-pad) line(x,h-135*s,x,h-100*s,art.foreground,(if(i%3==0)4f else 2f)*s) }
        }
        LyricShareCardStyle.Journal -> {
            line(58*s,0f,58*s,h,Color.rgb(202,110,104))
            var y=art.bodyTop+52*s;while(y<h-120*s) { line(pad,y,w-pad,y,Color.argb(40,75,120,180));y+=62*s }
        }
        LyricShareCardStyle.StickyNote -> {
            p.color=Color.argb(80,247,247,237);c.drawRect(w*.34f,0f,w*.66f,38*s,p)
            p.color=Color.rgb(226,201,108);c.drawPath(Path().apply { moveTo(w-65*s,h);lineTo(w,h-65*s);lineTo(w,h);close() },p)
        }
        LyricShareCardStyle.Ticket -> {
            text("ADMIT ONE  /  LYRICS",pad,82*s,26)
            p.pathEffect=DashPathEffect(floatArrayOf(12*s,12*s),0f);line(pad,art.bodyTop-30*s,w-pad,art.bodyTop-30*s);p.pathEffect=null
            p.color=Color.GRAY;c.drawCircle(0f,art.bodyTop-30*s,22*s,p);c.drawCircle(w,art.bodyTop-30*s,22*s,p)
        }
        LyricShareCardStyle.Cyberpunk -> {
            line(24*s,26*s,w-24*s,26*s,art.accent,5*s);line(24*s,26*s,24*s,h-26*s,Color.MAGENTA,3*s)
            text("NOW PLAYING //",pad,h-112*s,24,art.accent)
        }
        else -> Unit
    }
    if(style==LyricShareCardStyle.Vinyl || style==LyricShareCardStyle.CD) {
        val r=art.photo.width()/2;val cx=art.photo.centerX();val cy=art.photo.centerY()
        p.shader=if(style==LyricShareCardStyle.CD) SweepGradient(cx,cy,intArrayOf(Color.LTGRAY,Color.CYAN,Color.LTGRAY,Color.MAGENTA,Color.WHITE,Color.LTGRAY),null) else null
        p.color=Color.rgb(11,12,14);c.drawCircle(cx,cy,r,p);p.shader=null;p.style=Paint.Style.STROKE;p.color=Color.rgb(65,65,68);p.strokeWidth=s
        for(i in 1..15) c.drawCircle(cx,cy,r-i*12*s,p);p.style=Paint.Style.FILL
        if(cover!=null) { val save=c.save();val label=RectF(cx-r*.40f,cy-r*.40f,cx+r*.40f,cy+r*.40f);c.clipPath(Path().apply { addOval(label,Path.Direction.CW) });artPhoto(c,cover,label);c.restoreToCount(save) }
        p.color=art.background;c.drawCircle(cx,cy,12*s,p)
    } else if(cover!=null && !art.photo.isEmpty) {
        val save=c.save();c.clipPath(Path().apply { addRoundRect(art.photo,art.photoRadius,art.photoRadius,Path.Direction.CW) });artPhoto(c,cover,art.photo);c.restoreToCount(save)
    }
    fun draw(layoutText:StaticLayout,x:Float,y:Float) { val save=c.save();c.translate(x,y);layoutText.draw(c);c.restoreToCount(save) }
    draw(layout.titleLayout,art.metadataLeft,layout.titleTop);draw(layout.artistLayout,art.metadataLeft,layout.artistTop)
    var y=art.bodyTop
    layout.lyricBlocks.forEach { b ->
        draw(b.primary.layout,pad,y);y+=b.primary.layout.height+b.primary.gapAfter
        b.secondary.forEach { t -> draw(t.layout,pad,y);y+=t.layout.height+t.gapAfter };y+=b.gapAfter
    }
    if(style==LyricShareCardStyle.Minimal) line(pad,h-120*s,w-pad,h-120*s)
    val footer=layout.footerText
    val footerPaint=TextPaint(layout.footerPaint).apply { textSize=if(style==LyricShareCardStyle.Spotify)38*s else 24*s }
    c.drawText(footer,pad,layout.footerBaseline,footerPaint)
    return bitmap
}

private fun artPhoto(c:Canvas,bitmap:Bitmap,dst:RectF) {
    val scale=maxOf(dst.width()/bitmap.width,dst.height()/bitmap.height)
    val sw=dst.width()/scale;val sh=dst.height()/scale
    val src=Rect(((bitmap.width-sw)/2).toInt(),((bitmap.height-sh)/2).toInt(),((bitmap.width+sw)/2).toInt(),((bitmap.height+sh)/2).toInt())
    c.drawBitmap(bitmap,src,dst,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
}
