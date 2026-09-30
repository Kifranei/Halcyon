package com.ella.music.ui.poster

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class PosterWallGeometryTest {
    @Test fun remoteNavigationKeepsSelectingPhysicalPostersAcrossInfiniteCameraRebases() {
        val geometry = PosterWallGeometry(1, infinite = true)
        val state = PosterWallState().apply { resize(412f, 680f, geometry) }
        var selected = geometry.visiblePosters(state.camera.bounds(412f, 680f), overscan = 0f).first()
        repeat(40) {
            selected = state.reveal(checkNotNull(nextPosterPlacement(selected, geometry, PosterDirection.Right)), geometry)
            val shown = geometry.visiblePosters(state.camera.bounds(412f, 680f), overscan = 0f)
            assertTrue(shown.any { it.key == selected.key && it.rect == selected.rect })
            assertEquals(0, selected.index)
        }
    }
    @Test fun infiniteFieldRepeatsInEveryDirectionWithoutEmptyTailBlocks() {
        for (count in listOf(1, 13, 882, 100000)) {
            val geometry = PosterWallGeometry(count, infinite = true)
            for (offset in listOf(-12000f, -600f, 0f, 90000f)) {
                val bounds = PosterRect(offset, offset, 600f, 900f)
                val visible = geometry.visiblePosters(bounds, overscan = 0f)
                assertTrue(visible.isNotEmpty())
                assertTrue(visible.size < 100)
                assertEquals(visible.size, visible.map { it.key }.distinct().size)
                assertTrue(visible.all { it.index in 0 until count && it.rect.intersects(bounds) })
                val repeated = geometry.visiblePosters(bounds.copy(x = bounds.x + geometry.repeatWidth, y = bounds.y + geometry.repeatHeight), overscan = 0f)
                assertEquals(visible.map { it.index }, repeated.map { it.index })
            }
        }
    }

    @Test fun infiniteCameraRebasesByAWholeFieldAndStillLocatesTheOriginalQueueIndex() {
        val geometry = PosterWallGeometry(882, infinite = true)
        val original = PosterCamera(20000f, -80000f, 0.8f)
        val camera = original.clamped(geometry, 840f, 360f)
        assertTrue(camera.x <= 0f && camera.x > -geometry.repeatWidth * camera.scale)
        assertTrue(camera.y <= 0f && camera.y > -geometry.repeatHeight * camera.scale)
        val target = geometry.nearestRect(881, camera.bounds(840f, 360f))
        val located = camera.centeredOn(target, 840f, 360f).clamped(geometry, 840f, 360f)
        assertTrue(geometry.visiblePosters(located.bounds(840f, 360f), overscan = 0f).any {
            it.index == 881 && kotlin.math.abs(located.x + it.rect.centerX * located.scale - 420f) < 1f &&
                kotlin.math.abs(located.y + it.rect.centerY * located.scale - 180f) < 1f
        })
    }

    @Test fun expansionAndCollapsePreserveTheClickedPostersShapeAndCornerRadius() {
        val target = PosterRect(16f, 60f, 380f, 560f)
        for (origin in listOf(PosterRect(40f, 180f, 56f, 116f), PosterRect(80f, 240f, 116f, 56f), PosterRect(20f, 130f, 116f, 116f))) {
            assertEquals(origin, posterMorphFrame(origin, target, 5.85f, 0f).rect)
            assertEquals(target, posterMorphFrame(origin, target, 5.85f, 1f).rect)
            assertEquals(5.85f, posterMorphFrame(origin, target, 5.85f, 0f).cornerRadius, 0f)
            for (step in 0..100) {
                val frame = posterMorphFrame(origin, target, 5.85f, step / 100f)
                assertTrue("The poster must remain rectangular throughout the morph", frame.cornerRadius < minOf(frame.rect.width, frame.rect.height) * 0.15f)
            }
        }
    }

    @Test fun packedPostersNeverOverlapAndRemainInsideTheField() {
        for (count in listOf(0, 1, 2, 11, 12, 13, 24, 25, 49, 120)) {
            val geometry = PosterWallGeometry(count)
            val rects = (0 until count).map(geometry::rect)
            rects.forEachIndexed { index, rect ->
                assertTrue(rect.width > 0 && rect.height > 0)
                assertTrue(rect.right <= geometry.width && rect.bottom <= geometry.height)
                rects.drop(index + 1).forEach { assertFalse("Overlapping posters at size $count", rect.intersects(it)) }
            }
        }
    }

    @Test fun viewportCullingMatchesAnExhaustiveScanAcrossBlockBoundaries() {
        val geometry = PosterWallGeometry(500)
        for (x in listOf(-300f, 0f, 790f, 1500f, 3000f)) for (y in listOf(-200f, 0f, 520f, 1000f, 2800f)) {
            val viewport = PosterRect(x, y, 412f, 640f)
            val expected = (0 until 500).filter { geometry.rect(it).intersects(viewport) }.toSet()
            assertEquals(expected, geometry.visibleIndices(viewport, overscan = 0f).toSet())
        }
    }

    @Test fun aHundredThousandTracksStillMountOnlyNearbyPosters() {
        val geometry = PosterWallGeometry(100_000)
        val visible = geometry.visibleIndices(PosterRect(800f, 12_000f, 600f, 900f))
        assertTrue(visible.isNotEmpty())
        assertTrue("Mounted ${visible.size} posters", visible.size < 120)
        assertEquals(visible.size, visible.distinct().size)
        assertTrue(visible.all { it in 0 until 100_000 })
    }

    @Test fun zoomKeepsTheWorldPointUnderTheFingersStationary() {
        val old = PosterCamera(-120f, -240f, 0.8f)
        val updated = old.transformed(12f, -8f, 1.25f, 170f, 310f)
        assertEquals((170f - old.x) / old.scale, (182f - updated.x) / updated.scale, 0.001f)
        assertEquals((310f - old.y) / old.scale, (302f - updated.y) / updated.scale, 0.001f)
    }

    @Test fun cameraCannotGetLostBeyondTheFieldAndZoomIsBounded() {
        val geometry = PosterWallGeometry(1000)
        val camera = PosterCamera(50_000f, -50_000f).clamped(geometry, 412f, 640f)
        assertTrue(abs(camera.x) < geometry.width)
        assertTrue(camera.y >= 640f - geometry.height * camera.scale - 24f)
        assertEquals(0.45f, camera.transformed(0f, 0f, 0.001f, 0f, 0f).scale, 0f)
        assertEquals(1.5f, camera.transformed(0f, 0f, 100f, 0f, 0f).scale, 0f)
    }

    @Test fun aSingleSongIsCentredAndCurrentTrackCanBeLocatedAtTheFarEnd() {
        val small = PosterWallGeometry(1)
        val centred = PosterCamera().clamped(small, 412f, 640f)
        assertEquals(206f, centred.x + small.rect(0).centerX * centred.scale, 0.001f)
        assertEquals(320f, centred.y + small.rect(0).centerY * centred.scale, 0.001f)
        val large = PosterWallGeometry(10000)
        val target = large.rect(9999)
        val camera = PosterCamera().centeredOn(target, 412f, 640f).clamped(large, 412f, 640f)
        assertTrue(target.intersects(camera.bounds(412f, 640f)))
    }
}
