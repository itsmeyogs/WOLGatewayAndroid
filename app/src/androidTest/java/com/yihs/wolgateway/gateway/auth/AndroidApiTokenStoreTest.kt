package com.yihs.wolgateway.gateway.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidApiTokenStoreTest {
    private lateinit var store: AndroidApiTokenStore

    @Before
    fun setUp() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("gateway_secure_storage", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = AndroidApiTokenStore(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun generatedAndCustomTokensRoundTripThroughKeystore() {
        val generated = store.createNew()
        assertEquals(generated, store.read())

        val custom = "custom-token-with-at-least-32-characters"
        assertEquals(custom, store.replace(custom))
        assertEquals(custom, store.read())
        assertNotNull(store.read())

        store.revoke()
        assertNull(store.read())
    }
}
