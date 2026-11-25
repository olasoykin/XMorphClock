package com.drdisagree.xmorphclock.data.source

import com.drdisagree.xmorphclock.domain.model.LockscreenAffordance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemShortcutControllerTest {

    @Test
    fun parseAvailableAffordancesStripsExtraIconColumnAndPreservesCleanNames() {
        val raw = """
            Row: 0 id=camera, name=Camera, icon=2131234567, is_enabled=1, enablement_explanation=NULL
            Row: 1 id=flashlight, name=Flashlight, icon=2131234568, is_enabled=1, enablement_explanation=NULL
            Row: 2 id=wallet, name=Wallet, icon=2131234569, is_enabled=0, enablement_explanation=Add a card first
        """.trimIndent()

        val parsed = SystemShortcutController.parseAvailableAffordances(raw)
        assertNotNull(parsed)
        val items = requireNotNull(parsed)
        assertEquals(4, items.size) // includes "none" + 3 parsed rows

        val camera = items.first { it.id == "camera" }
        assertEquals("Camera", camera.name)
        assertTrue(camera.isEnabled)
        assertNull(camera.explanation)

        val flashlight = items.first { it.id == "flashlight" }
        assertEquals("Flashlight", flashlight.name)

        val wallet = items.first { it.id == "wallet" }
        assertEquals("Wallet", wallet.name)
        assertFalse(wallet.isEnabled)
        assertEquals("Add a card first", wallet.explanation)
    }

    @Test
    fun parseAvailableAffordancesDeduplicatesIdsAndHandlesArbitraryColumnOrder() {
        val raw = """
            Row: 0 enablement_explanation=NULL, icon=999, is_enabled=true, id=qr_code_scanner, name=QR Scanner
            Row: 1 id=qr_code_scanner, name=Duplicate QR, is_enabled=1, enablement_explanation=NULL
        """.trimIndent()

        val parsed = SystemShortcutController.parseAvailableAffordances(raw)
        assertNotNull(parsed)
        val items = requireNotNull(parsed)
        assertEquals(2, items.size)
        assertEquals("QR Scanner", items[1].name)
    }

    @Test
    fun displayLabelUsesFallbackWhenNameMatchesId() {
        val item = LockscreenAffordance(id = "do_not_disturb", name = "do_not_disturb")
        assertEquals("Do Not Disturb", item.displayLabel("Do Not Disturb"))
    }
}
