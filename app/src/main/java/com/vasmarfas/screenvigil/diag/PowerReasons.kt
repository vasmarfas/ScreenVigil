package com.vasmarfas.screenvigil.diag

object PowerReasons {

    data class Reasons(val powerOff: String, val powerOn: String)

    private val POWER_OFF = Regex("Power-off reason: Triggered from (.+)")
    private val POWER_ON = Regex("Power-on reason: Triggered from (.+)")

    fun parse(kernelLog: String): Reasons? {
        val off = POWER_OFF.find(kernelLog)?.groupValues?.get(1)?.trim() ?: return null
        val on = POWER_ON.find(kernelLog)?.groupValues?.get(1)?.trim().orEmpty()
        return Reasons(off, on)
    }
}
