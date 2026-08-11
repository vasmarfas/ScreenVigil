package com.vasmarfas.screenvigil

import com.vasmarfas.screenvigil.camera.FrameProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameProcessorTest {

    private val width = 176
    private val height = 144
    private val lumaSize = width * height

    private fun frame(value: Int): ByteArray = ByteArray(lumaSize * 3 / 2) { value.toByte() }

    @Test
    fun `average luma of uniform frame equals pixel value`() {
        val processor = FrameProcessor()
        val stats = processor.process(frame(90), width, height, 20)
        assertEquals(90, stats.avgLuma)
        assertFalse(stats.hadPrevious)
        assertEquals(0, stats.diffPixels)
    }

    @Test
    fun `first frame has no reference so diff is not counted`() {
        val processor = FrameProcessor()
        val first = processor.process(frame(10), width, height, 20)
        assertFalse(first.hadPrevious)
        assertEquals(0f, first.diffRatio, 0.0001f)
    }

    @Test
    fun `identical consecutive frames produce no difference`() {
        val processor = FrameProcessor()
        processor.process(frame(120), width, height, 20)
        val stats = processor.process(frame(120), width, height, 20)
        assertTrue(stats.hadPrevious)
        assertEquals(0, stats.diffPixels)
    }

    @Test
    fun `change below threshold is ignored`() {
        val processor = FrameProcessor()
        processor.process(frame(100), width, height, 20)
        val stats = processor.process(frame(115), width, height, 20)
        assertEquals(0, stats.diffPixels)
    }

    @Test
    fun `whole frame change is reported as full ratio`() {
        val processor = FrameProcessor()
        processor.process(frame(20), width, height, 20)
        val stats = processor.process(frame(200), width, height, 20)
        assertEquals(lumaSize, stats.diffPixels)
        assertEquals(1f, stats.diffRatio, 0.0001f)
    }

    @Test
    fun `partial motion yields proportional ratio`() {
        val processor = FrameProcessor()
        processor.process(frame(50), width, height, 20)
        val moved = frame(50)
        // «объект» закрывает ровно 10 % кадра
        val changed = lumaSize / 10
        for (i in 0 until changed) moved[i] = 200.toByte()
        val stats = processor.process(moved, width, height, 20)
        assertEquals(changed, stats.diffPixels)
        assertEquals(0.1f, stats.diffRatio, 0.001f)
    }

    @Test
    fun `avg luma uses only the luma plane`() {
        val processor = FrameProcessor()
        val data = ByteArray(lumaSize * 3 / 2)
        for (i in 0 until lumaSize) data[i] = 60.toByte()
        // хроматическая часть заполнена мусором и не должна влиять на результат
        for (i in lumaSize until data.size) data[i] = 255.toByte()
        val stats = processor.process(data, width, height, 20)
        assertEquals(60, stats.avgLuma)
    }

    @Test
    fun `frame smaller than declared size is rejected`() {
        val processor = FrameProcessor()
        val stats = processor.process(ByteArray(10), width, height, 20)
        assertEquals(0, stats.avgLuma)
        assertEquals(0, stats.comparedPixels)
    }

    @Test
    fun `reset drops the reference frame`() {
        val processor = FrameProcessor()
        processor.process(frame(80), width, height, 20)
        processor.reset()
        val stats = processor.process(frame(200), width, height, 20)
        assertFalse(stats.hadPrevious)
    }

    @Test
    fun `preview buffer is filled with grayscale and highlights`() {
        val processor = FrameProcessor()
        val preview = IntArray(lumaSize)
        processor.process(frame(40), width, height, 20, preview = preview)
        assertEquals(0xFF282828.toInt(), preview[0])
        processor.process(frame(200), width, height, 20, preview = preview)
        // изменившийся пиксель подсвечивается красным
        assertEquals(0xFF, (preview[0] shr 16) and 0xFF)
    }

    @Test
    fun `centre zone counts only the middle of the frame`() {
        val processor = FrameProcessor()
        processor.process(frame(100), width, height, 20, zoneFraction = 0.5f)
        // меняем только верхнюю полосу кадра - она вне центральной зоны
        val edge = frame(100)
        for (i in 0 until lumaSize / 8) edge[i] = 220.toByte()
        val stats = processor.process(edge, width, height, 20, zoneFraction = 0.5f)
        assertTrue("общая разница видна", stats.diffRatio > 0.1f)
        assertEquals("в центре изменений нет", 0f, stats.zoneDiffRatio, 0.001f)
        assertTrue("зона занимает около четверти кадра", stats.zonePixels in (lumaSize / 5)..(lumaSize / 3))
    }

    @Test
    fun `centre zone catches movement in the middle`() {
        val processor = FrameProcessor()
        processor.process(frame(100), width, height, 20, zoneFraction = 0.5f)
        val middle = frame(100)
        // прямоугольник в центре кадра
        for (y in height / 3 until height * 2 / 3) {
            for (x in width / 3 until width * 2 / 3) middle[y * width + x] = 220.toByte()
        }
        val stats = processor.process(middle, width, height, 20, zoneFraction = 0.5f)
        assertTrue("в зоне изменений больше, чем в кадре целиком", stats.zoneDiffRatio > stats.diffRatio)
    }

    @Test
    fun `zone ratio falls back to the whole frame when disabled`() {
        val processor = FrameProcessor()
        processor.process(frame(60), width, height, 20)
        val stats = processor.process(frame(200), width, height, 20)
        assertEquals(stats.diffRatio, stats.zoneDiffRatio, 0.0001f)
    }

    @Test
    fun `large frames are subsampled`() {
        val processor = FrameProcessor()
        val bigWidth = 640
        val bigHeight = 480
        val big = ByteArray(bigWidth * bigHeight * 3 / 2) { 100.toByte() }
        val stats = processor.process(big, bigWidth, bigHeight, 20)
        assertEquals(100, stats.avgLuma)
        assertTrue(processor.stride > 1)
    }
}
