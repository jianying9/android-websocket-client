package com.wolf.websocket;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 *
 * @author aladdin
 */
public abstract class AbstractWebSocketClient implements WebSocketClient {

    private final BlockingQueue<String> sendMessageQueue;
    private final String serverUrl;
    private Thread sendThread = null;

    public AbstractWebSocketClient(String serverUrl) {
        this.sendMessageQueue = new LinkedBlockingQueue<String>(50);
        this.serverUrl = serverUrl;
    }

    @Override
    public final void send(String message) {
        synchronized (this) {
            if (sendThread == null || sendThread.isInterrupted()) {
                sendThread = new Thread(new WebSocketSendThread(this));
                sendThread.start();
            }
        }
        try {
            this.sendMessageQueue.put(message);
        } catch (InterruptedException ex) {
            this.onError(ex);
        }
    }

    @Override
    public final void close() {
        if(this.sendThread != null && this.sendThread.isAlive()) {
            this.sendThread.interrupt();
        }
    }

    private final class WebSocketSendThread implements Runnable {

        private final WebSocketClientListener listener;
        private WebSocket webSocket = null;

        public WebSocketSendThread(WebSocketClientListener listener) {
            this.listener = listener;
        }

        @Override
        public void run() {
            String message;
            try {
                while (Thread.interrupted() == false) {
                    message = sendMessageQueue.take();
                    if (this.webSocket != null && this.webSocket.isOpen()) {
                        this.webSocket.send(message);
                    } else {
                        this.webSocket = new AbstractWebSocket(serverUrl, message) {
                            @Override
                            public void onOpen() {
                                System.out.println(Thread.currentThread().getName() + " connect success!!!");
                            }

                            @Override
                            public void onMessage(String message) {
                                listener.onMessage(message);
                            }

                            @Override
                            public void onClose() {
                                System.out.println(Thread.currentThread().getName() + " closing");
                                webSocket = null;
                            }

                            @Override
                            public void onError(Exception ex) {
                                listener.onError(ex);
                            }
                        };
                        this.webSocket.connect();
                    }
                }
            } catch (InterruptedException e) {
                this.webSocket.onError(e);
            } finally {
                if(this.webSocket != null && this.webSocket.isOpen()) {
                    this.webSocket.close();
                }
                sendThread = null;
            }
        }
    }
}
