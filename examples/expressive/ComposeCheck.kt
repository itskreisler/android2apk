package com.klei.composecheck

import androidx.compose.runtime.Composable

/**
 * Composable mínimo que solo depende de `compose-runtime`.
 * Sirve para verificar la etapa 2: que el plugin de Compose transforma @Composable.
 */
@Composable
fun Saludo(nombre: String) {
    println("Hola, $nombre")
}
