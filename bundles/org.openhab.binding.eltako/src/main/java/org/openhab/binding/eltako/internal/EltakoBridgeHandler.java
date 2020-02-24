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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.eclipse.smarthome.config.core.status.ConfigStatusMessage;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.ConfigStatusBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.util.HexUtils;
import org.eclipse.smarthome.io.transport.serial.PortInUseException;
import org.eclipse.smarthome.io.transport.serial.SerialPort;
import org.eclipse.smarthome.io.transport.serial.SerialPortIdentifier;
import org.eclipse.smarthome.io.transport.serial.SerialPortManager;
import org.eclipse.smarthome.io.transport.serial.UnsupportedCommOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EltakoBridgeHandler} is responsible for handling connection to serial interface
 *
 * @author Martin Wenske - Initial contribution
 */
public class EltakoBridgeHandler extends ConfigStatusBridgeHandler {

    /*
     * Logger instance to create log entries
     */
    private Logger logger = LoggerFactory.getLogger(EltakoBridgeHandler.class);

    public final static Set<ThingTypeUID> SUPPORTED_THING_TYPES = new HashSet<>(Arrays.asList(THING_TYPE_BRIDGE));

    /*
     * Instance of serialPortManager
     */
    private SerialPortManager serialPortManager;

    /*
     * Variables related to serial data handling
     */
    private SerialPort serialPort;
    private String comportName;
    protected InputStream inputStream;
    protected OutputStream outputStream;

    /* TODO: Make this configurable in Bridge config */
    private static final int ELTAKO_DEFAULT_BAUD = 57600;

    // Our own thread pool for the long-running listener job
    private ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> bridgePollingJob;
    private Boolean bridgePollingThreadIsNotCanceled;
    protected Boolean waitForGateway;

    // Queue implementation for telegram handling
    Queue<int[]> telegramQueue = new LinkedList<int[]>();

    protected Map<Long, HashSet<EltakoTelegramListener>> listeners;

    /**
     * Initializer method
     */
    public EltakoBridgeHandler(Bridge bridge, SerialPortManager serialPortManager) {
        super(bridge);
        this.serialPortManager = serialPortManager;
        serialPort = null;
        outputStream = null;
        inputStream = null;
        comportName = null;
        bridgePollingThreadIsNotCanceled = null;
        telegramQueue.clear();
        listeners = new HashMap<>();
        waitForGateway = false;
    }

    /**
     * Called by framework after bridge instance has been created
     */
    @Override
    public void initialize() {

        // Log event to console
        logger.debug("Initialize bridge => {}", this.getThing().getUID());

        // Set bridge status to UNKNOWN (always good practice)
        updateStatus(ThingStatus.UNKNOWN);

        if (this.serialPortManager == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "SerialPortManager instance could not be found");
        }

        // Execute initialization in background (because of unknown runtime and potential blocking behavior)
        scheduler.execute(() -> {
            // Acquire comport number from thing configuration (set by the user)
            comportName = (String) getThing().getConfiguration().get(SERIALCOMPORT);
            // Log event to console
            logger.debug("Bridge configured to use comport => {}", comportName);
            // Get comport handle
            SerialPortIdentifier id = serialPortManager.getIdentifier(comportName);
            // Check if comport is available
            if (id == null) {
                // Log event to console
                logger.error("Comport {} not available", comportName);
                // Set bridge status to OFFLINE
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Comport not physical available");
            } else if (id.isCurrentlyOwned() == true) {
                // Comport occupied by someone else
                // Log event to console
                logger.error("Comport {} already opened by another application", comportName);
                // Set bridge status to OFFLINE
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Comport in use by another application");
            } else {
                // Comport is available and can be opened
                try {
                    // Try to open comport
                    serialPort = id.open(EltakoBindingConstants.BINDING_ID, 1000);
                } catch (PortInUseException e) {
                    logger.error("{} already in use: {}", comportName, e);
                    // Set bridge status to OFFLINE
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            "Comport in use by another application");
                }

                try {
                    // Set some parameters for the serial interface
                    serialPort.setSerialPortParams(ELTAKO_DEFAULT_BAUD, SerialPort.DATABITS_8, SerialPort.STOPBITS_1,
                            SerialPort.PARITY_NONE);
                    // Wait 100ms before exiting serial.read()
                    serialPort.enableReceiveTimeout(100);
                } catch (UnsupportedCommOperationException e) {
                    // Log event to console
                    logger.error("Something went wrong setting {} parameters: {}", comportName, e);
                    // Set bridge status to OFFLINE
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            "Something went wrong setting Comport parameters");
                }
                // Get handle for Input and Output stream
                try {
                    inputStream = serialPort.getInputStream();
                    outputStream = serialPort.getOutputStream();
                } catch (IOException e) {
                    // Log event to console
                    logger.error("Something went wrong acquireing input/output stream on {}: {}", comportName, e);
                    // Set bridge status to OFFLINE
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            "Something went wrong acquireing input/output stream");
                }

                // Log event to console
                logger.debug("{} opened successfully", comportName);
                // Set Polling Thread exit condition
                bridgePollingThreadIsNotCanceled = true;
                // Start background thread for receiving serial data
                bridgePollingJob = scheduledExecutorService.schedule(bridgePollingThread, 0, TimeUnit.SECONDS);
            }
        });
    }

    /**
     * Called by framework right before bridge instance will be destroyed.
     * This is a good place to close open handlers and deinitialize variables.
     */
    @Override
    public void dispose() {

        // Log event to console
        logger.debug("Dispose bridge => {}", this.getThing().getUID());

        // Dispose bridge
        super.dispose();

        // Cancel SerialPolling Thread
        if (bridgePollingJob != null) {
            logger.debug("Canceling SerialPollingThread");
            // SerialPollingJob.cancel(true);
            bridgePollingThreadIsNotCanceled = false;
            while (!bridgePollingJob.isDone()) {
                ;
            }
            bridgePollingJob = null;
            bridgePollingThreadIsNotCanceled = null;
        }

        // Close output stream
        if (outputStream != null) {
            logger.debug("Closing serial output stream");
            IOUtils.closeQuietly(outputStream);
        }

        // Close input stream
        if (inputStream != null) {
            logger.debug("Closeing serial input stream");
            IOUtils.closeQuietly(inputStream);
        }

        // Close serial port
        if (serialPort != null) {
            // Log event to console
            logger.debug("{} closed", comportName);
            // Close comport to be used by other applications
            serialPort.close();
            serialPort = null;
        }

        // Reset variable
        if (comportName != null) {
            comportName = null;
        }
    }

    /**
     * TODO: What is this method good for?
     */
    @Override
    public Collection<ConfigStatusMessage> getConfigStatus() {

        // Log event to console
        // logger.debug("GetConfigStatus for bridge => {}", this.getThing().getUID());

        Collection<ConfigStatusMessage> configStatusMessages = new LinkedList<>();
        return configStatusMessages;
    }

    /**
     * Event handler is called in case a channel has received a command
     */
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {

        // Log event to console
        logger.debug("Command received for bridge => {}", this.getThing().getUID());

        // We dont expect any commands to be handled by bridge handler
        switch (channelUID.getId()) {
            default:
                break;
        }
    }

    /**
     * Write data to serial interface
     */
    protected void serialWrite(int[] message, int length) {

        // Safety check
        if (length == 0 || message == null) {
            // Log event to console
            logger.error("Serial Write: Length is zero or invalid buffer");
            return;
        }

        // Another safety check
        if (outputStream == null) {
            // Log event to console
            logger.error("Serial Write: outputStream is not initilized");
            return;
        }

        // ############################################
        // Prepare data to be written to log
        StringBuffer strbuf = new StringBuffer();
        // Create string out of byte data
        for (int i = 0; i < length; i++) {
            strbuf.append(String.format("%02X ", message[i]));
        }
        // Log event to console
        logger.debug("Serial Write: {}", strbuf);
        // ############################################
        byte[] buffer = new byte[length];
        for (int i = 0; i < length; i++) {
            buffer[i] = (byte) message[i];
        }
        // Pass data to output steam
        try {
            outputStream.write(buffer, 0, length);
            // Force all remaining data to be written immediately
            outputStream.flush();
        } catch (IOException e) {
            // Log event to console
            logger.error("Write action on {} failed: {}", comportName, e);
        }
    }

    /**
     * Read data from serial interface
     */
    protected int serialRead(byte[] buffer, int length) {

        int rxcount = 0;

        // Safety check
        if (length == 0 || buffer == null) {
            // Log event to console
            logger.error("Serial Read: Length is zero or invalid buffer");
            return 0;
        }

        // Another safety check
        if (inputStream == null) {
            // Log event to console
            logger.error("Serial Write: inputstream is not initilized");
            return 0;
        }

        // Pull data from input stream
        try {
            rxcount = inputStream.read(buffer, 0, length);
        } catch (IOException e) {
            logger.error("Read action on {} failed: {}", comportName, e);
            return 0;
        }

        // ############################################
        if (rxcount > 0) {
            // Prepare data to be written to log
            StringBuffer strbuf = new StringBuffer();
            // Create string out of byte data
            for (int i = 0; i < rxcount; i++) {
                strbuf.append(String.format("%02X ", buffer[i] & 0xFF));
            }
            // Log event to console
            logger.debug("Serial Read: {}", strbuf);
        }
        // ############################################
        return rxcount;
    }

    int rxbytes = 0;

    protected void processSerialData() {
        byte[] buffer = new byte[14];
        int[] telegram = new int[14];
        int bytesRead;

        if (rxbytes == 0) {
            // Read single byte until we receive the sync byte
            bytesRead = serialRead(buffer, 1);
            // Check for first byte to be 0xA5
            if (bytesRead > 0) {
                if ((buffer[0] & 0xFF) == 0xA5) {
                    // Log event to console
                    logger.debug("Sync Byte 0xA5 received");
                    // Copy Sync byte
                    telegram[0] = buffer[0] & 0xFF;
                    // Increase counter
                    rxbytes = 1;
                }
            }
        } else if (rxbytes == 1) {
            // Read single byte until we receive the sync byte
            bytesRead = serialRead(buffer, 1);
            // Check for first byte to be 0xA5
            if (bytesRead > 0) {
                if ((buffer[0] & 0xFF) == 0x5A) {
                    // Log event to console
                    logger.debug("Sync Byte 0x5A received");
                    // Copy Sync byte
                    telegram[1] = buffer[0] & 0xFF;
                    // Increase counter
                    rxbytes = 2;
                } else if ((buffer[0] & 0xFF) == 0xA5) {
                    // do nothing. sync byte still valid
                    // Log event to console
                    logger.warn(
                            "Missmatch of Eltako telegram sync byte! This may be an indication for ether bad connection, high bus load or defective device.");
                } else {
                    // Log event to console
                    logger.warn(
                            "Missmatch of Eltako telegram sync byte! This may be an indication for ether bad connection, high bus load or defective device.");
                    // Reset byte counter
                    rxbytes = 0;
                }
            }
        } else {
            // Read received data (received data length is not guaranteed)
            bytesRead = serialRead(buffer, 14 - rxbytes);
            // Data received?
            if (bytesRead > 0) {
                // Add serial data to message
                for (int i = 0; i < bytesRead; i++) {
                    telegram[i + rxbytes] = buffer[i] & 0xFF;
                }
            }
            // Track how much data has been received
            rxbytes += bytesRead;
            // Check if a complete telegram (14 Bytes) has been received
            if (rxbytes == 14) {
                // ############################################
                // Prepare data to be written to log
                StringBuffer strbuf = new StringBuffer();
                // Create string out of byte data
                for (int i = 0; i < 14; i++) {
                    strbuf.append(String.format("%02X", telegram[i]));
                }
                // Log event to console
                logger.debug("Telegram Received: {}", strbuf);
                // ############################################

                // Add received telegram to RxQueue
                // telegramQueue.add(temp);
                // Log event to console
                // logger.debug("Element added to Queue. Size is now {}", telegramQueue.size());
                // TODO: Maybe add queue later

                if (waitForGateway == true) {
                    if (telegram[10] == 0x17) {
                        int[] message = new int[14];
                        int[] data = new int[] { 0, 0, 0, 0 };
                        int[] id = new int[] { 0, 0, 0, 0 };
                        // Update bridge status
                        updateStatus(ThingStatus.ONLINE);
                        // Waiting for FAM14 to respond with status telegram
                        waitForGateway = false;
                        // parse message
                        parseMessage(telegram);
                        // Set FAM14 back to telegram mode
                        constuctMessage(message, 5, 0xFF, data, id, 0x00);
                        serialWrite(message, 14);
                    }
                } else {
                    // Forward telegram to listeners
                    byte senderId[] = { 0, 0, 0, 1 };
                    long s = Long.parseLong(HexUtils.bytesToHex(senderId), 16);
                    HashSet<EltakoTelegramListener> pl = listeners.get(s);
                    if (pl != null) {
                        pl.forEach(l -> l.telegramReceived(telegram));
                    }
                }

                // Reset byte counter
                rxbytes = 0;
            }
        }
    }

    protected void handlebridgeStatus() {

        ThingStatus status = this.getThing().getStatus();

        // Check for bridge status
        if ((status == ThingStatus.UNKNOWN) && (waitForGateway == false)) {
            // Bridge has state UNKOWN right after initialization
            int[] message = new int[14];
            int[] data = new int[] { 0, 0, 0, 0 };
            int[] id = new int[] { 0, 0, 0, 0 };
            // Force FAM14 gateway into config mode
            constuctMessage(message, 5, 0xFF, data, id, 0xFF);
            serialWrite(message, 14);
            // Search for FAM14 gateway
            constuctMessage(message, 5, 0xF0, data, id, 0xFF);
            serialWrite(message, 14);
            // Wait for response from FAM14 gateway
            waitForGateway = true;
        }
    }

    /**
     * Thread is executed as soon as the connection to serial interface is established and data can be transmitted
     */
    protected Runnable bridgePollingThread = () -> {
        // Never stop reading data from serial interface unless the bridge should be disposed
        while (bridgePollingThreadIsNotCanceled) {
            // Perform actions depending on bridge state
            handlebridgeStatus();
            // Receive serial data
            processSerialData();
        }
        // Log event to console
    };

    protected void parseMessage(int[] message) {
        // Extract information from telegram
        // int sync_byte_1 = message[0];
        // int sync_byte_2 = message[1];
        // int header_ident = message[2] >> 5;
        // int length = message[2] & 0x1F;
        // int org = message[3];
        // int own_id = message[4];
        // int used_ids = message[5];
        // int unknown_1 = message[6];
        // int unknown_2 = message[7];
        // int unknown_3 = message[8];
        // int device_type_number = message[9];
        int version_number_high = message[10] >> 4;
        int version_number_low = message[10] & 0x0F;
        // int unknown_4 = message[11];
        // int unknown_5 = message[12];
        // int crc = message[13];

        // Update thing property
        updateProperty(FAM14_HARDWARE_VERSION, String.format("V%d.%d", version_number_high, version_number_low));

    }

    protected void constuctMessage(int[] message, int header_ident, int org, int[] data, int[] id, int status) {
        // Fill message with data
        message[0] = 0xA5;
        message[1] = 0x5A;
        message[2] = (header_ident << 5) + 0x0B;
        message[3] = org;
        message[4] = data[0];
        message[5] = data[1];
        message[6] = data[2];
        message[7] = data[3];
        message[8] = id[0];
        message[9] = id[1];
        message[10] = id[2];
        message[11] = id[3];
        message[12] = status;
        message[13] = 0;

        // Calculate CRC from message data (excluding sync bytes)
        for (int i = 2; i < 13; i++) {
            message[13] += message[i];
        }
        // CRC is only a single byte so we break it down to 8bit
        message[13] = message[13] % 256;
    }

    public void addPacketListener(EltakoTelegramListener listener, long senderIdToListenTo) {
        if (listeners.computeIfAbsent(senderIdToListenTo, k -> new HashSet<>()).add(listener)) {
            logger.debug("Listener {} added with ID {}", listener, senderIdToListenTo);
        }
    }

    public void removePacketListener(EltakoTelegramListener listener, long senderIdToListenTo) {
        HashSet<EltakoTelegramListener> pl = listeners.get(senderIdToListenTo);
        if (pl != null) {
            pl.remove(listener);
            if (pl.isEmpty()) {
                listeners.remove(senderIdToListenTo);
            }
            logger.debug("Listener {} removed with ID {}", listener, senderIdToListenTo);
        }
    }

}