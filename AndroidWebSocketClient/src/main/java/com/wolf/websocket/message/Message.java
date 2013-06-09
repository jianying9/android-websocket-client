package com.wolf.websocket.message;

import java.nio.ByteBuffer;

/**
 *
 * @author aladdin
 */
public interface Message {

    public OpCode getOpCode();

    public String getUTF8Data();

    public ByteBuffer getBinaryData();
}
