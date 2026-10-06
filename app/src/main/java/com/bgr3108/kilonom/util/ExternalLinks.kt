package com.bgr3108.kilonom.util

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

object ExternalLinks {
    const val PRIVACY_POLICY_URL = "https://bgr3108.github.io/CocheHibrido/privacy/"
    const val INSTAGRAM_PROFILE_URL = "https://www.instagram.com/kilonom.app/"
    const val SUPPORT_EMAIL_ADDRESS = "kilonom.app@gmail.com"
    const val MITECO_STATIONS_URL = "https://catalogo.datosabiertos.miteco.gob.es/catalogo/es/dataset/902a266d-5ba2-4735-a378-45818ba5a4f4"
    const val MITECO_CHARGERS_URL = "https://catalogo.datosabiertos.miteco.gob.es/catalogo/es/dataset/6ee8d46f-93bd-478f-8e29-3ba4f6d8405c"
}

internal data class ExternalIntentRequest(
    val action: String,
    val uri: String,
    val subject: String? = null,
    val body: String? = null
)

internal fun externalUrlIntentRequest(url: String): ExternalIntentRequest = ExternalIntentRequest(
    action = Intent.ACTION_VIEW,
    uri = url
)

internal fun vehicleRequestEmailIntentRequest(
    brand: String?,
    model: String?,
    year: String?,
    variant: String?,
    fuel: String? = null,
    note: String? = null
): ExternalIntentRequest = ExternalIntentRequest(
    action = Intent.ACTION_SENDTO,
    uri = "mailto:${ExternalLinks.SUPPORT_EMAIL_ADDRESS}",
    subject = "Kilonom - Solicitud de vehículo",
    body = buildString {
        appendLine("Hola,")
        appendLine()
        appendLine("No encuentro mi vehículo en Kilonom.")
        appendLine()
        brand?.takeIf(String::isNotBlank)?.let { appendLine("Marca: $it") }
        model?.takeIf(String::isNotBlank)?.let { appendLine("Modelo: $it") }
        year?.takeIf(String::isNotBlank)?.let { appendLine("Año: $it") }
        fuel?.takeIf(String::isNotBlank)?.let { appendLine("Combustible: $it") }
        variant?.takeIf(String::isNotBlank)?.let { appendLine("Motorización: $it") }
        note?.takeIf(String::isNotBlank)?.let { appendLine(it) }
        appendLine()
        append("Gracias.")
    }
)

fun Context.openExternalUrl(url: String): Boolean = runCatching {
    val request = externalUrlIntentRequest(url)
    startActivity(Intent(request.action, request.uri.toUri()))
}.isSuccess

fun Context.openVehicleRequestEmail(
    brand: String?,
    model: String?,
    year: String?,
    variant: String?,
    fuel: String? = null,
    note: String? = null
): Boolean = runCatching {
    val request = vehicleRequestEmailIntentRequest(brand, model, year, variant, fuel, note)
    startActivity(Intent(request.action, request.uri.toUri()).apply {
        putExtra(Intent.EXTRA_SUBJECT, request.subject)
        putExtra(Intent.EXTRA_TEXT, request.body)
    })
}.isSuccess
