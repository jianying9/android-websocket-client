package com.wolf.websocket;

public interface WebSocketClientListener {

    public void onMessage(String message);

    public void onError(Exception ex);
}
