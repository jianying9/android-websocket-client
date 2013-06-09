package com.wolf.websocket;

/**
 *
 * @author aladdin
 */
public interface WebSocket {

    public int DEFAULT_PORT = 80;
    public int RCV_BUF_SIZE = 16384;

    public void connect();

    public void close();

    public void send(String message);

    public ReadyState getReadyState();

    public boolean isConnecting();

    public boolean isOpen();

    public boolean isClosing();

    public boolean isClosed();

    public void onOpen();

    public void onMessage(String message);

    public void onClose();

    public void onError(Exception ex);
}
