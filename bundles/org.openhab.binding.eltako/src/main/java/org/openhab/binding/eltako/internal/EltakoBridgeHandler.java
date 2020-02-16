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
import java.util.HashSet;
import java.util.LinkedList;
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
import org.eclipse.smarthome.io.transport.serial.PortInUseException;
import org.eclipse.smarthome.io.transport.serial.SerialPort;
import org.eclipse.smarthome.io.transport.serial.SerialPortIdentifier;
import org.eclipse.smarthome.io.transport.serial.SerialPortManager;
import org.eclipse.smarthome.io.transport.serial.UnsupportedCommOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EltakoBridgeHandler} is responsible for sending ESP3Packages build by {@link EltakoActuatorHandler} and
 * transferring received ESP3Packages to {@link EltakoSensorHandler}.
 *
 * @author Daniel Weber - Initial contribution
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
    private ScheduledFuture<?> serialPollingJob;
    private Boolean serialPollingThreadIsNotCanceled;

    /*
     * Initializer method
     */
    public EltakoBridgeHandler(Bridge bridge, SerialPortManager serialPortManager) {
        super(bridge);
        this.serialPortManager = serialPortManager;
        serialPort = null;
        outputStream = null;
        inputStream = null;
        comportName = null;
        serialPollingThreadIsNotCanceled = null;
    }

    /*
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
                serialPollingThreadIsNotCanceled = true;
                // Start background thread for receiving serial data
                serialPollingJob = scheduledExecutorService.schedule(SerialPollingThread, 1, TimeUnit.SECONDS);
                // Update bridge status
                updateStatus(ThingStatus.ONLINE);
            }
        });
    }

    /*
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
        if (serialPollingJob != null) {
            logger.debug("Canceling SerialPollingThread");
            // SerialPollingJob.cancel(true);
            serialPollingThreadIsNotCanceled = false;
            while (!serialPollingJob.isDone()) {
                ;
            }
            serialPollingJob = null;
            serialPollingThreadIsNotCanceled = null;
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

    @Override
    public Collection<ConfigStatusMessage> getConfigStatus() {

        // Log event to console
        logger.debug("GetConfigStatus for bridge => {}", this.getThing().getUID());

        Collection<ConfigStatusMessage> configStatusMessages = new LinkedList<>();
        return configStatusMessages;
    }

    /*
     * Event handler is called in case a channel has received a command
     */
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {

        // Log event to console
        logger.debug("Command received for bridge => {}", this.getThing().getUID());

        // We dont expect any commands to be handeled by bridge handler
        switch (channelUID.getId()) {
            default:
                break;
        }
    }

    /*
     * Write data to serial interface
     */
    protected void serialWrite(byte[] buffer, int length) {

        // Safety check
        if (length == 0 || buffer == null) {
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
            strbuf.append(String.format("%02X ", buffer[i] & 0xFF));
        }
        // Log event to console
        logger.debug("Serial Write: {}", strbuf);
        // ############################################

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

    /*
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

    /*
     * This method is called by the EltakoDeviceDiscoveryService to signal a scan should be started
     */
    public void startDiscovery(EltakoDeviceDiscoveryService service) {
        // TODO: Implement scan handling
    }

    /*
     * This method is called by the EltakoDeviceDiscoveryService to signal a scan should be stopped
     */
    public void stopDiscovery() {
        // TODO: Implement scan handling
    }

    /*
     * Thread is executed as soon as the connection to serial interface is established and data can be transmitted
     */
    protected Runnable SerialPollingThread = () -> {
        int rxbytes = 0;
        byte[] buffer = new byte[14];
        byte[] message = new byte[14];

        // Never stop reading data from serial interface
        while (serialPollingThreadIsNotCanceled) {
            // Read received data (received data length is not guaranteed)
            int bytesRead = serialRead(buffer, 14 - rxbytes);
            // Data received?
            if (bytesRead > 0) {
                // Add serial data to message
                for (int i = 0; i < bytesRead; i++) {
                    message[i + rxbytes] = buffer[i];
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
                    strbuf.append(String.format("%02X ", message[i] & 0xFF));
                }
                // Log event to console
                logger.debug("Telegram Received: {}", strbuf);
                // ############################################
                // Reset byte counter
                rxbytes = 0;
            }
        }
    };
}