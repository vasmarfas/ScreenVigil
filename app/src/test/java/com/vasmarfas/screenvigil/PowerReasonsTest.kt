package com.vasmarfas.screenvigil

import com.vasmarfas.screenvigil.diag.PowerReasons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PowerReasonsTest {

    @Test
    fun `reasons are read from the pm8916 boot lines`() {
        val log = """
            [    0.634762] usbcore: registered new interface driver xpad
            [    2.887680] qcom,qpnp-power-on qpnp-power-on-ee336200: PMIC@SID0 Power-on reason: Triggered from Hard Reset and 'cold' boot
            [    2.887705] qcom,qpnp-power-on qpnp-power-on-ee336200: PMIC@SID0: Power-off reason: Triggered from UVLO (Under Voltage Lock Out)
            [    2.889806] PMIC@SID0: PM8916 v2.0 options: 0, 2, 0, 0
        """.trimIndent()
        assertEquals(
            PowerReasons.Reasons("UVLO (Under Voltage Lock Out)", "Hard Reset and 'cold' boot"),
            PowerReasons.parse(log),
        )
    }

    @Test
    fun `nothing is reported once the boot lines have rotated out`() {
        val log = "[  138.630205] i2c-msm-v2 78b6000.i2c: NACK: slave not responding, ensure its powered"
        assertNull(PowerReasons.parse(log))
    }
}
