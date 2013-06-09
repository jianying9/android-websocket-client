package com.wolf.websocket;

import com.wolf.websocket.drafts.Draft;
import com.wolf.websocket.drafts.Draft17Impl;
import com.wolf.websocket.handshake.ClientHandshake;
import com.wolf.websocket.handshake.ServerHandshake;
import com.wolf.websocket.message.Message;
import com.wolf.websocket.message.MessageImpl;
import com.wolf.websocket.message.OpCode;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.List;

/**
 *
 * @author aladdin
 */
public abstract class AbstractWebSocket implements WebSocket {

    public SocketChannel channel;
    private volatile ReadyState readystate = ReadyState.CLOSED;
    private final Draft draft = new Draft17Impl();
    private final URI uri;
    private Thread channelThread = null;
    private String message;

    public AbstractWebSocket(String serverUrl, String message) {
        this.uri = URI.create(serverUrl);
        this.message = message;
    }

    public AbstractWebSocket(String serverUrl) {
        this.uri = URI.create(serverUrl);
        this.message = "";
    }

    @Override
    public final synchronized void close() {
        if (readystate != ReadyState.CLOSING && readystate != ReadyState.CLOSED) {
            this.readystate = ReadyState.CLOSING;
            this.channelThread = null;
            if (this.channel != null && this.channel.isOpen()) {
                try {
                    this.channel.close();
                } catch (IOException ex) {
                }
            }
            this.readystate = ReadyState.CLOSED;
            this.onClose();
        }
    }

    @Override
    public final synchronized void send(String message) {
        if (this.readystate == ReadyState.OPEN && message.isEmpty() == false) {
            Message msg = new MessageImpl(OpCode.TEXT, message);
            ByteBuffer buf = this.draft.createFrame(msg);
            try {
                while (buf.hasRemaining()) {
                    this.channel.write(buf);
                }
            } catch (IOException e) {
                this.onError(e);
                this.close();
            }
        }
    }

    @Override
    public final void connect() {
        if (this.readystate == ReadyState.CLOSED) {
            System.out.println(Thread.currentThread().getName() + " start handshake");
            ClientHandshake clientHandshake = this.draft.createClientHandshake(this.uri);
            ByteBuffer clientHandshakeByteBuffer = this.draft.createClientHandshakeByteBuffer(clientHandshake);
            ByteBuffer buff = ByteBuffer.allocate(WebSocket.RCV_BUF_SIZE);
            try {
                this.channel = SelectorProvider.provider().openSocketChannel();
                this.channel.configureBlocking(true);
                int port = this.uri.getPort();
                if (port == -1) {
                    port = WebSocket.DEFAULT_PORT;
                }
                this.channel.connect(new InetSocketAddress(this.uri.getHost(), port));
                this.readystate = ReadyState.CONNECTING;
                this.channel.write(clientHandshakeByteBuffer);
                int read;
                boolean result;
                ServerHandshake serverHandshake;
                while (this.channel.isOpen()) {
                    buff.clear();
                    read = this.channel.read(buff);
                    buff.flip();
                    if (read > 0) {
                        serverHandshake = this.draft.parseServerHandshake(buff);
                        result = this.draft.validate(clientHandshake, serverHandshake);
                        if (result) {
                            this.readystate = ReadyState.OPEN;
                            if (this.channelThread == null) {
                                this.channelThread = new Thread(new WebSocketIOThread(this));
                                this.channelThread.start();
                            }
                            this.onOpen();
                            //同步发送消息
                            this.send(this.message);
                            this.message = "";
                        } else {
                            this.close();
                        }
                        break;
                    }
                }
            } catch (IOException ex) {
                this.onError(ex);
                this.close();
            }
        }
    }

    @Override
    public final ReadyState getReadyState() {
        return this.readystate;
    }

    @Override
    public final boolean isConnecting() {
        return this.readystate == ReadyState.CONNECTING;
    }

    @Override
    public final boolean isOpen() {
        return this.readystate == ReadyState.OPEN;
    }

    @Override
    public final boolean isClosing() {
        return this.readystate == ReadyState.CLOSING;
    }

    @Override
    public final boolean isClosed() {
        return this.readystate == ReadyState.CLOSED;
    }

    private final class WebSocketIOThread implements Runnable {

        private final AbstractWebSocket webSocket;

        public WebSocketIOThread(AbstractWebSocket webSocket) {
            this.webSocket = webSocket;
        }

        @Override
        public void run() {
            ByteBuffer buff = ByteBuffer.allocate(WebSocket.RCV_BUF_SIZE);
            int read;
            List<Message> serverMessageList;
            try {
                while (Thread.interrupted() == false && this.webSocket.channel.isOpen()) {
                    buff.clear();
                    read = this.webSocket.channel.read(buff);
                    buff.flip();
                    if (read > 0) {
                        serverMessageList = this.webSocket.draft.parseFrame(buff);
                        if (serverMessageList.isEmpty() == false) {
                            for (Message message : serverMessageList) {
                                switch (message.getOpCode()) {
                                    case TEXT:
                                        this.webSocket.onMessage(message.getUTF8Data());
                                        break;
                                    case CLOSING:
                                        this.webSocket.close();
                                        break;
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                this.webSocket.onError(e);
            } finally {
                this.webSocket.close();
            }
        }
    }
}
