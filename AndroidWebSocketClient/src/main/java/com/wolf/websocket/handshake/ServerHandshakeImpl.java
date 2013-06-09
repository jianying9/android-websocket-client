package com.wolf.websocket.handshake;

/**
 *
 * @author aladdin
 */
public class ServerHandshakeImpl extends AbstractHandshake implements ServerHandshake {

    private final int state;
    private final String message;

    public ServerHandshakeImpl(int state, String message) {
        this.state = state;
        this.message = message;
    }

    public int getHttpState() {
        return this.state;
    }

    public String getHttpMessage() {
        return this.message;
    }
}
