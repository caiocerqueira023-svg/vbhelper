package com.tamerslab.vpet

/**
 * Transporte de linhas ASCII para o adaptador (A-Com / BTCom), equivalente ao VpetLink do
 * DBT-Link (`online.battleterminal.vital.VpetLink`). Todas as implementações são line-based:
 * o adaptador responde com linhas terminadas em '\n' (bytes ISO-8859-1).
 */
interface VpetLink {
    fun enviar(texto: String)
    fun cerrar()
}

/** Resultado decodificado pelo lector.js (um Digimon lido do VPet). */
data class VpetReadResult(
    val tipo: String,          // dm20 | dmx | pen20 | penz | dmc | penc
    val version: Int,
    val indice: Int,
    val nombre: String?,
    val etapa: Int?,           // 0..5 (Baby I..Super Ultimate) ou null
    val attr: Int?,            // 0 Vaccine, 1 Data, 2 Virus, 3 Free
    val poder: Int?,
    val hex: String,
    val valido: Boolean,
)

/** Etapas: tabela usada pelo DBT-Link (VPET.etapas). */
enum class VpetStage(val level: Int, val label: String) {
    BABY_I(0, "Baby I"), BABY_II(1, "Baby II"), CHILD(2, "Child"),
    ADULT(3, "Adult"), PERFECT(4, "Perfect"), ULTIMATE(5, "Ultimate"),
}
