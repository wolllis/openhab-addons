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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.io.CommPortIdentifier;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;

/**
 * The {@link EltakoHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Martin Wenske - Initial contribution
 */
@NonNullByDefault
public class EltakoHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(EltakoHandler.class);
    private @Nullable EltakoConfiguration config;
    private @Nullable ScheduledFuture<?> pollingJob;

    public EltakoHandler(Thing thing) {
        super(thing);
    }

    private @Nullable CommPortIdentifier portId;

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        switch (channelUID.getId()) {
            case CHANNEL_POWER:
                if (command instanceof RefreshType) {
                    // TODO: handle data refresh
                }

                // TODO: handle command

                // Note: if communication with thing fails for some reason,
                // indicate that by setting the status with detail information:
                // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                // "Could not control device at IP address x.x.x.x");

                if (command instanceof OnOffType) {
                    logger.info("Got OnOffType!");

                    // ############################################################
                    logger.info("Setting properties");
                    // Update vendor property
                    updateProperty(PROPERTY_VENDOR, "Eltako");
                    updateProperty(PROPERTY_ID, "I dont care");
                    updateProperty(PROPERTY_PROTOCOL, "Serial and EnOcean (I think)");

                    int temp = getConfigAs(EltakoConfiguration.class).DeviceID;
                    logger.info("DeviceID is: {}", temp);

                    logger.info("Search for Serial Ports");

                    // Platform specific port name, here= a Unix name
                    //
                    // NOTE: On at least one Unix JavaComm implementation JavaComm
                    // enumerates the ports as "COM1" ... "COMx", too, and not
                    // by their Unix device names "/dev/tty...".
                    // Yet another good reason to not hard-code the wanted
                    // port, but instead make it user configurable.
                    //
                    String wantedPortName = "COM8";
                    //
                    // Get an enumeration of all ports known to JavaComm
                    //
                    Enumeration portIdentifiers = CommPortIdentifier.getPortIdentifiers();
                    //
                    // Check each port identifier if
                    // (a) it indicates a serial (not a parallel) port, and
                    // (b) matches the desired name.
                    //
                    CommPortIdentifier portId = null; // will be set if port found
                    while (portIdentifiers.hasMoreElements()) {
                        CommPortIdentifier pid = (CommPortIdentifier) portIdentifiers.nextElement();
                        if (pid.getPortType() == CommPortIdentifier.PORT_SERIAL
                                && pid.getName().equals(wantedPortName)) {
                            portId = pid;
                            break;
                        }
                    }
                    if (portId == null) {
                        logger.info("Could not find serial port {}", wantedPortName);
                    }

                    SerialPort port = null;

                    try {
                        port = portId.open("name", // Name of the application asking for the port
                                10000 // Wait max. 10 sec. to acquire port
                        );
                    } catch (PortInUseException e) {
                        logger.info("Port already in use: {}", e);
                    }
                    // Set all the params.
                    // This may need to go in a try/catch block which throws UnsupportedCommOperationException
                    //
                    try {
                        port.setSerialPortParams(57600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1,
                                SerialPort.PARITY_NONE);
                    } catch (Exception e) {
                        logger.info("Something went wrong during setting com parameters: {}", e.toString());
                    }

                    //
                    // Open the input Reader and output stream. The choice of a
                    // Reader and Stream are arbitrary and need to be adapted to
                    // the actual application. Typically one would use Streams in
                    // both directions, since they allow for binary data transfer,
                    // not only character data transfer.
                    //
                    BufferedReader is = null; // for demo purposes only. A stream would be more typical.
                    PrintStream os = null;

                    try {
                        is = new BufferedReader(new InputStreamReader(port.getInputStream()));
                    } catch (IOException e) {
                        logger.info("Can't open input stream: write-only");
                    }

                    //
                    // New Linux systems rely on Unicode, so it might be necessary to
                    // specify the encoding scheme to be used. Typically this should
                    // be US-ASCII (7 bit communication), or ISO Latin 1 (8 bit
                    // communication), as there is likely no modem out there accepting
                    // Unicode for its commands. An example to specify the encoding
                    // would look like:
                    //
                    // os = new PrintStream(port.getOutputStream(), true, "ISO-8859-1");
                    //
                    try {
                        os = new PrintStream(port.getOutputStream(), true);
                        //
                        // Actual data communication would happen here

                        // State state = (OnOffType) command;
                        if (command.equals(OnOffType.ON)) {
                            // updateState(CHANNEL_POWER, OnOffType.OFF);
                            // Write to the output
                            // os.print("State ON");
                            // os.write( 0x53 );
                            os.write(0xA5);
                            os.write(0x5A);
                            os.write(0x0B);
                            os.write(0x07);
                            os.write(0x02);
                            os.write(0x64);
                            os.write(0x00);
                            os.write(0x09);
                            os.write(0x00);
                            os.write(0x00);
                            os.write(0x00);
                            os.write(0x03);
                            os.write(0x00);
                            os.write(0x84);
                        }
                        if (command.equals(OnOffType.OFF)) {
                            // updateState(CHANNEL_POWER, OnOffType.OFF);
                            // Write to the output
                            // os.print("State OFF");
                            os.write(0xA5);
                            os.write(0x5A);
                            os.write(0x0B);
                            os.write(0x07);
                            os.write(0x02);
                            os.write(0x64);
                            os.write(0x00);
                            os.write(0x08);
                            os.write(0x00);
                            os.write(0x00);
                            os.write(0x00);
                            os.write(0x03);
                            os.write(0x00);
                            os.write(0x83);
                        }
                        //
                        // It is very important to close input and output streams as well
                        // as the port. Otherwise Java, driver and OS resources are not released.
                        //
                        if (is != null) {
                            is.close();
                        }
                        if (os != null) {
                            os.close();
                        }
                        if (port != null) {
                            port.close();
                        }
                    } catch (IOException e) {
                        logger.info("Something went wrong during setting com parameters: {}", e.toString());
                    }
                    // ############################################################

                }
                break;
            // ...
            case CHANNEL_BRIGHTNESS:
                logger.info("CHANNEL_BRIGHTNESS received command!");
                break;
        }

        logger.info("Command received: {}, {}", channelUID, command);
        logger.info("For channel: {}", channelUID.getId());
    }

    @Override
    public void initialize() {
        logger.info("Start initializing!");
        config = getConfigAs(EltakoConfiguration.class);

        @Nullable
        List<String> mylist = null;

        if (mylist != null) {
            mylist.forEach(array -> logger.info("{}", array));
        }

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                // execute some binding specific polling code
                logger.info("Some scheduled stuff!");
            }
        };

        logger.info("Creating scheduler");
        pollingJob = scheduler.scheduleWithFixedDelay(runnable, 0, 1, TimeUnit.SECONDS);

        // TODO: Initialize the handler.
        // The framework requires you to return from this method quickly. Also, before leaving this method a thing
        // status from one of ONLINE, OFFLINE or UNKNOWN must be set. This might already be the real thing status in
        // case you can decide it directly.
        // In case you can not decide the thing status directly (e.g. for long running connection handshake using WAN
        // access or similar) you should set status UNKNOWN here and then decide the real status asynchronously in the
        // background.

        // set the thing status to UNKNOWN temporarily and let the background task decide for the real status.
        // the framework is then able to reuse the resources from the thing handler initialization.
        // we set this upfront to reliably check status updates in unit tests.
        updateStatus(ThingStatus.UNKNOWN);

        // Example for background initialization:
        scheduler.execute(() -> {
            boolean thingReachable = true; // <background task with long running initialization here>
            // when done do:
            if (thingReachable) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE);
            }
        });

        logger.info("Finished initializing!");

        // Note: When initialization can NOT be done set the status with more details for further
        // analysis. See also class ThingStatusDetail for all available status details.
        // Add a description to give user information to understand why thing does not work as expected. E.g.
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
        // "Can not access device as username and/or password are invalid");
    }

    @Override
    public void dispose() {
        logger.info("Dispose instance!");

        if (this.pollingJob != null) {
            pollingJob.cancel(true);
        }
    }
}
