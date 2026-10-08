package dev.cniekirk.wikidroid.core.ui

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.testing.RobolectricTest
import org.junit.Test

class WebLinksTest : RobolectricTest() {
    @Test
    fun webAddressesAreOpened() {
        assertThat(isWebUrl("https://minecraft.wiki/w/Diamond")).isTrue()
        assertThat(isWebUrl("http://example.com")).isTrue()
        assertThat(isWebUrl("HTTPS://EXAMPLE.COM")).isTrue()
    }

    @Test
    fun otherSchemesAreNot() {
        assertThat(isWebUrl("javascript:alert(1)")).isFalse()
        assertThat(isWebUrl("intent://scan/#Intent;scheme=zxing;end")).isFalse()
        assertThat(isWebUrl("file:///sdcard/x")).isFalse()
        assertThat(isWebUrl("mailto:a@b.c")).isFalse()
        assertThat(isWebUrl("/w/Diamond")).isFalse()
        assertThat(isWebUrl("")).isFalse()
    }
}
