package com.v2ray.ang.stratos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StratosQrLoginTest {

    @Test
    fun `parse json payload`() {
        val creds = StratosQrLogin.parse("{\"u\":\"fyx12345\",\"p\":\"secret42\"}")
        assertEquals("fyx12345", creds?.username)
        assertEquals("secret42", creds?.password)
    }

    @Test
    fun `parse json payload with long keys`() {
        val creds = StratosQrLogin.parse("{\"username\":\"fyx99\",\"password\":\"123456\"}")
        assertEquals("fyx99", creds?.username)
        assertEquals("123456", creds?.password)
    }

    @Test
    fun `parse stratos uri`() {
        val creds = StratosQrLogin.parse("stratos://login?u=fyx1&p=abcde")
        assertEquals("fyx1", creds?.username)
        assertEquals("abcde", creds?.password)
    }

    @Test
    fun `garbage is rejected`() {
        assertNull(StratosQrLogin.parse(null))
        assertNull(StratosQrLogin.parse(""))
        assertNull(StratosQrLogin.parse("   "))
        assertNull(StratosQrLogin.parse("vless://some-config"))
        assertNull(StratosQrLogin.parse("{\"u\":\"fyx12345\"}"))
        assertNull(StratosQrLogin.parse("stratos://login?u=fyx1"))
    }
}
