package com.wolf.websocket.message;

/**
 *
 * @author aladdin
 */
public enum OpCode {

    TEXT {
        public byte getByte() {
            return 1;
        }
    },
    CLOSING {
        public byte getByte() {
            return 8;
        }
    };

    public static OpCode getOpCode(byte b) {
        OpCode opCode;
        switch (b) {
            case 1:
                opCode = OpCode.TEXT;
                break;
            // 3-7 are not yet defined
            case 8:
                opCode = OpCode.CLOSING;
                break;
            // 11-15 are not yet defined
            default:
                opCode = null;
        }
        return opCode;
    }

    public abstract byte getByte();
}
