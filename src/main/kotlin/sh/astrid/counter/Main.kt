package sh.astrid.counter

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URLEncoder
import java.nio.file.Path
import java.util.concurrent.Executors

@Serializable
data class Config(val songs: List<String>)
@Serializable
data class Response(val lyrics: String, val url: String)

val array = mutableListOf<String>()

fun countWords(): Map<String, Int> {
    val wordCount = mutableMapOf<String, Int>()

    for (word in array) {
        val count = wordCount[word] ?: 0
        wordCount[word] = count + 1
    }

    return wordCount
}

fun addWords(song: String) {
    val res = get("https://lyrics.astrid.sh/api/search?q=" + URLEncoder.encode(song, "utf-8"))

    try {
        val body = Json.decodeFromString(res.body()) as Response
        val words = body.lyrics
            .lowercase()
            .replace("\n\n", "")
            .replace(Regex("/[-_\"]/g"), "")
            .replace("(", "")
            .replace(")", "")
            .replace(",", "")
            .replace(" ", " ")
            .replace(" ", "")
            .replace(Regex("/\\(.*\\)/"), "")
            .split(" ")

        synchronized(array) {
            array.addAll(words)
        }

        println("Added $song to the list")
    } catch (ex: Exception) {
        println("$song is an invalid song")
    }
}

fun main() {
    val conf = Json.decodeFromString(Path.of("config.json").toFile().readText()) as Config

    val started = System.currentTimeMillis()

    println("Looking for ${conf.songs.size} songs.")

    val executor = Executors.newFixedThreadPool(conf.songs.size)
    val futures = mutableListOf<java.util.concurrent.Future<*>>()

    for (song in conf.songs) {
        val future = executor.submit { addWords(song) }
        futures.add(future)
    }

    futures.forEach { it.get() }

    executor.shutdown()

    val outputFile = File("word_counts.txt")
    val wordCounts = countWords().entries.sortedByDescending { it.value }

    outputFile.bufferedWriter().use { writer ->
        writer.write("Scanned ${conf.songs.size} total songs\n")
        for ((word, count) in wordCounts) {
            writer.write("$word: $count\n")
        }
    }

    val ended = System.currentTimeMillis()

    println("Fetching songs took ${ended - started}ms. Saved to the file.")
}
