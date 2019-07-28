/*
 * Copyright 2013 - 2019 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import org.traccar.BaseProtocolDecoder;
import org.traccar.Context;
import org.traccar.DeviceSession;
import org.traccar.NetworkMessage;
import org.traccar.Protocol;
import org.traccar.helper.BitUtil;
import org.traccar.helper.Checksum;
import org.traccar.helper.UnitsConverter;
import org.traccar.model.CellTower;
import org.traccar.model.Network;
import org.traccar.model.Position;

import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.AbstractMap;

public class TeltonikaProtocolDecoder extends BaseProtocolDecoder {

    static final String PREFIX_DI = "di";
    static final String KEY_AXIS_X = "axisX";
    static final String KEY_AXIS_Y = "axisY";
    static final String KEY_AXIS_Z = "axisZ";
    static final String KEY_WORK_MODE = "workMode";
    private final String PREFIX_DRIVER = "driver";
    private static final String KEY_USB_CONNECTED = "usbConnected";
    private static final String KEY_UPTIME = "uptime";
    private static final String KEY_BUTTON = "button";
    private static final String KEY_PRIORITY = "priority";

    private static final int IMAGE_PACKET_MAX = 2048;
    private static final int GNSS_IN_SLEEP_STATE = 3;
    private static final int SLEEP_GPS = 1;
    private static final int SLEEP_DEEP = 2;

    private boolean connectionless;
    private boolean extended;
    private boolean parseFMB9xx;
    private Map<Long, ByteBuf> photos = new HashMap<>();
    private TeltonikaFMB9XXEventParser fmb9xxParser;

    public void setExtended(boolean extended) {
        this.extended = extended;
    }

    public TeltonikaProtocolDecoder(Protocol protocol, boolean connectionless) {
        super(protocol);
        this.connectionless = connectionless;
        this.extended = Context.getConfig().getBoolean(getProtocolName() + ".extended");
        this.parseFMB9xx = Context.getConfig().getBoolean(getProtocolName() + ".fmb9xx");
        this.fmb9xxParser = new TeltonikaFMB9XXEventParser();
    }

    private void parseIdentification(Channel channel, SocketAddress remoteAddress, ByteBuf buf) {

        int length = buf.readUnsignedShort();
        String imei = buf.toString(buf.readerIndex(), length, StandardCharsets.US_ASCII);
        DeviceSession deviceSession = getDeviceSession(channel, remoteAddress, imei);

        if (channel != null) {
            ByteBuf response = Unpooled.buffer(1);
            if (deviceSession != null) {
                response.writeByte(1);
            } else {
                response.writeByte(0);
            }
            channel.writeAndFlush(new NetworkMessage(response, remoteAddress));
        }
    }

    public static final int CODEC_GH3000 = 0x07;
    public static final int CODEC_8 = 0x08;
    public static final int CODEC_8_EXT = 0x8E;
    public static final int CODEC_12 = 0x0C;
    public static final int CODEC_16 = 0x10;

    private void sendImageRequest(Channel channel, SocketAddress remoteAddress, long id, int offset, int size) {
        if (channel != null) {
            ByteBuf response = Unpooled.buffer();
            response.writeInt(0);
            response.writeShort(0);
            response.writeShort(19); // length
            response.writeByte(CODEC_12);
            response.writeByte(1); // nod
            response.writeByte(0x0D); // camera
            response.writeInt(11); // payload length
            response.writeByte(2); // command
            response.writeInt((int) id);
            response.writeInt(offset);
            response.writeShort(size);
            response.writeByte(1); // nod
            response.writeShort(0);
            response.writeShort(Checksum.crc16(
                    Checksum.CRC16_IBM, response.nioBuffer(8, response.readableBytes() - 10)));
            channel.writeAndFlush(new NetworkMessage(response, remoteAddress));
        }
    }

    private void decodeSerial(Channel channel, SocketAddress remoteAddress, Position position, ByteBuf buf) {

        getLastLocation(position, null);

        int type = buf.readUnsignedByte();
        if (type == 0x0D) {

            buf.readInt(); // length
            int subtype = buf.readUnsignedByte();
            if (subtype == 0x01) {

                long photoId = buf.readUnsignedInt();
                ByteBuf photo = Unpooled.buffer(buf.readInt());
                photos.put(photoId, photo);
                sendImageRequest(
                        channel, remoteAddress, photoId,
                        0, Math.min(IMAGE_PACKET_MAX, photo.capacity()));

            } else if (subtype == 0x02) {

                long photoId = buf.readUnsignedInt();
                buf.readInt(); // offset
                ByteBuf photo = photos.get(photoId);
                photo.writeBytes(buf, buf.readUnsignedShort());
                if (photo.writableBytes() > 0) {
                    sendImageRequest(
                            channel, remoteAddress, photoId,
                            photo.writerIndex(), Math.min(IMAGE_PACKET_MAX, photo.writableBytes()));
                } else {
                    String uniqueId = Context.getIdentityManager().getById(position.getDeviceId()).getUniqueId();
                    photos.remove(photoId);
                    try {
                        position.set(Position.KEY_IMAGE, Context.getMediaManager().writeFile(uniqueId, photo, "jpg"));
                    } finally {
                        photo.release();
                    }
                }

            }

        } else {

            position.set(Position.KEY_TYPE, type);

            int length = buf.readInt();
            boolean readable = true;
            for (int i = 0; i < length; i++) {
                byte b = buf.getByte(buf.readerIndex() + i);
                if (b < 32 && b != '\r' && b != '\n') {
                    readable = false;
                    break;
                }
            }

            if (readable) {
                position.set(Position.KEY_RESULT, buf.readSlice(length).toString(StandardCharsets.US_ASCII));
            } else {
                position.set(Position.KEY_RESULT, ByteBufUtil.hexDump(buf.readSlice(length)));
            }
        }
    }

    private long readValue(ByteBuf buf, int length, boolean signed) {
        switch (length) {
            case 1:
                return signed ? buf.readByte() : buf.readUnsignedByte();
            case 2:
                return signed ? buf.readShort() : buf.readUnsignedShort();
            case 4:
                return signed ? buf.readInt() : buf.readUnsignedInt();
            default:
                return buf.readLong();
        }
    }

    private void decodeOtherParameter(Position position, int id, ByteBuf buf, int length) {
        switch (id) {
            case 1:
            case 2:
            case 3:
            case 4:
                position.set(PREFIX_DI + id, readValue(buf, length, false));
                break;
            case 9:
                position.set(Position.PREFIX_ADC + 1, readValue(buf, length, false));
                break;
            case 17:
                position.set(KEY_AXIS_X, readValue(buf, length, true));
                break;
            case 18:
                position.set(KEY_AXIS_Y, readValue(buf, length, true));
                break;
            case 19:
                position.set(KEY_AXIS_Z, readValue(buf, length, true));
                break;
            case 21:
                position.set(Position.KEY_RSSI, readValue(buf, length, false));
                break;
            case 25:
            case 26:
            case 27:
            case 28:
                position.set(Position.PREFIX_TEMP + (id - 24), readValue(buf, length, true) * 0.1);
                break;
            case 66:
                position.set(Position.KEY_POWER, readValue(buf, length, false) * 0.001);
                break;
            case 67:
                position.set(Position.KEY_BATTERY, readValue(buf, length, false) * 0.001);
                break;
            case 69:
                position.set(Position.KEY_GPS_STATUS, readValue(buf, length, false));
                break;
            case 72:
            case 73:
            case 74:
                position.set(Position.PREFIX_TEMP + (id - 71), readValue(buf, length, true) * 0.1);
                break;
            case 78:
                long driverUniqueId = readValue(buf, length, false);
                if (driverUniqueId != 0) {
                    position.set(Position.KEY_DRIVER_UNIQUE_ID, String.format("%016X", driverUniqueId));
                }
                break;
            case 80:
                position.set(KEY_WORK_MODE, readValue(buf, length, false));
                break;
            case 129:
            case 130:
            case 131:
            case 132:
            case 133:
            case 134:
                String driver = id == 129 || id == 132 ? "" : position.getString(PREFIX_DRIVER+"1");
                position.set(PREFIX_DRIVER + (id >= 132 ? 2 : 1),
                        driver + buf.readSlice(length).toString(StandardCharsets.US_ASCII).trim());
                break;
            case 179:
                position.set(Position.PREFIX_OUT + 1, readValue(buf, length, false) == 1);
                break;
            case 180:
                position.set(Position.PREFIX_OUT + 2, readValue(buf, length, false) == 1);
                break;
            case 181:
                position.set(Position.KEY_PDOP, readValue(buf, length, false) * 0.1);
                break;
            case 182:
                position.set(Position.KEY_HDOP, readValue(buf, length, false) * 0.1);
                break;
            case 236:
                if (readValue(buf, length, false) == 1) {
                    position.set(Position.KEY_ALARM, Position.ALARM_OVERSPEED);
                }
                break;
            case 237:
                position.set(Position.KEY_MOTION, readValue(buf, length, false) == 0);
                break;
            case 238:
                switch ((int) readValue(buf, length, false)) {
                    case 1:
                        position.set(Position.KEY_ALARM, Position.ALARM_ACCELERATION);
                        break;
                    case 2:
                        position.set(Position.KEY_ALARM, Position.ALARM_BRAKING);
                        break;
                    case 3:
                        position.set(Position.KEY_ALARM, Position.ALARM_CORNERING);
                        break;
                    default:
                        break;
                }
                break;
            case 239:
                position.set(Position.KEY_IGNITION, readValue(buf, length, false) == 1);
                break;
            case 240:
                position.set(Position.KEY_MOTION, readValue(buf, length, false) == 1);
                break;
            case 241:
                position.set(Position.KEY_OPERATOR, readValue(buf, length, false));
                break;
            default:
                position.set(Position.PREFIX_IO + id, readValue(buf, length, false));
                break;
        }
    }

    private void decodeGh3000Parameter(Position position, int id, ByteBuf buf, int length) {
        switch (id) {
            case 1:
                position.set(Position.KEY_BATTERY_LEVEL, readValue(buf, length, false));
                break;
            case 2:
                position.set(KEY_USB_CONNECTED, readValue(buf, length, false) == 1);
                break;
            case 5:
                position.set(KEY_UPTIME, readValue(buf, length, false));
                break;
            case 20:
                position.set(Position.KEY_HDOP, readValue(buf, length, false) * 0.1);
                break;
            case 21:
                position.set(Position.KEY_VDOP, readValue(buf, length, false) * 0.1);
                break;
            case 22:
                position.set(Position.KEY_PDOP, readValue(buf, length, false) * 0.1);
                break;
            case 67:
                position.set(Position.KEY_BATTERY, readValue(buf, length, false) * 0.001);
                break;
            case 221:
                position.set(KEY_BUTTON, readValue(buf, length, false));
                break;
            case 222:
                if (readValue(buf, length, false) == 1) {
                    position.set(Position.KEY_ALARM, Position.ALARM_SOS);
                }
                break;
            case 240:
                position.set(Position.KEY_MOTION, readValue(buf, length, false) == 1);
                break;
            case 244:
                position.set(Position.KEY_ROAMING, readValue(buf, length, false) == 1);
                break;
            default:
                position.set(Position.PREFIX_IO + id, readValue(buf, length, false));
                break;
        }
    }

    private void decodeParameter(Position position, int id, ByteBuf buf, int length, int codec) {
        if (codec == CODEC_GH3000) {
            decodeGh3000Parameter(position, id, buf, length);
        } else {
            decodeOtherParameter(position, id, buf, length);
        }
    }

    private void decodeNetwork(Position position) {
        long cid = position.getLong(Position.PREFIX_IO + 205);
        int lac = position.getInteger(Position.PREFIX_IO + 206);
        if (cid != 0 && lac != 0) {
            CellTower cellTower = CellTower.fromLacCid(lac, cid);
            long operator = position.getInteger(Position.KEY_OPERATOR);
            if (operator != 0) {
                cellTower.setOperator(operator);
            }
            position.setNetwork(new Network(cellTower));
        }
    }

    private int readExtByte(ByteBuf buf, int codec, int... codecs) {
        boolean ext = false;
        for (int c : codecs) {
            if (codec == c) {
                ext = true;
                break;
            }
        }
        if (ext) {
            return buf.readUnsignedShort();
        } else {
            return buf.readUnsignedByte();
        }
    }

    private void decodeLocation(Position position, ByteBuf buf, int codec) {

        int globalMask = 0x0f;

        if (codec == CODEC_GH3000) {

            long time = buf.readUnsignedInt() & 0x3fffffff;
            time += 1167609600; // 2007-01-01 00:00:00

            globalMask = buf.readUnsignedByte();
            if (BitUtil.check(globalMask, 0)) {

                position.setTime(new Date(time * 1000));

                int locationMask = buf.readUnsignedByte();

                if (BitUtil.check(locationMask, 0)) {
                    position.setLatitude(buf.readFloat());
                    position.setLongitude(buf.readFloat());
                }

                if (BitUtil.check(locationMask, 1)) {
                    position.setAltitude(buf.readUnsignedShort());
                }

                if (BitUtil.check(locationMask, 2)) {
                    position.setCourse(buf.readUnsignedByte() * 360.0 / 256);
                }

                if (BitUtil.check(locationMask, 3)) {
                    position.setSpeed(UnitsConverter.knotsFromKph(buf.readUnsignedByte()));
                }

                if (BitUtil.check(locationMask, 4)) {
                    position.set(Position.KEY_SATELLITES, buf.readUnsignedByte());
                }

                if (BitUtil.check(locationMask, 5)) {
                    CellTower cellTower = CellTower.fromLacCid(buf.readUnsignedShort(), buf.readUnsignedShort());

                    if (BitUtil.check(locationMask, 6)) {
                        cellTower.setSignalStrength((int) buf.readUnsignedByte());
                    }

                    if (BitUtil.check(locationMask, 7)) {
                        cellTower.setOperator(buf.readUnsignedInt());
                    }

                    position.setNetwork(new Network(cellTower));

                } else {
                    if (BitUtil.check(locationMask, 6)) {
                        position.set(Position.KEY_RSSI, buf.readUnsignedByte());
                    }
                    if (BitUtil.check(locationMask, 7)) {
                        position.set(Position.KEY_OPERATOR, buf.readUnsignedInt());
                    }
                }

            } else {

                getLastLocation(position, new Date(time * 1000));

            }

        } else {

            position.setTime(new Date(buf.readLong()));

            position.set(KEY_PRIORITY, buf.readUnsignedByte());

            position.setLongitude(buf.readInt() / 10000000.0);
            position.setLatitude(buf.readInt() / 10000000.0);
            position.setAltitude(buf.readShort());
            position.setCourse(buf.readUnsignedShort());

            int satellites = buf.readUnsignedByte();
            position.set(Position.KEY_SATELLITES, satellites);

            position.setValid(satellites != 0);

            position.setSpeed(UnitsConverter.knotsFromKph(buf.readUnsignedShort()));

            position.set(Position.KEY_EVENT, readExtByte(buf, codec, CODEC_8_EXT, CODEC_16));
            if (codec == CODEC_16) {
                buf.readUnsignedByte(); // generation type
            }

            readExtByte(buf, codec, CODEC_8_EXT); // total IO data records

        }

        // Read 1 byte data
        if (BitUtil.check(globalMask, 1)) {
            int cnt = readExtByte(buf, codec, CODEC_8_EXT);
            for (int j = 0; j < cnt; j++) {
                decodeParameter(position, readExtByte(buf, codec, CODEC_8_EXT, CODEC_16), buf, 1, codec);
            }
        }

        // Read 2 byte data
        if (BitUtil.check(globalMask, 2)) {
            int cnt = readExtByte(buf, codec, CODEC_8_EXT);
            for (int j = 0; j < cnt; j++) {
                decodeParameter(position, readExtByte(buf, codec, CODEC_8_EXT, CODEC_16), buf, 2, codec);
            }
        }

        // Read 4 byte data
        if (BitUtil.check(globalMask, 3)) {
            int cnt = readExtByte(buf, codec, CODEC_8_EXT);
            for (int j = 0; j < cnt; j++) {
                decodeParameter(position, readExtByte(buf, codec, CODEC_8_EXT, CODEC_16), buf, 4, codec);
            }
        }

        // Read 8 byte data
        if (codec == CODEC_8 || codec == CODEC_8_EXT || codec == CODEC_16) {
            int cnt = readExtByte(buf, codec, CODEC_8_EXT);
            for (int j = 0; j < cnt; j++) {
                decodeOtherParameter(position, readExtByte(buf, codec, CODEC_8_EXT, CODEC_16), buf, 8);
            }
        }

        // Read 16 byte data
        if (extended) {
            int cnt = readExtByte(buf, codec, CODEC_8_EXT);
            for (int j = 0; j < cnt; j++) {
                int id = readExtByte(buf, codec, CODEC_8_EXT, CODEC_16);
                position.set(Position.PREFIX_IO + id, ByteBufUtil.hexDump(buf.readSlice(16)));
            }
        }

        // Read X byte data
        if (codec == CODEC_8_EXT) {
            int cnt = buf.readUnsignedShort();
            for (int j = 0; j < cnt; j++) {
                int id = buf.readUnsignedShort();
                int length = buf.readUnsignedShort();
                if (id == 256) {
                    position.set(Position.KEY_VIN, buf.readSlice(length).toString(StandardCharsets.US_ASCII));
                } else {
                    position.set(Position.PREFIX_IO + id, ByteBufUtil.hexDump(buf.readSlice(length)));
                }
            }
        }

        decodeNetwork(position);
        if(this.parseFMB9xx){
            decodeFMB9xxParameters(position);
        }
        checkGPSStatus(position);
    }

    private List<Position> parseData(
            Channel channel, SocketAddress remoteAddress, ByteBuf buf, int locationPacketId, String... imei) {
        List<Position> positions = new LinkedList<>();

        if (!connectionless) {
            buf.readUnsignedInt(); // data length
        }

        int codec = buf.readUnsignedByte();
        int count = buf.readUnsignedByte();

        DeviceSession deviceSession = getDeviceSession(channel, remoteAddress, imei);

        if (deviceSession == null) {
            return null;
        }

        for (int i = 0; i < count; i++) {
            Position position = new Position(getProtocolName());

            position.setDeviceId(deviceSession.getDeviceId());
            position.setValid(true);

            if (codec == CODEC_12) {
                decodeSerial(channel, remoteAddress, position, buf);
            } else {
                decodeLocation(position, buf, codec);
            }

            if (!position.getOutdated() || !position.getAttributes().isEmpty()) {
                positions.add(position);
            }
        }

        if (channel != null) {
            if (connectionless) {
                ByteBuf response = Unpooled.buffer();
                response.writeShort(5);
                response.writeShort(0);
                response.writeByte(0x01);
                response.writeByte(locationPacketId);
                response.writeByte(count);
                channel.writeAndFlush(new NetworkMessage(response, remoteAddress));
            } else {
                ByteBuf response = Unpooled.buffer();
                response.writeInt(count);
                channel.writeAndFlush(new NetworkMessage(response, remoteAddress));
            }
        }

        return positions.isEmpty() ? null : positions;
    }

    @Override
    protected Object decode(Channel channel, SocketAddress remoteAddress, Object msg) throws Exception {

        ByteBuf buf = (ByteBuf) msg;

        if (connectionless) {
            return decodeUdp(channel, remoteAddress, buf);
        } else {
            return decodeTcp(channel, remoteAddress, buf);
        }
    }

    private Object decodeTcp(Channel channel, SocketAddress remoteAddress, ByteBuf buf) throws Exception {

        if (buf.getUnsignedShort(0) > 0) {
            parseIdentification(channel, remoteAddress, buf);
        } else {
            buf.skipBytes(4);
            return parseData(channel, remoteAddress, buf, 0);
        }

        return null;
    }

    private Object decodeUdp(Channel channel, SocketAddress remoteAddress, ByteBuf buf) throws Exception {

        buf.readUnsignedShort(); // length
        buf.readUnsignedShort(); // packet id
        buf.readUnsignedByte(); // packet type
        int locationPacketId = buf.readUnsignedByte();
        String imei = buf.readSlice(buf.readUnsignedShort()).toString(StandardCharsets.US_ASCII);

        return parseData(channel, remoteAddress, buf, locationPacketId, imei);

    }

    private void checkGPSStatus(Position position) {
        // checks if unit is in gps sleep mode, if so consider coordinates as still valid
        long sat = position.getInteger(Position.KEY_SATELLITES);
        long gpsStatus = position.getInteger(Position.KEY_GPS_STATUS);
        long sleep = position.getInteger(Position.KEY_SLEEP); // returns 0 if not found

        // possible values for GNSS Status AVL_ID = 69 - https://wiki.teltonika.lt/view/FMB_AVL_ID
        // mark positions in GNSS sleep mode as valid
        if (!position.getValid() && sat == 0
                &&
                (gpsStatus == GNSS_IN_SLEEP_STATE
                        || sleep == SLEEP_GPS
                        || sleep == SLEEP_DEEP
                )
        ) {
            position.setValid(true);
        }
    }

    private static Map.Entry<String, Object> createEntry(String key, Object value) {
        return new AbstractMap.SimpleEntry<>(key, value);
    }

    private void decodeFMB9xxParameters(Position position) {
        // this function will check all attributes of the position and if there are any unparsed (i.e. io214),
        // then it will try to map it to proper name based on latest FMB protocol description

        Map<String, Object> posAttrs = position.getAttributes();
        // copy of attributes, to make sure we don't mutate Map we are iterating
        Map<String, Object> attrs = new HashMap<String, Object>(posAttrs);
        int id;

        // iterate over copy
        for(Map.Entry<String, Object> entry : attrs.entrySet()) {
            // check if there are unparsed attributes
            if( entry.getKey().matches("^"+Position.PREFIX_IO+"[0-9]+$") ) {
                // parse attribute Id
                id = Integer.parseInt(entry.getKey().substring(Position.PREFIX_IO.length()));
                // convert Id to text
                String event = fmb9xxParser.parseEvent(id);
                if(event != null){
                    // replace attribute value (remove old, add new)
                    position.add(createEntry(event, entry.getValue()));
                    posAttrs.remove(entry.getKey());
                }
            }
        }
    }

    public void setParseFMB9xx(boolean parseFMB9xx){
        this.parseFMB9xx = parseFMB9xx;
    }
}
