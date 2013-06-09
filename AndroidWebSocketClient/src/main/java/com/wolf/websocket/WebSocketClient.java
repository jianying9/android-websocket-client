package com.wolf.websocket;

/**
 *
 * @author aladdin
 */
public interface WebSocketClient extends WebSocketClientListener {

    public void send(String message);

    public void close();
}
