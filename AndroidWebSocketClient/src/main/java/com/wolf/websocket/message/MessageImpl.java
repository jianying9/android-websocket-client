package com.wolf.websocket.message;

import com.wolf.websocket.util.Charsetfunctions;

import java.nio.ByteBuffer;

/**
 *
 * @author aladdin
 */
public class MessageImpl implements Message {

    private final OpCode opCode;

    private final ByteBuffer byteBuffer;

    public MessageImpl(OpCode opCode, ByteBuffer byteBuffer) {
        this.opCode = opCode;
        this.byteBuffer = byteBuffer;
    }

    public MessageImpl(OpCode opCode, String message) {
        this.opCode = opCode;
        byte[] messageByte = Charsetfunctions.utf8Bytes(message);
        this.byteBuffer = ByteBuffer.allocate(messageByte.length);
        this.byteBuffer.put(messageByte);
        this.byteBuffer.flip();
    }

    public OpCode getOpCode() {
        return this.opCode;
    }

    public String getUTF8Data() {
        return Charsetfunctions.stringUtf8(this.byteBuffer);
    }

    public ByteBuffer getBinaryData() {
        return this.byteBuffer;
    }

}
