package com.wolf.websocket.handshake;

import java.util.Map;

/**
 *
 * @author aladdin
 */
public interface ClientHandshake {

    public String getPath();

    public Map<String, String> getHttpFieldMap();

    public String getHttpFieldValue(String fieldName);
}
