package com.ivannafusion


data class PFPreset(
    val name: String,
    val displayName: String,
    val emoji: String,
    val ampModel: Int,
    val alpha: Float, val beta: Float, val gamma: Float,
    val delta: Float, val sigma: Float,
    val drive: Float, val wet: Float,
    val lowGain: Float, val midGain: Float, val highGain: Float,
    val midFreq: Float, val presence: Float,
    val sag: Float, val bias: Float,
    val description: String,
    val color: Long            // color como Long para evitar dependencia de Compose en el modelo
)

val AMP_NAMES = listOf("Marshall", "Fender", "Vox", "70s Rock", "Bypass")

val PF_PRESETS = listOf(
    PFPreset("clean_studio","Clean Studio","🎙️",1,
        0.95f,0.15f,0.40f,0.10f,0.70f,0.80f,0.60f,2.0f,0.5f,1.5f,500f,1.5f,0.05f,0.55f,
        "Fender limpio. Ideal para grabación vocal y guitarras cristalinas.",0xFF44AAFF),
    PFPreset("marshall_crunch","Marshall Crunch","🎸",0,
        1.10f,0.55f,0.60f,0.75f,0.40f,3.20f,0.85f,3.0f,-1.5f,4.0f,700f,5.0f,0.25f,0.45f,
        "Stack Marshall clásico. Crunch agresivo con presencia brutal.",0xFFFF4444),
    PFPreset("vox_sparkle","Vox Sparkle","✨",2,
        1.00f,0.40f,0.50f,0.35f,0.55f,1.80f,0.70f,0.5f,3.5f,2.0f,1200f,3.0f,0.12f,0.50f,
        "AC30 estilo Vox. Medios brillantes y presencia chispeante.",0xFFFFCC44),
    PFPreset("70s_rock","70s Rock","🎤",3,
        1.05f,0.50f,0.65f,0.60f,0.50f,2.80f,0.80f,4.0f,1.0f,3.0f,900f,4.0f,0.20f,0.48f,
        "Full-stack 70s (Rush, Grand Funk). Cuerpo y ataque imparable.",0xFFFF8C00),
    PFPreset("psychedelic","Psychedelic","🌀",3,
        1.00f,0.55f,0.60f,0.50f,0.70f,2.20f,0.75f,2.0f,2.5f,3.5f,1400f,5.0f,0.15f,0.50f,
        "Textura sicodélica con amplios harmónicos. Floyd / Hendrix.",0xFFCC44FF),
    PFPreset("flat","Flat","⚖️",4,
        1.00f,0.00f,0.50f,0.00f,0.50f,1.00f,0.00f,0.0f,0.0f,0.0f,800f,0.0f,0.00f,0.50f,
        "Bypass total. Señal pura, sin coloración.",0xFF888888)
)
