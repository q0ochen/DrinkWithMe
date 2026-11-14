package com.drinkwithme.core

import com.drinkwithme.core.bar.BarItem
import com.drinkwithme.core.bar.DefaultBarCatalog
import com.drinkwithme.core.log.DrinkEntry
import com.drinkwithme.core.social.InviteLinks
import com.drinkwithme.core.speech.GermanArticles
import com.drinkwithme.core.speech.GermanNumberWords
import com.drinkwithme.core.speech.SpeechTexts
import com.drinkwithme.core.stats.MonthComparison
import com.drinkwithme.core.stats.StatisticsCalculator
import com.drinkwithme.core.stats.TodaySummary
import com.drinkwithme.core.stats.WeeklyAlcohol
import com.drinkwithme.core.voice.GermanTextNormalizer
import com.drinkwithme.core.voice.JaroWinkler
import com.drinkwithme.core.voice.KoelnerPhonetik
import com.drinkwithme.core.voice.PhoneticTextSimilarity
import com.drinkwithme.core.voice.VoiceCommandMatcher
import com.drinkwithme.core.voice.VoiceMatch
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.test.Test

/**
 * Erzeugt web/src/core/golden/kotlin-golden.json: viele (pseudozufällige, aber feste) Eingaben und die Ausgaben
 * des Kotlin-Kerns. Der TypeScript-Port muss für dieselben Eingaben exakt dieselben Ausgaben liefern
 * (siehe web/src/core/golden/kotlinGenerated.golden.test.ts). Ausführen: web/tools/kotlin-golden/export.sh
 */
class GoldenExport {
    private val random = Random(20261110)

    @Test
    fun export() {
        val out = System.getenv("GOLDEN_OUT") ?: error("GOLDEN_OUT fehlt")
        val json = obj(
            "numbers" to numbers(),
            "speech" to speech(),
            "articles" to articles(),
            "phonetics" to phonetics(),
            "voice" to voice(),
            "statistics" to statistics(),
            "invites" to invites(),
        )
        File(out).writeText(json + "\n")
    }

    // ---------------- Zahlen ----------------

    private fun numbers(): String {
        val cardinals = (0..1200).toList() + List(300) { random.nextInt(0, 2_000_000_000) } + listOf(-1, -42, 999_999_999)
        val decimals = List(400) { random.nextDouble(-50.0, 500.0) } +
            List(200) { (random.nextInt(0, 100_000) / 1000.0) } +
            listOf(0.05, 0.15, 0.25, 0.35, 1.005, 2.675, 9.95, 9.96, 99.95, 0.0, -0.0, 1e-7, 123456.789)
        return obj(
            "cardinal" to arr(cardinals.map { arr(listOf(num(it.toDouble()), str(GermanNumberWords.cardinal(it.toLong())))) }),
            "decimal" to arr(decimals.flatMap { value ->
                (0..2).map { digits -> arr(listOf(num(value), num(digits.toDouble()), str(GermanNumberWords.decimal(value, digits)))) }
            }),
            "ordinal" to arr((1..99).map { arr(listOf(num(it.toDouble()), str(GermanNumberWords.ordinalDative(it)))) }),
        )
    }

    private fun speech(): String {
        val grams = List(300) { random.nextDouble(0.0, 200.0) } + List(100) { random.nextInt(0, 2000) / 100.0 }
        val volumes = (1..2000 step 7).toList() + listOf(999, 1000, 1049, 1050, 1949, 1950, 2000)
        val todays = List(150) {
            val count = random.nextInt(0, 6)
            TodaySummary(
                sugarGrams = if (random.nextBoolean()) 0.0 else random.nextDouble(0.0, 120.0),
                alcoholGrams = if (random.nextBoolean()) 0.0 else random.nextDouble(0.0, 80.0),
                sugaryDrinks = random.nextInt(0, 4),
                alcoholicDrinks = random.nextInt(0, 4),
                totalMilliliters = random.nextInt(0, 4000),
                drinkCount = count,
            )
        }
        val today = LocalDate.of(2026, 11, 10)
        val weeks = List(150) {
            WeeklyAlcohol(
                weekStart = today.minusWeeks(random.nextLong(0, 12)).with(java.time.DayOfWeek.MONDAY),
                grams = if (random.nextInt(4) == 0) 0.0 else random.nextDouble(0.0, 200.0),
                alcoholFreeDays = random.nextInt(0, 8),
            )
        }
        val comparisons = List(150) {
            MonthComparison(
                sugarChangePercent = if (random.nextInt(4) == 0) null else random.nextDouble(-120.0, 300.0),
                alcoholChangePercent = if (random.nextInt(4) == 0) null else random.nextDouble(-100.0, 300.0),
                sugarGramsLast30Days = 0.0, sugarGramsPrevious30Days = 0.0, alcoholGramsLast30Days = 0.0, alcoholGramsPrevious30Days = 0.0,
            )
        }
        val names = listOf("Anna", " Ben ", "Anna 🌹", "", "Zoë", "Jean-Luc")
        val drinks = DefaultBarCatalog.items.map { it.name } + listOf("Kombucha", "🥤", "Bier 🍺️", "Glas Wein", "2 Bier", "Club-Mate")
        return obj(
            "grams" to arr(grams.map { arr(listOf(num(it), str(SpeechTexts.grams(it)))) }),
            "volume" to arr(volumes.map { arr(listOf(num(it.toDouble()), str(SpeechTexts.volume(it)))) }),
            "today" to arr(todays.map { summary ->
                obj(
                    "input" to arr(listOf(num(summary.sugarGrams), num(summary.alcoholGrams), num(summary.sugaryDrinks.toDouble()),
                        num(summary.alcoholicDrinks.toDouble()), num(summary.totalMilliliters.toDouble()), num(summary.drinkCount.toDouble()))),
                    "text" to str(SpeechTexts.today(summary)),
                )
            }),
            "weekly" to arr(weeks.map { week ->
                obj(
                    "weekStart" to str(week.weekStart.toString()),
                    "grams" to num(week.grams),
                    "alcoholFreeDays" to num(week.alcoholFreeDays.toDouble()),
                    "today" to str(today.toString()),
                    "text" to str(SpeechTexts.weeklyAlcohol(week, today)),
                )
            }),
            "comparison" to arr(comparisons.map { comparison ->
                obj(
                    "sugar" to (comparison.sugarChangePercent?.let(::num) ?: "null"),
                    "alcohol" to (comparison.alcoholChangePercent?.let(::num) ?: "null"),
                    "text" to str(SpeechTexts.monthComparison(comparison)),
                )
            }),
            "drinkEvent" to arr(names.flatMap { name ->
                drinks.map { drink ->
                    val volume = if (random.nextBoolean()) null else random.nextInt(1, 2001)
                    obj(
                        "author" to str(name),
                        "drink" to str(drink),
                        "volume" to (volume?.let { num(it.toDouble()) } ?: "null"),
                        "text" to str(SpeechTexts.drinkEvent(name, drink, volume)),
                    )
                }
            }),
            "speakable" to arr((names + drinks + listOf("a b", " \t x ‍ ", "Äpfel")).map { arr(listOf(str(it), str(SpeechTexts.speakable(it)))) }),
        )
    }

    private fun articles(): String {
        val names = DefaultBarCatalog.items.map { it.name } + listOf(
            "Glas Wein", "Tasse Kakao", "Ein Glas Milch", "2 Bier", "Club-Mate", "Latte Macchiato", "Kombucha", "Kölsch",
            "Spezi", "Weißweinschorle", "Becher Kaffee", "Gin Tonic", "Hugo Spritz", "Ayran", "  Mate  ", "noch ein Bier",
        )
        return arr(names.map { arr(listOf(str(it), str(GermanArticles.withIndefiniteArticle(it)), str(GermanArticles.genderOf(it)?.name ?: "null"))) })
    }

    // ---------------- Sprache ----------------

    private fun words(): List<String> {
        val base = DefaultBarCatalog.items.flatMap { listOf(it.name) + it.aliases } + listOf(
            "Müller-Lüdenscheidt", "Breschnew", "Xaver", "Phi", "Chemie", "Christstollen", "Tschechien", "Sachsen", "Dachs",
            "Pilz", "Bils", "Kola", "Weissbier", "Ice Tea", "Eistee", "Sprite", "Fanta", "Spezi", "Cappuccino", "Espresso",
        )
        val mutated = List(250) { mutate(base[random.nextInt(base.size)]) }
        return base + mutated
    }

    private fun mutate(word: String): String {
        if (word.length < 3) return word
        val chars = word.toMutableList()
        when (random.nextInt(4)) {
            0 -> chars.removeAt(random.nextInt(chars.size))
            1 -> chars[random.nextInt(chars.size)] = "abcdefghijklmnopqrstuvwxyzäöüß"[random.nextInt(30)]
            2 -> { val i = random.nextInt(chars.size - 1); val t = chars[i]; chars[i] = chars[i + 1]; chars[i + 1] = t }
            else -> chars.add(random.nextInt(chars.size), "aeiou"[random.nextInt(5)])
        }
        return chars.joinToString("")
    }

    private fun phonetics(): String {
        val list = words()
        val pairs = List(500) { list[random.nextInt(list.size)] to list[random.nextInt(list.size)] }
        val similarity = PhoneticTextSimilarity()
        return obj(
            "normalize" to arr(list.map { arr(listOf(str(it), str(GermanTextNormalizer.normalize(it)))) }),
            "koelner" to arr(list.map { arr(listOf(str(it), str(KoelnerPhonetik.encode(it)))) }),
            "similarity" to arr(pairs.map { (a, b) ->
                val na = GermanTextNormalizer.normalize(a).replace(" ", "")
                val nb = GermanTextNormalizer.normalize(b).replace(" ", "")
                arr(listOf(str(na), str(nb), num(JaroWinkler.similarity(na, nb)), num(similarity.similarity(na, nb))))
            }),
        )
    }

    private fun voice(): String {
        val catalog = DefaultBarCatalog.items
        val fillers = listOf("ich trinke", "ein", "eine", "bitte", "zwei", "ein großes Glas", "noch eine Tasse", "0,5 Liter", "")
        val commands = listOf("rückgängig", "Nein danke", "vorlesen", "lies das vor", "wie viel heute", "Was habe ich heute getrunken", "nee")
        val candidates = words().map { word ->
            val filler = fillers[random.nextInt(fillers.size)]
            if (filler.isEmpty()) word else "$filler $word"
        } + commands + listOf("Fahrrad", "", "bitte", "Wein", "Saft", "Apfel", "Bier", "Wasser bitte") + fragments(catalog)
        val bars = listOf(
            "catalog" to catalog,
            "default" to DefaultBarCatalog.defaultSelection(),
            "small" to catalog.filter { it.id in setOf(DefaultBarCatalog.WEIZENBIER, DefaultBarCatalog.WASSER, DefaultBarCatalog.ROTWEIN) },
        )
        return arr(bars.flatMap { (barName, bar) ->
            candidates.map { candidate ->
                val result = VoiceCommandMatcher.match(listOf(candidate), bar)
                obj("bar" to str(barName), "candidate" to str(candidate), "result" to str(describe(result)))
            }
        })
    }

    /** Wortanfänge und -enden der Namen („wein“, „bier“, „apfel“) – prüft die Schwellen 0,88/0,05 und Wortenden. */
    private fun fragments(catalog: List<BarItem>): List<String> =
        catalog.flatMap { listOf(it.name) + it.aliases }
            .flatMap { GermanTextNormalizer.tokens(it) }
            .filter { it.length >= 4 }
            .flatMap { token -> (3..minOf(7, token.length - 1)).flatMap { n -> listOf(token.takeLast(n), token.take(n)) } }
            .distinct()

    private fun describe(match: VoiceMatch): String = when (match) {
        is VoiceMatch.Drink -> "Drink:${match.item.id}"
        is VoiceMatch.Ambiguous -> "Ambiguous:${match.first.id}|${match.second.id}"
        is VoiceMatch.Command -> "Command:${match.cmd.name}"
        VoiceMatch.NoMatch -> "NoMatch"
    }

    // ---------------- Statistik ----------------

    private fun statistics(): String {
        val zone = ZoneId.of("Europe/Berlin")
        val scenarios = List(40) { index ->
            val now = Instant.parse("2026-11-10T12:00:00Z").plusSeconds(random.nextLong(-400L * 86_400, 400L * 86_400))
            val catalog = DefaultBarCatalog.items
            val count = if (index == 0) 0 else random.nextInt(1, 160)
            val spanDays = random.nextLong(1, 120)
            val entries = List(count) { n ->
                val item: BarItem = catalog[random.nextInt(catalog.size)]
                DrinkEntry(
                    id = "e-$index-$n",
                    barItemId = item.id,
                    name = item.name,
                    icon = item.icon,
                    category = item.category,
                    volumeMilliliters = if (random.nextInt(5) == 0) random.nextInt(1, 2001) else item.servingMilliliters,
                    alcoholPercent = item.alcoholPercent,
                    sugarGramsPer100Ml = item.sugarGramsPer100Ml,
                    consumedAt = now.minusSeconds(random.nextLong(0, spanDays * 86_400)),
                    shareWithFriends = false,
                )
            }
            val stats = StatisticsCalculator(Clock.fixed(now, ZoneOffset.UTC), zone)
            val today = stats.today(entries)
            val month = stats.monthComparison(entries)
            val average = stats.movingAverage4Weeks(entries)
            obj(
                "now" to num(now.toEpochMilli().toDouble()),
                "entries" to arr(entries.map { entry ->
                    obj(
                        "id" to str(entry.id), "name" to str(entry.name), "icon" to str(entry.icon.key), "category" to str(entry.category.name),
                        "volume" to num(entry.volumeMilliliters.toDouble()), "alcohol" to num(entry.alcoholPercent),
                        "sugar" to num(entry.sugarGramsPer100Ml), "at" to num(entry.consumedAt.toEpochMilli().toDouble()),
                    )
                }),
                "dailySugar" to arr(stats.dailySugar(entries).map { arr(listOf(str(it.date.toString()), num(it.grams))) }),
                "weeklyAlcohol" to arr(stats.weeklyAlcohol(entries).map {
                    arr(listOf(str(it.weekStart.toString()), num(it.grams), num(it.alcoholFreeDays.toDouble()), str(it.band.name)))
                }),
                "today" to arr(listOf(num(today.sugarGrams), num(today.alcoholGrams), num(today.sugaryDrinks.toDouble()),
                    num(today.alcoholicDrinks.toDouble()), num(today.totalMilliliters.toDouble()), num(today.drinkCount.toDouble()))),
                "month" to arr(listOf(month.sugarChangePercent?.let(::num) ?: "null", month.alcoholChangePercent?.let(::num) ?: "null",
                    num(month.sugarGramsLast30Days), num(month.sugarGramsPrevious30Days), num(month.alcoholGramsLast30Days),
                    num(month.alcoholGramsPrevious30Days))),
                "average" to arr(listOf(average.alcoholGramsPerWeek?.let(::num) ?: "null", average.sugarGramsPerDay?.let(::num) ?: "null",
                    num(average.coveredDays.toDouble()))),
                "streak" to num(stats.alcoholFreeStreak(entries).toDouble()),
            )
        }
        return arr(scenarios)
    }

    private fun invites(): String {
        val token = "AbCdEfGhIjKlMnOpQrStUv"
        val inputs = listOf(
            token, " $token ", "drinkwithme://einladung/$token", "DRINKWITHME://einladung/$token", "drinkwithme://EINLADUNG/$token",
            "drinkwithme://einladung/$token/", "drinkwithme://einladung//$token", "https://q0ochen.github.io/DrinkWithMe/einladung/$token",
            "HTTPS://example.org/einladung/$token", "https://example.org:8443/einladung/$token", "https://user@example.org/einladung/$token",
            "https://example.org/einladung/$token?x=1#y", "https://example.org/a/b/einladung/$token", "https://example.org/einladung/$token/x",
            "http://example.org/einladung/$token", "ftp://example.org/einladung/$token", "einladung/$token", "https://example.org/einladung/",
            "https://example.org/einladung/${token}%20", "drinkwithme:einladung/$token", "https://example.org/Einladung/$token",
            "a".repeat(22), "a".repeat(21), "a".repeat(128), "a".repeat(129), "abc def ghi jkl mno pqr", "",
        )
        return arr(inputs.map { arr(listOf(str(it), InviteLinks.parseToken(it)?.let(::str) ?: "null")) })
    }

    // ---------------- Kleiner JSON-Schreiber ----------------

    private fun str(value: String): String = buildString {
        append('"')
        for (char in value) {
            when {
                char == '"' -> append("\\\"")
                char == '\\' -> append("\\\\")
                char.code < 0x20 || char.code in 0x7F..0x9F || char.isSurrogate() || char.code > 0x7E ->
                    append("\\u").append(char.code.toString(16).padStart(4, '0'))
                else -> append(char)
            }
        }
        append('"')
    }

    private fun num(value: Double): String {
        require(value.isFinite())
        return if (value == 0.0 && 1.0 / value < 0) "-0.0" else value.toString().replace("E", "e")
    }

    private fun arr(items: List<String>): String = items.joinToString(",", "[", "]")

    private fun obj(vararg pairs: Pair<String, String>): String = pairs.joinToString(",", "{", "}") { (key, value) -> "${str(key)}:$value" }
}
