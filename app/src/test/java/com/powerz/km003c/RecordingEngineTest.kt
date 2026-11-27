package com.powerz.km003c

import com.powerz.km003c.model.RecordingConfig
import com.powerz.km003c.model.RecordingState
import com.powerz.km003c.model.TelemetrySample
import com.powerz.km003c.recording.RecordingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingEngineTest {

    private fun sampleAt(tsMs: Long, v: Double, i: Double): TelemetrySample =
        TelemetrySample(
            timestampMs = tsMs,
            vbusV = v,
            ibusA = i,
            powerW = v * kotlin.math.abs(i)
        )

    @Test
    fun testCustomMeasurementIntervalThrottling() {
        val engine = RecordingEngine()
        val t0 = 1_000_000L
        engine.start(RecordingConfig(intervalMs = 500L), nowMs = t0)

        // Feed samples every 100ms for 1.5 seconds (16 samples total)
        // Expected recorded samples at t=0, 500, 1000, 1500 -> 4 samples
        var appendedCount = 0
        for (k in 0..15) {
            val out = engine.onSample(sampleAt(t0 + k * 100L, 9.0, 2.0))
            if (out.appendedSample != null) appendedCount++
        }
        assertEquals(4, appendedCount)
        assertEquals(4, engine.currentSamples().size)
    }

    @Test
    fun testAutoStartCurrentTrigger() {
        val engine = RecordingEngine()
        val t0 = 2_000_000L
        val st = engine.start(
            RecordingConfig(
                intervalMs = 100L,
                autoStartCurrentEnabled = true,
                autoStartCurrentA = 0.50
            ),
            nowMs = t0
        )
        assertEquals(RecordingState.ARMED_WAITING_TRIGGER, st.state)

        // Below threshold -> stays armed, no recorded samples
        val r1 = engine.onSample(sampleAt(t0 + 100L, 5.0, 0.10))
        assertEquals(RecordingState.ARMED_WAITING_TRIGGER, r1.status.state)
        assertNull(r1.appendedSample)

        // Reaches threshold -> transitions to RECORDING and appends first sample
        val r2 = engine.onSample(sampleAt(t0 + 200L, 9.0, 1.20))
        assertEquals(RecordingState.RECORDING, r2.status.state)
        assertNotNull(r2.appendedSample)
    }

    @Test
    fun testLowCurrentSustainedAutoStopAndResetOnSpike() {
        val engine = RecordingEngine()
        val t0 = 3_000_000L
        engine.start(
            RecordingConfig(
                intervalMs = 1000L,
                stopByLowCurrentEnabled = true,
                stopLowCurrentA = 0.05,
                stopLowCurrentHoldSec = 5
            ),
            nowMs = t0
        )

        // Normal load
        engine.onSample(sampleAt(t0, 9.0, 2.0))
        // Low current for 3 seconds (not enough yet)
        engine.onSample(sampleAt(t0 + 1000L, 9.0, 0.02))
        val mid = engine.onSample(sampleAt(t0 + 4000L, 9.0, 0.02))
        assertEquals(RecordingState.RECORDING, mid.status.state)
        assertNull(mid.completedSession)
        assertTrue(mid.status.holdProgress > 0.5f)

        // Current spikes back up -> hold timer must reset!
        val spike = engine.onSample(sampleAt(t0 + 5000L, 9.0, 0.50))
        assertEquals(RecordingState.RECORDING, spike.status.state)
        assertEquals(0f, spike.status.holdProgress, 0.001f)

        // Now sustain low current for full 5 seconds -> auto-stops!
        engine.onSample(sampleAt(t0 + 6000L, 9.0, 0.03))
        val done = engine.onSample(sampleAt(t0 + 11000L, 9.0, 0.03))
        assertEquals(RecordingState.COMPLETED, done.status.state)
        assertNotNull(done.completedSession)
        assertTrue(done.completedSession!!.summary.stopReason.contains("저전류 종료"))
    }

    @Test
    fun testDurationAndCapacityAutoStop() {
        // Duration limit test
        val durEngine = RecordingEngine()
        val t0 = 4_000_000L
        durEngine.start(
            RecordingConfig(intervalMs = 500L, stopByDurationEnabled = true, stopDurationSec = 10),
            nowMs = t0
        )
        durEngine.onSample(sampleAt(t0, 5.0, 1.0))
        val before = durEngine.onSample(sampleAt(t0 + 9_000L, 5.0, 1.0))
        assertNull(before.completedSession)
        val after = durEngine.onSample(sampleAt(t0 + 10_000L, 5.0, 1.0))
        assertNotNull(after.completedSession)
        assertTrue(after.completedSession!!.summary.stopReason.contains("시간 도달"))

        // Target capacity (mAh) test: 3.6A for 1s = 1.0 mAh
        val capEngine = RecordingEngine()
        capEngine.start(
            RecordingConfig(intervalMs = 1000L, stopByCapacityEnabled = true, stopTargetMah = 2.0),
            nowMs = t0
        )
        capEngine.onSample(sampleAt(t0, 5.0, 3.6))
        val step1 = capEngine.onSample(sampleAt(t0 + 1000L, 5.0, 3.6)) // 1.0 mAh
        assertNull(step1.completedSession)
        val step2 = capEngine.onSample(sampleAt(t0 + 2000L, 5.0, 3.6)) // 2.0 mAh
        assertNotNull(step2.completedSession)
        assertTrue(step2.completedSession!!.summary.stopReason.contains("목표 용량 도달"))
    }
}
