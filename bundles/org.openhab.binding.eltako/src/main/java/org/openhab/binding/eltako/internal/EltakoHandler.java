/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.eltako.internal;

import static org.openhab.binding.eltako.internal.EltakoBindingConstants.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EltakoHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Martin Wenske - Initial contribution
 */
@NonNullByDefault
public class EltakoHandler extends BaseThingHandler implements EltakoTelegramListener {

    /*
     * Logger instance to create log entries
     */
    private final Logger logger = LoggerFactory.getLogger(EltakoHandler.class);

    /**
     * Channel variables
     */
    private PercentType brightness;
    private DecimalType speed;
    private OnOffType power;
    private OnOffType blocking;
    private int deviceId;

    /**
     * Initializer method
     */
    public EltakoHandler(Thing thing) {
        super(thing);
        brightness = PercentType.ZERO;
        speed = DecimalType.ZERO;
        power = OnOffType.OFF;
        blocking = OnOffType.OFF;
        deviceId = 0;
    }

    /**
     * Called by framework after creation of thing
     */
    @Override
    public void initialize() {

        // Acquire device ID from thing configuration (set by the user)
        this.deviceId = Integer.parseInt(getThing().getConfiguration().get(FUD14_ID).toString(), 16);
        // Update vendor property
        // updateProperty(PROPERTY_VENDOR, "Eltako");
        // updateProperty(PROPERTY_ID, "I dont care");
        // updateProperty(PROPERTY_PROTOCOL, "Serial and EnOcean (I think)");

        // Set thing status to UNKNOWN
        this.updateStatus(ThingStatus.UNKNOWN);

        EltakoBridgeHandler bridgeHandle = this.getMyBridgeHandle();
        if (bridgeHandle != null) {
            // Listen for a specific ID so received telegrams are forwarded to thing (Ignore 4th byte)
            bridgeHandle.addPacketListener(this, deviceId & 0xFFFFFF);
            // Set thing status depending on status of bridge
            Thing bridge = this.getMyBridge();
            if (bridge != null) {
                if (bridge.getStatus() != ThingStatus.ONLINE) {
                    // Set thing status to offline
                    this.updateStatus(ThingStatus.OFFLINE);
                } else {
                    // Set thing status to offline
                    this.updateStatus(ThingStatus.ONLINE);
                    // TODO: Check communication to thing first before setting it to ONLINE
                }

            }
        }

        logger.debug("Finished initializing of thing handler");
    }

    /**
     * Is called in case the bridge is changing its state
     */
    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        // Log event to console
        logger.debug("bridgeStatusChanged => {}", bridgeStatusInfo);
        if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            // Set thing status to unknown
            this.updateStatus(ThingStatus.ONLINE);
        }
        if (bridgeStatusInfo.getStatus() == ThingStatus.OFFLINE) {
            // Set thing status to unknown
            this.updateStatus(ThingStatus.UNKNOWN);
        }
    }

    /**
     * Bridge handler getter
     */
    protected @Nullable EltakoBridgeHandler getMyBridgeHandle() {
        Thing bridge = this.getBridge();
        if (bridge != null) {
            EltakoBridgeHandler bridgeHandle = (EltakoBridgeHandler) bridge.getHandler();
            if (bridgeHandle != null) {
                // Return
                return bridgeHandle;
            } else {
                // Log event to console
                logger.error("BridgeHandle not available for thing");
                return null;
            }
        } else {
            // Log event to console
            logger.error("Bridge not available for thing");
            return null;
        }
    }

    /**
     * Bridge getter
     */
    protected @Nullable Thing getMyBridge() {
        Thing bridge = this.getBridge();
        if (bridge != null) {
            // Return
            return bridge;
        } else {
            // Log event to console
            logger.error("BridgeHandle not available for thing");
            return null;
        }
    }

    /**
     * Prepares the data used for the telegram and sends it out
     */
    protected void sendTelegram(EltakoBridgeHandler bridgehandler) {
        // Prepare channel values
        int value_brightness = brightness.intValue();
        int value_speed = speed.intValue();
        int value_power;

        if (power.equals(OnOffType.ON)) {
            value_power = 9;
        } else {
            value_power = 8;
        }
        if (blocking.equals(OnOffType.ON)) {
            value_power += 4;
        }

        // Convert Device ID from int into 4 bytes
        int[] ID = new int[4];
        ID[0] = deviceId & 0xFF;
        ID[1] = (deviceId >> 8) & 0xFF;
        ID[2] = (deviceId >> 16) & 0xFF;
        ID[3] = (deviceId >> 24) & 0xFF;

        // Calculate CRC value
        int crc = (0x0B + 0x07 + 0x02 + value_brightness + value_speed + value_power + ID[3] + ID[2] + ID[1] + ID[0])
                % 256;

        // Prepare telegram
        int[] data = new int[] { 0xA5, 0x5A, 0x0B, 0x07, 0x02, value_brightness, value_speed, value_power, ID[3], ID[2],
                ID[1], ID[0], 0x00, crc };

        // Get own state
        if (this.getThing().getStatus() == ThingStatus.ONLINE) {
            // Write data by calling bridge handler method
            bridgehandler.serialWrite(data, 14);
        }
    }

    /**
     * Called by Bridge when a new telegram has been received
     */
    @Override
    public void telegramReceived(int[] packet) {
        // Prepare data to be written to log
        StringBuffer strbuf = new StringBuffer();
        // Create string out of byte data
        for (int i = 0; i < 14; i++) {
            strbuf.append(String.format("%02X ", packet[i]));
        }
        // Log event to console
        logger.debug("FUD14: Telegram Received: {}", strbuf);

        if ((packet[11] | (packet[10] << 8) | (packet[9] << 16)) == (deviceId & 0xFFFFFF)) {
            updateState(CHANNEL_BRIGHTNESS, PercentType.valueOf(String.valueOf(packet[5])));
            updateState(CHANNEL_SPEED, DecimalType.valueOf(String.valueOf(packet[6])));
            // updateState(CHANNEL_POWER, OnOffType.from(Integer.packet[7])));
            // updateState(CHANNEL_BLOCKING, OnOffType.from(Integer.packet[7])));
        }
    }

    /**
     * Event handler is called in case a channel has received a command
     */
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // Log event to console
        logger.debug("Channel {} received command {} with class {}", channelUID, command, command.getClass());
        // Get bridge instance
        Thing bridge = this.getMyBridge();
        // Check for valid bridge instance
        if (bridge != null) {
            // Get bridge handler instance
            EltakoBridgeHandler bridgehandler = getMyBridgeHandle();
            // Check for valid bridge handler instance
            if (bridgehandler != null) {
                switch (channelUID.getId()) {
                    case CHANNEL_BRIGHTNESS:
                        if (command instanceof PercentType) {
                            brightness = (PercentType) command;
                            // updateState(CHANNEL_BRIGHTNESS, brightness);
                        }
                        if (command instanceof OnOffType) {
                            if (command.equals(OnOffType.OFF)) {
                                brightness = PercentType.ZERO;
                                // updateState(CHANNEL_BRIGHTNESS, brightness);
                            }
                        }
                        if (command instanceof OnOffType) {
                            if (command.equals(OnOffType.ON)) {
                                brightness = PercentType.HUNDRED;
                                // updateState(CHANNEL_BRIGHTNESS, brightness);
                            }
                        }
                        if (command instanceof RefreshType) {
                            brightness = PercentType.ZERO;
                            updateState(CHANNEL_BRIGHTNESS, brightness);
                        }
                        break;
                    case CHANNEL_SPEED:
                        if (command instanceof DecimalType) {
                            speed = (DecimalType) command;
                        }
                        if (command instanceof RefreshType) {
                            speed = DecimalType.valueOf("0");
                            updateState(CHANNEL_SPEED, speed);
                        }
                        break;
                    case CHANNEL_POWER:
                        if (command instanceof OnOffType) {
                            if (command.equals(OnOffType.OFF)) {
                                power = OnOffType.OFF;
                                updateState(CHANNEL_POWER, OnOffType.OFF);
                            }
                        }
                        if (command instanceof OnOffType) {
                            if (command.equals(OnOffType.ON)) {
                                power = OnOffType.ON;
                                updateState(CHANNEL_POWER, OnOffType.ON);
                            }
                        }
                        if (command instanceof RefreshType) {
                            power = OnOffType.OFF;
                            updateState(CHANNEL_POWER, power);
                        }
                        break;
                    case CHANNEL_BLOCKING:
                        if (command instanceof OnOffType) {
                            if (command.equals(OnOffType.OFF)) {
                                blocking = OnOffType.OFF;
                                updateState(CHANNEL_BLOCKING, OnOffType.OFF);
                            }
                        }
                        if (command instanceof OnOffType) {
                            if (command.equals(OnOffType.ON)) {
                                blocking = OnOffType.ON;
                                updateState(CHANNEL_BLOCKING, OnOffType.ON);
                            }
                        }
                        if (command instanceof RefreshType) {
                            blocking = OnOffType.OFF;
                            updateState(CHANNEL_BLOCKING, blocking);
                        }
                        break;
                    default:
                        // Log event to console
                        logger.debug("Command {} is not supported by thing", command);
                        break;
                }
                sendTelegram(bridgehandler);
            }
        }
    }

    /**
     * Last method called before thing will be destroyed
     */
    @Override
    public void dispose() {
        logger.debug("Dispose thing instance");

        EltakoBridgeHandler bridgeHandle = this.getMyBridgeHandle();
        if (bridgeHandle != null) {
            // Listen for a specific ID so received telegrams are forwarded to thing
            bridgeHandle.removePacketListener(this, deviceId);
        }
    }

}
