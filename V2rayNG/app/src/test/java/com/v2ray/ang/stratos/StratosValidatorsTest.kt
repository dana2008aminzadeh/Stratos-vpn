package com.v2ray.ang.stratos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StratosValidatorsTest {

    @Test
    fun `username must start with fyx, 4-8 chars, alnum`() {
        assertTrue(StratosValidators.isValidUsername("fyx12345"))
        assertTrue(StratosValidators.isValidUsername("fyx1"))
        assertTrue(StratosValidators.isValidUsername("fyxAb9"))

        // too short: prefix only
        assertFalse(StratosValidators.isValidUsername("fyx"))
        // too long: 9+ chars
        assertFalse(StratosValidators.isValidUsername("fyx123456"))
        // wrong prefix
        assertFalse(StratosValidators.isValidUsername("abc12345"))
        assertFalse(StratosValidators.isValidUsername("Fyx12345"))
        // symbols
        assertFalse(StratosValidators.isValidUsername("fyx_1234"))
        assertFalse(StratosValidators.isValidUsername("fyx 1234"))
        // empty
        assertFalse(StratosValidators.isValidUsername(""))
    }

    @Test
    fun `password length is 5-10`() {
        assertTrue(StratosValidators.isValidPassword("12345"))
        assertTrue(StratosValidators.isValidPassword("1234567890"))
        assertFalse(StratosValidators.isValidPassword("1234"))
        assertFalse(StratosValidators.isValidPassword("12345678901"))
        assertFalse(StratosValidators.isValidPassword(""))
    }

    @Test
    fun `username normalization trims and lowercases prefix`() {
        assertEquals("fyx12345", StratosValidators.normalizeUsername("  fyx12345 "))
        assertEquals("fyxAB9", StratosValidators.normalizeUsername("FYXAB9"))
    }
}
