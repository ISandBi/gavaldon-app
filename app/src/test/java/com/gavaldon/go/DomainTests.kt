package com.gavaldon.go

import com.gavaldon.go.domain.CardBrand
import com.gavaldon.go.domain.CardValidator
import com.gavaldon.go.domain.GeoUtils
import com.gavaldon.go.domain.InputValidator
import com.gavaldon.go.domain.Money
import com.gavaldon.go.domain.PasswordPolicy
import com.gavaldon.go.security.PasswordHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.YearMonth

class CardValidatorTest {
    @Test fun testCardsPassLuhn() {
        listOf("4111111111111111", "4000000000000002", "4000000000000259", "5555555555554444", "378282246310005")
            .forEach { assertTrue(it, CardValidator.luhnValid(it)) }
    }

    @Test fun badLuhnIsRejected() {
        assertFalse(CardValidator.luhnValid("4111111111111112"))
        assertNotNull(CardValidator.validatePan("4111 1111 1111 1112"))
    }

    @Test fun brandDetection() {
        assertEquals(CardBrand.VISA, CardValidator.brandOf("4111111111111111"))
        assertEquals(CardBrand.MASTERCARD, CardValidator.brandOf("5555555555554444"))
        assertEquals(CardBrand.MASTERCARD, CardValidator.brandOf("2221000000000009"))
        assertEquals(CardBrand.AMEX, CardValidator.brandOf("378282246310005"))
        assertEquals(CardBrand.UNKNOWN, CardValidator.brandOf("6011111111111117"))
    }

    @Test fun validPanAccepted() {
        assertNull(CardValidator.validatePan("4111 1111 1111 1111"))
        assertNull(CardValidator.validatePan("3782 822463 10005"))
    }

    @Test fun amexNeedsFifteenDigitsAndFourDigitCvv() {
        assertNotNull(CardValidator.validatePan("3782822463100"))
        assertNull(CardValidator.validateCvv("1234", CardBrand.AMEX))
        assertNotNull(CardValidator.validateCvv("123", CardBrand.AMEX))
        assertNull(CardValidator.validateCvv("123", CardBrand.VISA))
        assertNotNull(CardValidator.validateCvv("12a", CardBrand.VISA))
    }

    @Test fun expiry() {
        val now = YearMonth.of(2026, 10)
        assertNull(CardValidator.validateExpiry("10/26", now))
        assertNull(CardValidator.validateExpiry("01/30", now))
        assertNotNull(CardValidator.validateExpiry("09/26", now))
        assertNotNull(CardValidator.validateExpiry("13/28", now))
        assertNotNull(CardValidator.validateExpiry("1/28", now))
        assertNotNull(CardValidator.validateExpiry("10/99", now))
    }

    @Test fun holder() {
        assertNull(CardValidator.validateHolder("JUAN PÉREZ"))
        assertNotNull(CardValidator.validateHolder("<script>"))
        assertNotNull(CardValidator.validateHolder("A"))
    }

    @Test fun displayGrouping() {
        assertEquals("4111 1111 1111 1111", CardValidator.groupForDisplay("4111111111111111"))
        assertEquals("3782 822463 10005", CardValidator.groupForDisplay("378282246310005"))
        assertEquals("•••• 1111", CardValidator.maskLast4("1111"))
    }

    @Test fun cardDataToStringNeverLeaks() {
        val c = com.gavaldon.go.domain.CardData("X", "4111111111111111", 12, 2030, "123")
        assertFalse(c.toString().contains("4111"))
        assertFalse(c.toString().contains("123"))
    }
}

class InputValidatorTest {
    @Test fun email() {
        assertNull(InputValidator.validateEmail("Mauricio.Garro@ilb.edu.mx"))
        assertNotNull(InputValidator.validateEmail("sin-arroba"))
        assertNotNull(InputValidator.validateEmail("a@b"))
        assertEquals("a@b.com", InputValidator.normalizeEmail("  A@B.com "))
    }

    @Test fun phone() {
        assertEquals("7711234567", InputValidator.normalizePhone("771 123 4567"))
        assertEquals("7711234567", InputValidator.normalizePhone("+52 771 123 4567"))
        assertNull(InputValidator.normalizePhone("12345"))
    }

    @Test fun amountLimits() {
        assertEquals(BigDecimal(50), InputValidator.parseAmount("50"))
        assertEquals(BigDecimal(2000), InputValidator.parseAmount("2000"))
        assertNull(InputValidator.parseAmount("49"))
        assertNull(InputValidator.parseAmount("2001"))
        assertNull(InputValidator.parseAmount("12.5"))
        assertNull(InputValidator.parseAmount("-100"))
        assertNull(InputValidator.parseAmount(""))
    }

    @Test fun passwordPolicy() {
        assertNull(PasswordPolicy.validate("Correcta2026ok".toCharArray()))
        assertNotNull(PasswordPolicy.validate("corta1".toCharArray()))
        assertNotNull(PasswordPolicy.validate("soloLetrasSinNumero".toCharArray()))
        assertNotNull(PasswordPolicy.validate("123456789012345".toCharArray()))
    }
}

class PasswordHasherTest {
    @Test fun roundTripAndSaltUniqueness() {
        val pwd = "Correcta2026ok".toCharArray()
        val h1 = PasswordHasher.hash(pwd)
        val h2 = PasswordHasher.hash(pwd)
        assertTrue(h1.startsWith("pbkdf2$600000$"))
        assertTrue("sal distinta por hash", h1 != h2)
        assertTrue(PasswordHasher.verify(pwd, h1))
        assertFalse(PasswordHasher.verify("otraContrasena1".toCharArray(), h1))
        assertFalse(PasswordHasher.verify(pwd, "basura"))
    }
}

class GeoMoneyTest {
    @Test fun haversine() {
        // Cuauhtemoc -> Centro: aprox. 2.3 km
        val d = GeoUtils.distanceMeters(19.4315751, -99.1596397, 19.4271093, -99.1392078)
        assertTrue("distancia $d", d in 2000.0..2600.0)
        assertEquals("850 m", GeoUtils.formatDistance(850.0))
        assertEquals("1.2 km", GeoUtils.formatDistance(1234.0))
    }

    @Test fun litersTruncateNeverRoundUp() {
        assertEquals(BigDecimal("6.71"), Money.litersFor(BigDecimal(200), BigDecimal("29.80")))
        assertEquals(BigDecimal("50.00"), Money.litersFor(BigDecimal("1490"), BigDecimal("29.80")))
    }
}

class DispatchRulesTests {
    @org.junit.Test
    fun folioFormat() {
        org.junit.Assert.assertTrue(com.gavaldon.go.domain.DispatchRules.isFolio(" tck-2026-000001 "))
        org.junit.Assert.assertFalse(com.gavaldon.go.domain.DispatchRules.isFolio("TCK-26-1"))
    }

    @org.junit.Test
    fun litersValidation() {
        val max = java.math.BigDecimal("37.31")
        val r = com.gavaldon.go.domain.DispatchRules
        org.junit.Assert.assertNull(r.validateLiters("37,31", max))
        org.junit.Assert.assertNull(r.validateLiters("10", max))
        org.junit.Assert.assertNotNull(r.validateLiters("37.32", max))
        org.junit.Assert.assertNotNull(r.validateLiters("0", max))
        org.junit.Assert.assertNotNull(r.validateLiters("abc", max))
        org.junit.Assert.assertNotNull(r.validateLiters("1.234", max))
    }
}
