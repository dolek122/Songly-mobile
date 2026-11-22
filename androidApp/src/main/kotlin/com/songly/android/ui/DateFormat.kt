package com.songly.android.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_TIME = DateTimeFormatter.ofPattern("d.MM.yyyy, HH:mm")
private val DATE = DateTimeFormatter.ofPattern("d.MM.yyyy")

fun formatDateTime(iso: String): String = runCatching {
	DATE_TIME.format(Instant.parse(iso).atZone(ZoneId.systemDefault()))
}.getOrDefault(iso)

fun formatDate(iso: String): String = runCatching {
	DATE.format(Instant.parse(iso).atZone(ZoneId.systemDefault()))
}.getOrDefault(iso)
