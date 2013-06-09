package com.wolf.android.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

import com.wolf.android.service.WebSocketService;

/**
 * Created by aladdin on 6/8/13.
 */
public abstract class AbstractWebSocketActivity extends Activity{

    private MyReceiver receiver;
    private AbstractWebSocketActivity myContext = this;
    private WebSocketService.WebSocketBinder webSocketBinder;

    private ServiceConnection conn = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            webSocketBinder = (WebSocketService.WebSocketBinder) service;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.receiver = new MyReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.MY_RECEIVER");
        //注册
        this.registerReceiver(this.receiver, filter);
        Intent intent = new Intent(this, WebSocketService.class);
        String server = this.getServer();
        intent.putExtra(WebSocketService.SERVER_NAME, server);
        this.startService(intent);
        this.bindService(intent, conn, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        this.unregisterReceiver(this.receiver);
        this.unbindService(conn);
        super.onDestroy();
    }

    protected abstract void onMessage(String message);

    protected abstract String getServer();

    public final void sendMessage(String message) {
        this.webSocketBinder.send(message);
    }

    private final class MyReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getExtras();
            String message = bundle.getString(WebSocketService.MESSAGE_NAME);
            myContext.onMessage(message);
        }
    }
}
