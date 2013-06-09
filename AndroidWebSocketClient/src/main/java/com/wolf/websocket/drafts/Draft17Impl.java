package com.wolf.websocket.drafts;

import com.wolf.websocket.WebSocket;
import com.wolf.websocket.handshake.ClientHandshake;
import com.wolf.websocket.handshake.ClientHandshakeImpl;
import com.wolf.websocket.handshake.ServerHandshake;
import com.wolf.websocket.handshake.ServerHandshakeImpl;
import com.wolf.websocket.message.Message;
import com.wolf.websocket.message.MessageImpl;
import com.wolf.websocket.message.OpCode;
import com.wolf.websocket.util.Base64;
import com.wolf.websocket.util.Charsetfunctions;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

/**
 * 17协议数据结构 1byte: --1bit:frame-fin,1表示是消息的最后一个frame,0表示消息还有后续frame
 * --2~4bit:frame-rsv,一般都为0
 * --5~8bit:frame-opcode,0表示延续frame,1表示文本frame,2表示二进制frame,3~7保留给非控制frame,8表示关闭连接,9表示ping,A表示pong,B~F保留给控制frame
 * 2byte: --1bit:mask标记,1表示frame包含掩码,0表示没有掩码 --2~8bit:
 * 7bit取整数值,如果值在0~125之间,则表示frame数据长度
 * 如果值为126,则表示frame数据长度在126~65535之间,实际长度的值存储在后续的2byte
 * 如果值为127,则表示frame数据长度大于65535,实际长度的值存储在后续的8byte
 * 如果mask标记为1，后续4byte为掩码,并且传输数据时,数据需要解码，规则为：1~4掩码循环和数据byte异或操作 ~end
 * byte：后续byte为扩展数据和应用数据byte,一般没有扩展数据byte 数据长度为2byte中存储的值
 *
 *
 * @author aladdin
 */
public class Draft17Impl implements Draft {

    private ByteBuffer incompleteFrame;
    private final Random reuseableRandom = new Random();

    @Override
    public ClientHandshake createClientHandshake(URI uri) {
        String ip = uri.getHost();
        int port = uri.getPort();
        String path;
        String part1 = uri.getPath();
        String part2 = uri.getQuery();
        if (part1 == null || part1.length() == 0) {
            path = "/";
        } else {
            path = part1;
        }
        if (part2 != null) {
            path += "?" + part2;
        }
        StringBuilder hostBuilder = new StringBuilder(20);
        hostBuilder.append(ip);
        if (port != WebSocket.DEFAULT_PORT) {
            hostBuilder.append(':').append(port);
        }
        String host = hostBuilder.toString();
        ClientHandshakeImpl clientHandshake = new ClientHandshakeImpl(path);
        clientHandshake.putHttpFiled("Host", host);
        clientHandshake.putHttpFiled("Upgrade", "websocket");
        clientHandshake.putHttpFiled("Connection", "Upgrade");
        clientHandshake.putHttpFiled("Sec-WebSocket-Version", "13");
        byte[] random = new byte[16];
        this.reuseableRandom.nextBytes(random);
        clientHandshake.putHttpFiled("Sec-WebSocket-Key", Base64.encodeBytes(random));
        return clientHandshake;
    }

    @Override
    public ByteBuffer createClientHandshakeByteBuffer(ClientHandshake clientHandshake) {
        StringBuilder bui = new StringBuilder(100);
        bui.append("GET ").append(clientHandshake.getPath()).append(" HTTP/1.1").append("\r\n");
        String fieldName;
        String fieldValue;
        Set<Entry<String, String>> fields = clientHandshake.getHttpFieldMap().entrySet();
        for (Entry<String, String> entry : fields) {
            fieldName = entry.getKey();
            fieldValue = entry.getValue();
            bui.append(fieldName).append(": ").append(fieldValue).append("\r\n");
        }
        bui.append("\r\n");
        byte[] httpHeader = Charsetfunctions.asciiBytes(bui.toString());
        ByteBuffer byteBuffer = ByteBuffer.allocate(httpHeader.length);
        byteBuffer.put(httpHeader);
        byteBuffer.flip();
        return byteBuffer;
    }

    private String readStringLine(ByteBuffer buf) {
        ByteBuffer subBuf = ByteBuffer.allocate(buf.remaining());
        byte prev;
        byte cur = 0;
        String line = null;
        while (buf.hasRemaining()) {
            prev = cur;
            cur = buf.get();
            subBuf.put(cur);
            if (prev == '\r' && cur == '\n') {
                subBuf.limit(subBuf.position() - 2);
                subBuf.position(0);
                if (subBuf.limit() > 0) {
                    line = Charsetfunctions.stringAscii(subBuf.array(), 0, subBuf.limit());
                }
                break;
            }
        }
        return line;
    }

    @Override
    public ServerHandshake parseServerHandshake(ByteBuffer byteBuffer) {
        ServerHandshake serverHandshake = null;
        String firstLine = this.readStringLine(byteBuffer);
        if (firstLine != null) {
            String[] firstLineTokens = firstLine.split(" ", 3);
            if (firstLineTokens.length == 3) {
                int httpState = Integer.parseInt(firstLineTokens[1]);
                String httpMessage = firstLineTokens[2];
                ServerHandshakeImpl serverHandshakeImpl = new ServerHandshakeImpl(httpState, httpMessage);
                String line = this.readStringLine(byteBuffer);
                String[] fieldTokens;
                while (line != null && line.length() > 0) {
                    fieldTokens = line.split(":");
                    if (fieldTokens.length == 2) {
                        serverHandshakeImpl.putHttpFiled(fieldTokens[0], fieldTokens[1].trim());
                    }
                    line = this.readStringLine(byteBuffer);
                }
                serverHandshake = serverHandshakeImpl;
            }
        }
        return serverHandshake;
    }

    @Override
    public boolean validate(ClientHandshake clientHandshake, ServerHandshake serverHandshake) {
        boolean result = false;
        if (clientHandshake != null && serverHandshake != null) {
            String secKeyServer = serverHandshake.getHttpFieldValue("Sec-WebSocket-Accept");
            String secKeyClient = clientHandshake.getHttpFieldValue("Sec-WebSocket-Key");
            if (secKeyClient != null && secKeyServer != null) {
                MessageDigest sh1;
                try {
                    sh1 = MessageDigest.getInstance("SHA1");
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
                String keyText = secKeyClient + Draft.WEB_SOCKET_GUID;
                String entryKeyText = Base64.encodeBytes(sh1.digest(keyText.getBytes()));
                if (secKeyServer.equals(entryKeyText)) {
                    result = true;
                }
            }
        }
        return result;
    }

    private Message parseSingleFrame(ByteBuffer byteBuffer) {
        Message message = null;
        //判断是否是可读的frame,frame-rsv值为0
        byte oneByte = byteBuffer.get();
        boolean isFin = (oneByte & -128) != 0;
        boolean isCorrectRsv = (oneByte & 112) == 0;
        //获取frame opCode
        byte opCodeByte = (byte) (oneByte & 15);
        OpCode opCode = OpCode.getOpCode(opCodeByte);
        //获取数据长度
        byte twoByte = byteBuffer.get();
        int byteSize = twoByte & 127;
        int realByteSize = 0;
        if (byteSize <= 125) {
            //实际数据长度小等于125
            realByteSize = byteSize;
        } else if (byteSize == 126) {
            //实际数据长度在大于125且小等于65535,需要额外2byte存储数据长度
            byte[] sizeBytes = new byte[2];
            sizeBytes[0] = byteBuffer.get();
            sizeBytes[1] = byteBuffer.get();
            realByteSize = new BigInteger(sizeBytes).intValue();
        } else if (byteSize == 127) {
            //实际数据长度在大于65535,需要额外8byte存储数据长度
            byte[] sizeBytes = new byte[8];
            for (int index = 0; index < 8; index++) {
                sizeBytes[index] = byteBuffer.get();
            }
            realByteSize = new BigInteger(sizeBytes).intValue();
        }
        //获取掩码
        boolean isMask = (twoByte & -128) != 0;
        //开始处理数据
        if (isFin && isCorrectRsv && opCode != null) {
            //可读的frame
            ByteBuffer signByteBuffer = ByteBuffer.allocate(realByteSize);
            if (isMask) {
                //有掩码，数据需要解码
                byte[] maskKeys = new byte[4];
                byteBuffer.get(maskKeys);
                byte realByte;
                for (int index = 0; index < realByteSize; index++) {
                    realByte = byteBuffer.get();
                    realByte ^= maskKeys[index % 4];
                    signByteBuffer.put(realByte);
                }
            } else {
                //无掩码，直接读取
                for (int index = 0; index < realByteSize; index++) {
                    signByteBuffer.put(byteBuffer.get());
                }
            }
            //构造消息
            signByteBuffer.flip();
            message = new MessageImpl(opCode, signByteBuffer);
        } else {
            //丢弃当前signle frame
            int newPosition = byteBuffer.position() + realByteSize;
            if (isMask) {
                newPosition += 4;
            }
            byteBuffer.position(newPosition);
        }
        return message;
    }

    private boolean hasCompleteSingleFrame(ByteBuffer byteBuffer) {
        boolean result = false;
        int maxPacketSize = byteBuffer.remaining();
        if (maxPacketSize > 2) {
            //标记byteBuffer的初始位置
            byteBuffer.mark();
            //最小长度2byte
            int realPacketSize = 0;
            //one byte和长度无关
            byteBuffer.get();
            realPacketSize++;
            //tow byte和长度有关
            byte twoByte = byteBuffer.get();
            realPacketSize++;
            //获取数据长度
            int byteSize = twoByte & 127;
            int realByteSize = 0;
            if (byteSize <= 125) {
                //实际数据长度小等于125
                realByteSize = byteSize;
            } else if (byteSize == 126) {
                //实际数据长度在大于125且小等于65535,需要额外2byte存储数据长度
                realPacketSize += 2;
                byte[] sizeBytes = new byte[2];
                sizeBytes[0] = byteBuffer.get();
                sizeBytes[1] = byteBuffer.get();
                realByteSize = new BigInteger(sizeBytes).intValue();
            } else if (byteSize == 127) {
                //实际数据长度在大于65535,需要额外8byte存储数据长度
                realPacketSize += 8;
                byte[] sizeBytes = new byte[8];
                for (int index = 0; index < 8; index++) {
                    sizeBytes[index] = byteBuffer.get();
                }
                realByteSize = new BigInteger(sizeBytes).intValue();
            }
            realPacketSize += realByteSize;
            //获取掩码
            boolean isMask = (twoByte & -128) != 0;
            if (isMask) {
                realPacketSize += 4;
            }
            //如果byteBuffer的长度大等于第一个frame的数据长度,则可以读取
            if (maxPacketSize >= realPacketSize) {
                result = true;
            }
            byteBuffer.reset();
        }
        return result;
    }

    @Override
    public List<Message> parseFrame(ByteBuffer byteBuffer) {
        List<Message> messageList;
        if (this.incompleteFrame == null) {
            //没有不完整的输入数据
            messageList = new ArrayList<Message>();
            Message message;
            while (byteBuffer.hasRemaining()) {
                //判断是否有完整的frame可以读取
                if (this.hasCompleteSingleFrame(byteBuffer)) {
                    message = this.parseSingleFrame(byteBuffer);
                    if (message != null) {
                        //有效的消息
                        messageList.add(message);
                    }
                } else {
                    //消息不完整，缓存至incompleteFrame
                    int size = byteBuffer.remaining();
                    this.incompleteFrame = ByteBuffer.allocate(size);
                    this.incompleteFrame.put(byteBuffer.array(), byteBuffer.position(), size);
                    this.incompleteFrame.flip();
                    break;
                }
            }
        } else {
            //有不完整的输入数据,先合并数据
            int size = this.incompleteFrame.remaining() + byteBuffer.remaining();
            ByteBuffer newByteBuffer = ByteBuffer.allocate(size);
            newByteBuffer.put(this.incompleteFrame.array(), this.incompleteFrame.position(), this.incompleteFrame.remaining());
            this.incompleteFrame = null;
            newByteBuffer.put(byteBuffer.array(), byteBuffer.position(), byteBuffer.remaining());
            newByteBuffer.flip();
            messageList = this.parseFrame(newByteBuffer);
        }
        return messageList;
    }

    /**
     * 数值转成byte[]
     *
     * @param length
     * @param byteSize
     * @return
     */
    private byte[] lengthToByte(long length, int byteSize) {
        byte[] buffer = new byte[byteSize];
        int highest = 8 * byteSize - 8;
        for (int i = 0; i < byteSize; i++) {
            buffer[i] = (byte) (length >>> (highest - 8 * i));
        }
        return buffer;
    }

    @Override
    public ByteBuffer createFrame(Message message) {
        OpCode opCode = message.getOpCode();
        ByteBuffer messageData = message.getBinaryData();
        //获取数据长度,判断需要多少字节存储长度值
        int byteSize;
        int dataLength = messageData.remaining();
        if (dataLength <= 125) {
            //数据长度小等于125，用1字节存储
            byteSize = 1;
        } else if (dataLength <= 65535) {
            //数据长度小等于65535，用2字节存储
            byteSize = 2;
        } else {
            //数据长度大于65535，用8字节存储
            byteSize = 8;
        }
        //构造完整的frame,有掩码
        ByteBuffer buf = ByteBuffer.allocate(1 + (byteSize > 1 ? byteSize + 1 : byteSize) + 4 + dataLength);
        //构造完整的frame
        byte code = opCode.getByte();
        byte oneByte = (byte) (-128 | code);
        buf.put(oneByte);
        //mask为1
        byte towByte;
        byte[] lengthByte;
        switch (byteSize) {
            case 1:
                towByte = (byte) (-128 | dataLength);
                buf.put(towByte);
                break;
            case 2:
                towByte = (byte) (-128 | 126);
                buf.put(towByte);
                lengthByte = this.lengthToByte(dataLength, byteSize);
                buf.put(lengthByte);
                break;
            case 8:
                towByte = (byte) (-128 | 127);
                buf.put(towByte);
                lengthByte = this.lengthToByte(dataLength, byteSize);
                buf.put(lengthByte);
                break;
        }
        //随机4byte掩码
        byte[] maskKeyByte = new byte[4];
        this.reuseableRandom.nextBytes(maskKeyByte);
        buf.put(maskKeyByte);
        //用掩码对数据进行解码
        byte dataByte;
        byte maskDataByte;
        for (int index = 0; index < dataLength; index++) {
            dataByte = messageData.get();
            maskDataByte = (byte) (maskKeyByte[index % 4] ^ dataByte);
            buf.put(maskDataByte);
        }
        //
        buf.flip();
        return buf;
    }
}
