package com.zrlog.admin.web.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ByteUtilsTest {

    @Test
    public void shouldConvertBytesToUppercaseHexString() {
        assertEquals(ByteUtils.class, new ByteUtils().getClass());
        assertEquals(
                "000F10FF", ByteUtils.bytesToHexString(new byte[] {0x00, 0x0F, 0x10, (byte) 0xFF}));
    }

    @Test
    public void shouldConvertUppercaseAndLowercaseHexToBytes() {
        assertArrayEquals(
                new byte[] {0x00, 0x0F, 0x10, (byte) 0xFF}, ByteUtils.hexString2Bytes("000F10FF"));
        assertArrayEquals(new byte[] {0x0A, 0x0B, 0x0C}, ByteUtils.hexString2Bytes("0a0b0c"));
    }

    @Test
    public void shouldRoundTripHexEncoding() {
        byte[] bytes = new byte[] {1, 2, 3, 10, 15, 16, 127, -128, -1};

        assertArrayEquals(bytes, ByteUtils.hexString2Bytes(ByteUtils.bytesToHexString(bytes)));
    }
}
