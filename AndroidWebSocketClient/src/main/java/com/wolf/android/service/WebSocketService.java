package com.wolf.android.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import com.wolf.websocket.AbstractWebSocketClient;
import com.wolf.websocket.WebSocketClient;

/**
 * Created by aladdin on 5/30/13.
 */
public class WebSocketService extends Service {

    public final static String SERVER_NAME = "server";
    public final static String MESSAGE_NAME = "json";
    private Context myContext = this;
    private WebSocketClient client = null;
    private WebSocketBinder webSocketBinder = new WebSocketBinder();
    private String server = null;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (this.server == null) {
            this.server = intent.getStringExtra(SERVER_NAME);
        }
        if (this.server != null) {
            this.client = new AbstractWebSocketClient(server) {

                @Override
                public void onMessage(String message) {
                    Intent intent = new Intent();
                    intent.setAction("android.intent.action.MY_RECEIVER");
                    intent.putExtra(MESSAGE_NAME, message);
                    myContext.sendBroadcast(intent);
                }

                @Override
                public void onError(Exception ex) {
                }
            };
        }
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        this.client.close();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return this.webSocketBinder;
    }

    public final class WebSocketBinder extends Binder {

        public void send(String message) {
            if (client == null) {
                Intent intent = new Intent();
                intent.setAction("android.intent.action.MY_RECEIVER");
                intent.putExtra(MESSAGE_NAME, "{\"flag\":\"CLIENT_NO_SERVER_NAME\"}");
                myContext.sendBroadcast(intent);
            } else {
                client.send(message);
            }
        }
    }
}
