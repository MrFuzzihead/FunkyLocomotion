package com.rwtema.funkylocomotion.network;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class MessageClearTileTest {

    @Test
    public void roundTripsCoordinatesIncludingNegativeAndLargeValues() {
        MessageClearTile message = new MessageClearTile(-12345, -64, 300000);

        ByteBuf buffer = Unpooled.buffer();
        try {
            message.toBytes(buffer);
            MessageClearTile decoded = new MessageClearTile();
            decoded.fromBytes(buffer);

            assertEquals(-12345, decoded.x);
            assertEquals(-64, decoded.y);
            assertEquals(300000, decoded.z);
        } finally {
            buffer.release();
        }
    }
}
