package com.wolf.websocket.handshake;

/**
 *
 * @author aladdin
 */
public class ClientHandshakeImpl extends AbstractHandshake implements ClientHandshake {

    private final String path;

    public ClientHandshakeImpl(String path) {
        this.path = path;
    }

    public String getPath() {
        return this.path;
    }
}
