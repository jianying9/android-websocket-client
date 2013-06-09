package com.wolf.websocket.handshake;

import java.util.Map;

/**
 *
 * @author aladdin
 */
public interface ServerHandshake {

    public int getHttpState();

    public String getHttpMessage();

    public Map<String, String> getHttpFieldMap();

    public String getHttpFieldValue(String fieldName);
}
