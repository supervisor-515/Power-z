package com.powerz.km003c.protocol

import com.powerz.km003c.model.PdmPdoItem

/**
 * KM003C/KM002C Virtual Serial Port (CDC ACM) protocol trigger commands.
 * Directly implements the official specification in POWER-Z V2.5.1 translations/cmd.pdf.
 */
object PdmSerialProtocol {

    const val CMD_PDM_OPEN = "pdm open"
    const val CMD_PDM_CLOSE = "pdm close"
    const val CMD_RESET = "reset"
    const val CMD_ENTRY_PD = "entry pd"
    const val CMD_ENTRY_UFCS = "entry ufcs"
    const val CMD_ENTRY_QC = "entry qc"
    const val CMD_ENTRY_FCP = "entry fcp"
    const val CMD_ENTRY_SCP = "entry scp"
    const val CMD_ENTRY_AFC = "entry afc"
    const val CMD_ENTRY_VFCP = "entry vfcp"
    const val CMD_ENTRY_SFCP = "entry sfcp"
    const val CMD_ENTRY_TFCP = "entry tfcp"
    const val CMD_ENTRY_BC = "entry bc"
    const val CMD_ENTRY_APPLE = "entry apple"
    const val CMD_ENTRY_LIST = "entry list"
    const val CMD_PD_PDO = "pd pdo"
    const val CMD_UFCS_PDO = "ufcs pdo"

    fun pdmSet(type: Int = 0, em: Int = 0, sink: Int = 0): String =
        "pdm set type=$type,em=$em,sink=$sink"

    fun pdRequestFixed(position: Int, currentMa: Int? = null): String =
        if (currentMa != null) "pd req=$position,cur=$currentMa" else "pd req=$position"

    fun pdRequestPps(position: Int, voltageMv: Int, currentMa: Int? = null): String =
        if (currentMa != null) "pd req=$position,volt=$voltageMv,cur=$currentMa" else "pd req=$position,volt=$voltageMv"

    fun qc2Voltage(volts: Int): String = "qc ${volts}V"
    fun qc3Voltage(voltageMv: Int): String = "qc3 volt=${voltageMv.coerceIn(3600, 20000)}"
    fun qc3Inc(steps: Int = 1): String = "qc3 inc=$steps"
    fun qc3Dec(steps: Int = 1): String = "qc3 dec=$steps"

    fun afcVoltage(volts: Int): String = "afc ${volts}V"
    fun fcpVoltage(volts: Int): String = "fcp ${volts}V"
    fun scpVoltage(voltageMv: Int, currentMa: Int = 3000): String = "scp volt=$voltageMv,cur=$currentMa"
    fun ufcsRequest(index: Int, voltageMv: Int, currentMa: Int): String =
        "ufcs req=$index,volt=$voltageMv,cur=$currentMa"

    private val FIXED_REGEX = Regex("""Fixed:\s*([\d.]+)V\s+([\d.]+)A""", RegexOption.IGNORE_CASE)
    private val PPS_REGEX = Regex("""(PPS|AVS):\s*([\d.]+)\s*-\s*([\d.]+)V\s+([\d.]+)A""", RegexOption.IGNORE_CASE)

    /**
     * Parse the text output of `pd pdo` from KM003C virtual serial port.
     * Example output (from cmd.pdf page 4):
     *   Fixed:   5.00V 3.00A
     *   Fixed:   9.00V 3.00A
     *   PPS: 3.30-5.90V 5.10A
     */
    fun parsePdPdoOutput(text: String): List<PdmPdoItem> {
        val items = mutableListOf<PdmPdoItem>()
        var idx = 1
        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            val fixedMatch = FIXED_REGEX.find(line)
            if (fixedMatch != null) {
                val vMv = (fixedMatch.groupValues[1].toDouble() * 1000).toInt()
                val iMa = (fixedMatch.groupValues[2].toDouble() * 1000).toInt()
                items.add(PdmPdoItem(idx++, "Fixed", vMv, vMv, iMa, line))
                continue
            }
            val ppsMatch = PPS_REGEX.find(line)
            if (ppsMatch != null) {
                val type = ppsMatch.groupValues[1].uppercase()
                val minMv = (ppsMatch.groupValues[2].toDouble() * 1000).toInt()
                val maxMv = (ppsMatch.groupValues[3].toDouble() * 1000).toInt()
                val iMa = (ppsMatch.groupValues[4].toDouble() * 1000).toInt()
                items.add(PdmPdoItem(idx++, type, minMv, maxMv, iMa, line))
            }
        }
        return items
    }
}
