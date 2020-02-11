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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

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

    private SerialPort serialPort;

    private String ComportName;

    private static final int ELTAKO_DEFAULT_BAUD = 57600;

    /*
     * Initializer method
     */
    public EltakoBridgeHandler(Bridge bridge, SerialPortManager serialPortManager) {
        super(bridge);
        this.serialPortManager = serialPortManager;
        serialPort = null;
        ComportName = null;
    }

    /*
     * Called by framework after bridge instance has been created
     */
    @Override
    public void initialize() {

        // Log event to console
        logger.debug("Initialize bridge => {}", this.getThing().getUID());

        // Set bridge status to OFFLINE
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING, "trying to connect to gateway...");

        if (this.serialPortManager == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "SerialPortManager could not be found");
        }

        // Acquire comport number from thing configuration (set by the user)
        // ComportName = getConfigAs(EltakoConfiguration.class).SerialComPort;
        ComportName = (String) getThing().getConfiguration().get(SERIALCOMPORT);
        // Log event to console
        logger.debug("Bridge configured to use comport => {}", ComportName);

        SerialPortIdentifier id = serialPortManager.getIdentifier(ComportName);

        // Check if comport is available
        if (id == null) {
            // Log event to console
            logger.error("Comport {} not available", ComportName);
            // Set bridge status to OFFLINE
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Comport not physical available");
        } else if (id.isCurrentlyOwned() == true) {
            // Log event to console
            logger.error("Comport {} already opened by another application", ComportName);
            // Set bridge status to OFFLINE
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Comport in use by another application");
        } else {
            // COM1 is available and can be opened
            try {
                // Try opening COM1
                serialPort = id.open(EltakoBindingConstants.BINDING_ID, 1000);
            } catch (PortInUseException e) {
                logger.error("{} already in use: {}", ComportName, e);
                // Set bridge status to OFFLINE
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Comport in use by another application");
            }

            try {
                serialPort.setSerialPortParams(ELTAKO_DEFAULT_BAUD, SerialPort.DATABITS_8, SerialPort.STOPBITS_1,
                        SerialPort.PARITY_NONE);
            } catch (UnsupportedCommOperationException e) {
                // Log event to console
                logger.error("Something went wrong setting {} parameters: {}", ComportName, e);
                // Set bridge status to OFFLINE
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Something went wrong setting Comport parameters");
            }

            // Log event to console
            logger.debug("{} opened successfully", ComportName);
            // Update bridge status
            updateStatus(ThingStatus.ONLINE);
        }

    }

    /*
     * Called by framework right before bridge instance will be destroyed
     */
    @Override
    public void dispose() {

        // Log event to console
        logger.debug("Dispose bridge => {}", this.getThing().getUID());

        // Dispose bridge
        super.dispose();

        // Close serial port if it exists
        if (serialPort != null) {
            // Log event to console
            logger.debug("{} closed", ComportName);
            // Close comport to be used by other applications
            serialPort.close();
            serialPort = null;
        }

        // Reset variable if it exists
        if (ComportName != null) {
            ComportName = null;
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

        switch (channelUID.getId()) {
            default:
                break;
        }
    }
}