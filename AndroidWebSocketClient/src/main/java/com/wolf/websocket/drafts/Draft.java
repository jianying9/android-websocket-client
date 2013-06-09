package com.wolf.websocket.drafts;

import com.wolf.websocket.handshake.ClientHandshake;
import com.wolf.websocket.handshake.ServerHandshake;
import com.wolf.websocket.message.Message;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;

/**
 *
 * @author aladdin
 */
public interface Draft {

    public String WEB_SOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    public ClientHandshake createClientHandshake(URI uri);

    public ByteBuffer createClientHandshakeByteBuffer(ClientHandshake clientHandshake);

    public ServerHandshake parseServerHandshake(ByteBuffer byteBuffer);

    public boolean validate(ClientHandshake clientHandshake, ServerHandshake serverHandshake);

    public List<Message> parseFrame(ByteBuffer byteBuffer);

    public ByteBuffer createFrame(Message message);
}
