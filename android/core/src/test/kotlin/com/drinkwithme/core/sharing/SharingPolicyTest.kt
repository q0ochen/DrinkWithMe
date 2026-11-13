package com.drinkwithme.core.sharing

import com.drinkwithme.core.bar.DefaultBarCatalog
import com.drinkwithme.core.catalogItem
import com.drinkwithme.core.entryOf
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharingPolicyTest {
    private val pils = catalogItem(DefaultBarCatalog.PILS)
    private val alcoholFreeBeer = catalogItem(DefaultBarCatalog.ALKOHOLFREIES_BIER)
    private val water = catalogItem(DefaultBarCatalog.WASSER)
    private var state = SharingState(isSignedIn = true, alcoholConsent = AlcoholSharingConsent.UNKNOWN)
    private val policy = SharingPolicy { state }

    @Test
    fun fullRuleMatrix() {
        for (signedIn in listOf(true, false)) {
            for (markedPrivate in listOf(true, false)) {
                for (alcoholic in listOf(true, false)) {
                    for (consent in AlcoholSharingConsent.entries) {
                        val expected = signedIn && !markedPrivate && (!alcoholic || consent == AlcoholSharingConsent.ALLOWED)
                        val actual = SharingPolicy.shouldShare(SharingState(signedIn, consent), alcoholic, markedPrivate)
                        assertEquals(expected, actual, "angemeldet=$signedIn privat=$markedPrivate alkoholisch=$alcoholic $consent")
                    }
                }
            }
        }
    }

    @Test
    fun explicitExamplesOfTheMatrix() {
        val allowed = SharingState(true, AlcoholSharingConsent.ALLOWED)
        assertTrue(SharingPolicy.shouldShare(allowed, isAlcoholic = true, markedPrivate = false))
        assertTrue(SharingPolicy.shouldShare(SharingState(true, AlcoholSharingConsent.DENIED), isAlcoholic = false, markedPrivate = false))
        assertFalse(SharingPolicy.shouldShare(SharingState(true, AlcoholSharingConsent.UNKNOWN), isAlcoholic = true, markedPrivate = false))
        assertFalse(SharingPolicy.shouldShare(SharingState(true, AlcoholSharingConsent.DENIED), isAlcoholic = true, markedPrivate = false))
        assertFalse(SharingPolicy.shouldShare(allowed, isAlcoholic = false, markedPrivate = true))
        assertFalse(SharingPolicy.shouldShare(SharingState(false, AlcoholSharingConsent.ALLOWED), isAlcoholic = false, markedPrivate = false))
    }

    @Test
    fun shareOverrideFalseMarksTheEntryPrivate() {
        assertTrue(policy.shareWithFriends(water, shareOverride = null))
        assertFalse(policy.shareWithFriends(water, shareOverride = false))
        assertTrue(policy.shareWithFriends(water, shareOverride = true))
    }

    @Test
    fun shareOverrideTrueNeverReplacesConsentOrSignIn() {
        assertFalse(policy.shareWithFriends(pils, shareOverride = true))
        state = state.copy(alcoholConsent = AlcoholSharingConsent.DENIED)
        assertFalse(policy.shareWithFriends(pils, shareOverride = true))
        state = SharingState(isSignedIn = false, alcoholConsent = AlcoholSharingConsent.ALLOWED)
        assertFalse(policy.shareWithFriends(water, shareOverride = true))
    }

    @Test
    fun alcoholFreeBeerIsNotAlcoholicForSharing() {
        assertTrue(policy.shareWithFriends(alcoholFreeBeer, shareOverride = null))
        assertFalse(policy.shareWithFriends(pils, shareOverride = null))
        state = state.copy(alcoholConsent = AlcoholSharingConsent.ALLOWED)
        assertTrue(policy.shareWithFriends(pils, shareOverride = null))
    }

    @Test
    fun consentQuestionOnlyForTheFirstAlcoholicDrinkWhileSignedIn() {
        assertTrue(policy.needsAlcoholConsentQuestion(pils))
        assertFalse(policy.needsAlcoholConsentQuestion(water))
        assertFalse(policy.needsAlcoholConsentQuestion(alcoholFreeBeer))
        state = state.copy(alcoholConsent = AlcoholSharingConsent.DENIED)
        assertFalse(policy.needsAlcoholConsentQuestion(pils))
        state = SharingState(isSignedIn = false, alcoholConsent = AlcoholSharingConsent.UNKNOWN)
        assertFalse(policy.needsAlcoholConsentQuestion(pils))
        assertEquals("Alkoholische Getränke mit Freunden teilen?", SharingPolicy.ALCOHOL_CONSENT_QUESTION)
    }

    @Test
    fun publishingRechecksTheCurrentState() {
        val sharedBeer = entryOf(pils, Instant.EPOCH, share = true)
        val privateWater = entryOf(water, Instant.EPOCH, share = false)
        state = SharingState(true, AlcoholSharingConsent.ALLOWED)
        assertTrue(policy.shouldPublish(sharedBeer))
        assertFalse(policy.shouldPublish(privateWater))

        state = state.copy(alcoholConsent = AlcoholSharingConsent.DENIED) // Zustimmung widerrufen
        assertFalse(policy.shouldPublish(sharedBeer))
        state = SharingState(false, AlcoholSharingConsent.ALLOWED) // abgemeldet
        assertFalse(policy.shouldPublish(sharedBeer))
        assertFalse(policy.canUnpublish())
    }

    @Test
    fun sharedDrinkContainsOnlyTheAllowedFields() {
        val entry = entryOf(pils, Instant.parse("2026-11-10T18:00:00Z"), volume = 330, share = true, id = "e-1")
        val shared = entry.toSharedDrink()
        assertEquals(SharedDrink("e-1", "Pils", pils.icon, pils.category, 330, entry.consumedAt), shared)
        assertEquals(
            setOf("id", "name", "icon", "category", "volumeMilliliters", "consumedAt"),
            SharedDrink::class.java.declaredFields.map { it.name }.filterNot { it.startsWith("$") }.toSet(),
        )
    }

    @Test
    fun volumeFormatterForDisplay() {
        assertEquals("250 ml", VolumeFormatter.format(250))
        assertEquals("999 ml", VolumeFormatter.format(999))
        assertEquals("1 l", VolumeFormatter.format(1_000))
        assertEquals("1,3 l", VolumeFormatter.format(1_250))
        assertEquals("1,5 l", VolumeFormatter.format(1_500))
        assertEquals("2,5 l", VolumeFormatter.format(2_480))
    }
}
