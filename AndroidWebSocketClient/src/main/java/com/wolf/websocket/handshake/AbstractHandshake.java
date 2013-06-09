package com.wolf.websocket.handshake;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author aladdin
 */
public abstract class AbstractHandshake {

    private final Map<String, String> filedMap = new HashMap<String, String>(8, 1);

    public final Map<String, String> getHttpFieldMap() {
        return this.filedMap;
    }

    public final String getHttpFieldValue(String fieldName) {
        return this.filedMap.get(fieldName);
    }

    public final void putHttpFiled(String fieldName, String filedValue) {
        this.filedMap.put(fieldName, filedValue);
    }
}
