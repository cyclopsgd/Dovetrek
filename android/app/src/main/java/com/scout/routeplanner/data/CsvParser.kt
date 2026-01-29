package com.scout.routeplanner.data

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object CsvParser {

    fun parseOpenings(inputStream: InputStream): OpeningsData {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val headerLine = reader.readLine()?.trimStart('\uFEFF') // strip BOM
            ?: throw IllegalArgumentException("Empty openings file")

        val header = headerLine.split(",").map { it.trim() }
        // header: CP, BNG, 1000, 1030, ..., 1700
        val slotLabels = header.drop(2)
        val slotStarts = slotLabels.map { label ->
            val h = label.substring(0, label.length - 2).toInt()
            val m = label.substring(label.length - 2).toInt()
            h * 60 + m
        }

        val cpNames = mutableListOf<String>()
        val openings = mutableMapOf<String, List<Int>>()
        val bngRefs = mutableMapOf<String, String>()

        reader.forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            val parts = line.split(",").map { it.trim() }
            if (parts.isEmpty() || parts[0].isBlank()) return@forEachLine
            val name = parts[0]
            cpNames.add(name)
            if (parts.size > 1 && parts[1].isNotBlank()) {
                bngRefs[name] = parts[1]
            }
            val slots = parts.drop(2).map { it.toIntOrNull() ?: 0 }
            openings[name] = slots
        }

        reader.close()
        return OpeningsData(cpNames, slotStarts, openings, bngRefs)
    }

    fun parseDistances(inputStream: InputStream): Map<Pair<String, String>, DistanceRecord> {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        reader.readLine()?.trimStart('\uFEFF')
            ?: throw IllegalArgumentException("Empty distances file")

        val result = mutableMapOf<Pair<String, String>, DistanceRecord>()

        reader.forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            val parts = line.split(",").map { it.trim() }
            if (parts.size < 4) return@forEachLine
            val startCp = parts[0]
            val finishCp = parts[1]
            val distance = parts[2].toFloatOrNull() ?: return@forEachLine
            val heightGain = parts[3].toFloatOrNull() ?: return@forEachLine
            result[Pair(startCp, finishCp)] = DistanceRecord(distance, heightGain)
        }

        reader.close()
        return result
    }
}
