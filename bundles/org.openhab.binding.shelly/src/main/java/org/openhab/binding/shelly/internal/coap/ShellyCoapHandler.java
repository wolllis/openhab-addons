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
package org.openhab.binding.shelly.internal.coap;

import static org.openhab.binding.shelly.internal.ShellyBindingConstants.*;
import static org.openhab.binding.shelly.internal.ShellyUtils.*;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.*;
import static org.openhab.binding.shelly.internal.coap.ShellyCoapJSonDTO.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.MessageObserverAdapter;
import org.eclipse.californium.core.coap.Option;
import org.eclipse.californium.core.coap.OptionNumberRegistry;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.unit.SIUnits;
import org.eclipse.smarthome.core.library.unit.SmartHomeUnits;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.shelly.internal.api.ShellyDeviceProfile;
import org.openhab.binding.shelly.internal.coap.ShellyCoapJSonDTO.CoIotDescrBlk;
import org.openhab.binding.shelly.internal.coap.ShellyCoapJSonDTO.CoIotDescrSen;
import org.openhab.binding.shelly.internal.coap.ShellyCoapJSonDTO.CoIotDevDescription;
import org.openhab.binding.shelly.internal.coap.ShellyCoapJSonDTO.CoIotGenericSensorList;
import org.openhab.binding.shelly.internal.coap.ShellyCoapJSonDTO.CoIotSensor;
import org.openhab.binding.shelly.internal.coap.ShellyCoapJSonDTO.CoIotSensorTypeAdapter;
import org.openhab.binding.shelly.internal.config.ShellyThingConfiguration;
import org.openhab.binding.shelly.internal.handler.ShellyBaseHandler;
import org.openhab.binding.shelly.internal.handler.ShellyColorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * The {@link ShellyCoapHandler} handles the CoIoT/Coap registration and events.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyCoapHandler implements ShellyCoapListener {
    private final Logger logger = LoggerFactory.getLogger(ShellyCoapHandler.class);

    private final ShellyBaseHandler thingHandler;
    private final ShellyThingConfiguration config;
    private final GsonBuilder gsonBuilder;
    private final Gson gson;
    private String thingName;

    private @Nullable ShellyCoapServer coapServer;
    private @Nullable CoapClient statusClient;
    private @Nullable Request reqDescription;
    private @Nullable Request reqStatus;

    private int lastSerial = -1;
    private String lastPayload = "";
    private Map<String, CoIotDescrBlk> blockMap = new HashMap<String, CoIotDescrBlk>();
    private Map<String, CoIotDescrSen> sensorMap = new HashMap<String, CoIotDescrSen>();

    public ShellyCoapHandler(ShellyThingConfiguration config, ShellyBaseHandler thingHandler,
            @Nullable ShellyCoapServer coapServer) {
        Validate.notNull(coapServer);
        this.thingHandler = thingHandler;
        this.coapServer = coapServer;
        this.config = config;
        this.thingName = thingHandler.thingName;

        gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(CoIotGenericSensorList.class, new CoIotSensorTypeAdapter());
        gsonBuilder.setPrettyPrinting();
        gson = gsonBuilder.create();
    }

    /*
     * Initialize Coap access, send discovery packet and start Status server
     */
    @SuppressWarnings("null")
    public void start() {
        try {
            reqDescription = sendRequest(reqDescription, config.deviceIp, COLOIT_URI_DEVDESC, Type.CON);

            if (statusClient == null) {
                coapServer.init(config.localIp);
                coapServer.addListener(this);

                statusClient = new CoapClient(completeUrl(config.deviceIp, COLOIT_URI_DEVSTATUS))
                        .setTimeout((long) SHELLY_API_TIMEOUT_MS).useNONs().setEndpoint(coapServer.getEndpoint());

                coapServer.start();
            }
        } catch (IOException e) {
            logger.warn("{}: Unable to start CoIoT: {}", thingName, e.getMessage());
        } catch (NullPointerException e) {
            logger.debug("{}: Coap Exception: {} ({})\n{}", thingName, e.getMessage(), e.getClass(), e.getStackTrace());
        }
    }

    /**
     * Process an inbound Response (or mapped Request)
     * - decode Coap options
     * - handle discery result or status updates
     *
     * @param response The Response packet
     */
    @Override
    public void processResponse(@Nullable Response response) {
        if (response == null) {
            return; // other device instance
        }
        String ip = response.getSourceContext().getPeerAddress().toString();
        if (!ip.contains(config.deviceIp)) {
            return;
        }

        String payload = "";
        String devId = "";
        String uri = "";
        // int validity = 0;
        int serial = 0;
        try {
            logger.debug("{}: CoIoT Message from {}: {}", thingName, response.getSourceContext().getPeerAddress(),
                    response.toString());
            if (response.isCanceled() || response.isDuplicate() || response.isRejected()) {
                logger.debug("{} ({}): Packet was canceled, rejected or is a duplicate -> discard", thingName, devId);
                return;
            }

            if (response.getCode() == ResponseCode.CONTENT) {
                payload = response.getPayloadString();
                List<Option> options = response.getOptions().asSortedList();
                Validate.notNull(options);
                int i = 0;
                while (i < options.size()) {
                    @Nullable
                    Option opt = options.get(i);
                    Validate.notNull(opt);
                    switch (opt.getNumber()) {
                        case OptionNumberRegistry.URI_PATH:
                            uri = COLOIT_URI_BASE + opt.getStringValue();
                            break;
                        case COIOT_OPTION_GLOBAL_DEVID:
                            devId = opt.getStringValue();
                            break;
                        case COIOT_OPTION_STATUS_VALIDITY:
                            // validity = o.getIntegerValue();
                            break;
                        case COIOT_OPTION_STATUS_SERIAL:
                            serial = opt.getIntegerValue();
                            if (serial == lastSerial) {
                                // ShellyDeviceProfile profile = thingHandler.getProfile();
                                // if ((profile != null) && profile.isSensor) { // work around for Shelly HT

                                // As per specification the serial changes when any sensor data has changed. The App
                                // should ignore any updates with
                                // the same serial. However, as we have seen with the Shelly HT and Shelly 4 Pro this is
                                // not always the case. The
                                // device comes up with an status packet having the same serial, but new payload
                                // information.
                                // Work Around: Packet will only ignore when Serial AND Payload are the same as last
                                // time
                                if (!lastPayload.isEmpty() && !lastPayload.equals(payload)) {
                                    logger.debug(
                                            "{}: Duplicate serial {} will be processed, because payload is different: {} vs. {}",
                                            thingName, serial, payload, lastPayload);
                                    break;
                                }
                                logger.debug("{}: Serial {} was already processed, ignore update; payload={}",
                                        thingName, serial, payload);
                                return;
                            }
                            break;
                        default:
                            logger.debug("{} ({}): COAP option {} with value {} skipped", thingName, devId,
                                    opt.getNumber(), opt.getValue());
                    }
                    i++;
                }

                if (uri.equalsIgnoreCase(COLOIT_URI_DEVDESC) || (uri.isEmpty() && payload.contains(COIOT_TAG_BLK))) {
                    handleDeviceDescription(devId, payload);
                } else if (uri.equalsIgnoreCase(COLOIT_URI_DEVSTATUS)
                        || (uri.isEmpty() && payload.contains(COIOT_TAG_GENERIC))) {
                    handleStatusUpdate(devId, payload, serial);
                }
            } else {
                // error handling
                logger.debug("{}: Unknown Response Code {} received, payload={}", thingName, response.getCode(),
                        response.getPayloadString());
            }

            if (reqStatus == null) {
                /*
                 * Observe Status Updates
                 */
                reqStatus = sendRequest(reqStatus, config.deviceIp, COLOIT_URI_DEVSTATUS, Type.NON);
            }
        } catch (NullPointerException | IOException e) {
            logger.debug("{}: Unable to process CoIoT Message: {} ({}); payload={}\n{}", thingName, e.getMessage(),
                    e.getClass(), payload, e.getStackTrace());
            resetSerial();
        }
    }

    /**
     * Process a CoIoT device description message. This includes definitions on device units (Relay0, Relay1, Sensors
     * etc.) as well as a definition of
     * sensors and actors. This information needs to be stored allowing to map ids from status updates to the device
     * units and matching the correct
     * thing channel.
     *
     * @param payload Device desciption in JSon format, example:
     *            {"blk":[{"I":0,"D":"Relay0"}],"sen":[{"I":112,"T":"Switch","R":"0/1","L":0}],"act":[{"I":211,"D":"Switch","L":0,"P":[{"I":2011,"D":"ToState","R":"0/1"}]}]}
     *
     * @param devId The device id reported in the CoIoT message.
     */
    private void handleDeviceDescription(String devId, String payload) {
        // Device description
        // payload = StringUtils.substringBefore(payload, "}]}]}") + "}]}]}";
        logger.debug("{}: CoIoT Device Description for {}: {}", thingName, devId, payload);

        // Decode Json
        @Nullable
        CoIotDevDescription descr = gson.fromJson(payload, CoIotDevDescription.class);
        Validate.notNull(descr);

        int i;
        for (i = 0; i < descr.blk.size(); i++) {
            CoIotDescrBlk blk = descr.blk.get(i);
            logger.debug("{}:    id={}: {}", thingName, blk.id, blk.desc);
            if (!blockMap.containsKey(blk.id)) {
                blockMap.put(blk.id, blk);
            } else {
                blockMap.replace(blk.id, blk);
            }
            if ((blk.type != null) && !blk.type.isEmpty()) {
                // in fact it is a sen entry - that's vioaling the Spec
                logger.trace("{}:    fix: auto-create sensor definition for id {}/{}!", thingName, blk.id, blk.desc);
                CoIotDescrSen sen = new CoIotDescrSen();
                sen.id = blk.id;
                sen.desc = blk.desc;
                sen.type = blk.type;
                sen.range = blk.range;
                sen.links = blk.links;
                addSensor(sen);
            }
        }
        logger.debug("{}: Adding {} sensor definitions", thingName, descr.sen.size());
        if (descr.sen != null) {
            for (i = 0; i < descr.sen.size(); i++) {
                addSensor(descr.sen.get(i));
            }
        }

        // Save to thing properties
        thingHandler.updateProperties(PROPERTY_COAP_DESCR, payload);
    }

    private void addSensor(CoIotDescrSen sen) {
        logger.debug("{}:    id {}: {}, Type={}, Range={}, Links={}", thingName, sen.id, sen.desc, sen.type, sen.range,
                sen.links);
        try {
            CoIotDescrSen fixed = fixDescription(sen);
            if (!sensorMap.containsKey(fixed.id)) {
                sensorMap.put(sen.id, fixed);
            } else {
                sensorMap.replace(sen.id, fixed);
            }
        } catch (NullPointerException e) {
            logger.debug("{}:    Unable to decode sensor definition -> skip ({})\n{}", thingName, e.getMessage(),
                    e.getStackTrace());
        }
    }

    /**
     * Process CoIoT status update message. If a status update is received, but the
     * device description has not been received yet a GET is send to query device
     * description.
     *
     * @param devId device id included in the status packet
     * @param payload Coap payload (Json format), example: {"G":[[0,112,0]]}
     * @param serial Serial for this request. If this the the same as last serial
     *            the update was already sent and processed so this one gets
     *            ignored.
     * @throws IOException Exception on sending GET for device description.
     */
    @SuppressWarnings({ "null", "unused" })
    private void handleStatusUpdate(String devId, String payload, int serial) throws IOException {
        // payload = StringUtils.substringBefore(payload, "]]}") + "]]}";
        logger.debug("{}: CoIoT Sensor data {}", thingName, payload);
        if (blockMap.size() == 0) {
            // send discovery packet
            resetSerial();
            reqDescription = sendRequest(reqDescription, config.deviceIp, COLOIT_URI_DEVDESC, Type.CON);

            // try to uses description from last initialization
            String savedDescr = thingHandler.getProperty(PROPERTY_COAP_DESCR);
            if (savedDescr.isEmpty()) {
                logger.debug("{}: Device description not yet received, trigger auto-initialization", thingName);
                return;
            }

            // simulate received device description to create element table
            handleDeviceDescription(devId, savedDescr);
            logger.debug("{}: Device description for {} restored: {}", thingName, devId, savedDescr);
        }

        // Parse Json,
        CoIotGenericSensorList list = gson.fromJson(payload, CoIotGenericSensorList.class);
        Validate.notNull(list, "sensor list must not be empty!");
        Map<String, State> updates = new HashMap<String, State>();

        if (list.generic == null) {
            logger.debug("{}: Sensor list is empty! Payload: {}", devId, payload);
            return;
        }

        Validate.notNull(thingHandler, "thingHandler must not be null!");
        ShellyDeviceProfile profile = thingHandler.getProfile();
        if (profile == null) {
            logger.debug("{}: Thing not initialized yet, skip update (ID={})", thingName, devId);
            thingHandler.requestUpdates(1, true);
            return;
        }

        logger.debug("{}: {} status updates received", thingName, new Integer(list.generic.size()).toString());
        for (int i = 0; i < list.generic.size(); i++) {
            CoIotSensor s = list.generic.get(i);
            CoIotDescrSen sen = sensorMap.get(s.index);
            if (sen != null) {
                // find matching sensor definition from device description, use the Link ID as
                // index
                Validate.notNull(sen.links != null, "Coap: sen.L must not be null!");
                sen = fixDescription(sen);
                CoIotDescrBlk element = blockMap.get(sen.links);
                logger.debug("{}:  Sensor value[{}]: Index={}, Value={} ({}, Type={}, Range={}, Link={}: {})",
                        thingName, i, s.index, s.value, sen.desc, sen.type, sen.range, sen.links,
                        element != null ? element.desc : "n/a");

                // Process status information and convert into channel updates
                String type = (element != null ? element.desc : "").toLowerCase();
                Integer rIndex = Integer.parseInt(sen.links) + 1;
                String rGroup = profile.numRelays <= 1 ? CHANNEL_GROUP_RELAY_CONTROL
                        : CHANNEL_GROUP_RELAY_CONTROL + rIndex;

                switch (sen.type.toLowerCase()) /* CoIoT_STypes.valueOf(sen.T) */ {
                    case "b" /* BatteryLevel */:
                        updateChannel(updates, CHANNEL_GROUP_BATTERY, CHANNEL_SENSOR_BAT_LEVEL,
                                toQuantityType(s.value, DIGITS_PERCENT, SmartHomeUnits.PERCENT));
                        break;
                    case "t" /* Temperature */:
                        updateChannel(updates, CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_TEMP,
                                toQuantityType(s.value, DIGITS_TEMP, SIUnits.CELSIUS));
                        break;
                    case "h" /* Humidity */:
                        updateChannel(updates, CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_HUM,
                                toQuantityType(s.value, DIGITS_PERCENT, SmartHomeUnits.PERCENT));
                        break;
                    case "m" /* Motion */:
                        updateChannel(updates, CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_MOTION,
                                s.value == 1 ? OnOffType.ON : OnOffType.OFF);
                        break;
                    case "l" /* Luminosity */:
                        updateChannel(updates, CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_LUX,
                                toQuantityType(s.value, DIGITS_LUX, SmartHomeUnits.LUX));
                        break;
                    case "w" /* Watt */:
                        String mGroup = profile.numMeters == 1 ? CHANNEL_GROUP_METER : CHANNEL_GROUP_METER + rIndex;
                        updateChannel(updates, mGroup, CHANNEL_METER_CURRENTWATTS,
                                toQuantityType(s.value, DIGITS_WATT, SmartHomeUnits.WATT));
                        if (profile.isEMeter) {
                            updateChannel(updates, mGroup, CHANNEL_LAST_UPDATE, getTimestamp());
                        }
                        break;
                    case "o": // Overtemp
                        // will be handled by status update
                        break;

                    case "tc": /* Temp Celsius */
                    case "tf": /* Temp Fahrenheit */
                        /*
                         * It seems that tC and tF are the device temperature - currently no channel
                         */
                        break;

                    case "s" /* CatchAll */:
                        switch (sen.desc.toLowerCase()) {
                            case "relay0": // Shelly1
                            case "state":
                            case "switch":
                            case "output":
                            case "vswitch": // ???
                                updateChannel(updates, rGroup, CHANNEL_OUTPUT,
                                        s.value == 1 ? OnOffType.ON : OnOffType.OFF);
                                break;
                            case "position":
                                // work around: Roller reports 101% instead max 100
                                double pos = Math.max(SHELLY_MIN_ROLLER_POS, Math.min(s.value, SHELLY_MAX_ROLLER_POS));
                                updateChannel(updates, CHANNEL_GROUP_ROL_CONTROL, CHANNEL_ROL_CONTROL_CONTROL,
                                        toQuantityType(SHELLY_MAX_ROLLER_POS - pos, SmartHomeUnits.PERCENT));
                                updateChannel(updates, CHANNEL_GROUP_ROL_CONTROL, CHANNEL_ROL_CONTROL_POS,
                                        toQuantityType(pos, SmartHomeUnits.PERCENT));
                                break;
                            case "input":
                                Integer idx = getInputId(sen.id);
                                String iGroup = rGroup;
                                String iChannel = CHANNEL_INPUT;
                                if (idx != null) {
                                    if (profile.isDimmer || profile.isRoller) {
                                        // Dimmer and Roller things have 2 inputs
                                        iChannel = CHANNEL_INPUT + idx.toString();
                                    } else {
                                        // Device has 1 input per relay: 0=off, 1+2 depend on switch mode
                                        iGroup = profile.numRelays <= 1 ? CHANNEL_GROUP_RELAY_CONTROL
                                                : CHANNEL_GROUP_RELAY_CONTROL + idx;
                                    }
                                }
                                updateChannel(updates, iGroup, iChannel, s.value == 0 ? OnOffType.OFF : OnOffType.ON);
                                break;

                            case "flood":
                                updateChannel(updates, CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_FLOOD,
                                        s.value == 1 ? OnOffType.ON : OnOffType.OFF);
                                break;
                            case "brightness": // Dimmer
                                updateChannel(updates, rGroup, CHANNEL_BRIGHTNESS,
                                        toQuantityType(s.value, SmartHomeUnits.PERCENT));
                                break;
                            case "charger": // Sense
                                updateChannel(updates, CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_CHARGER,
                                        s.value == 1 ? OnOffType.ON : OnOffType.OFF);
                                break;

                            // RGBW2/Bulb
                            case "red":
                                updateChannel(updates, CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_RED,
                                        ShellyColorUtils.toPercent((int) s.value));
                                break;
                            case "green":
                                updateChannel(updates, CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_GREEN,
                                        ShellyColorUtils.toPercent((int) s.value));
                                break;
                            case "blue":
                                updateChannel(updates, CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_BLUE,
                                        ShellyColorUtils.toPercent((int) s.value));
                                break;
                            case "white":
                                updateChannel(updates, CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_WHITE,
                                        ShellyColorUtils.toPercent((int) s.value));
                                break;
                            case "gain":
                                updateChannel(updates, CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_GAIN,
                                        ShellyColorUtils.toPercent((int) s.value, SHELLY_MIN_GAIN, SHELLY_MAX_GAIN));
                                break;
                            case "temp":
                                updateChannel(updates, CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_TEMP, ShellyColorUtils
                                        .toPercent((int) s.value, MIN_COLOR_TEMPERATURE, MAX_COLOR_TEMPERATURE));

                                break;

                            default:
                                logger.debug("{}: Update for unknown sensor type {}/{} received", thingName, sen.type,
                                        sen.desc);
                        }
                        break;

                    default:
                        logger.debug("{}: Sensor data for type {} not processed, value={}", thingName, sen.type,
                                s.value);
                        break;
                }
            } else {
                logger.debug("{}: Update for unknown sensor[{}]: Dev={}, Index={}, Value={}", thingName, i, devId,
                        s.index, s.value);
            }
        }

        if (updates.size() > 0) {
            logger.debug("{}: Process {} CoIoT channel updates", thingName, updates.size());
            int i = 0;
            for (Map.Entry<String, State> u : updates.entrySet()) {
                logger.debug("{}:  Update[{}] channel {}, value={} (type {})", thingName, i, u.getKey(), u.getValue(),
                        u.getValue().getClass());
                thingHandler.updateChannel(u.getKey(), u.getValue(), true);
                i++;
            }

            if (!profile.isSensor) {
                // For now the Coap interface is not providing all updates, e.g. currentWatts yes, but not the average
                // values for the 3 mins
                // To prevent confusing the user we schedule a regular REST update shortly
                // This will be removed once Coap returns all values, which have changed since the last update
                if (thingHandler.scheduledUpdates == 0) {
                    thingHandler.requestUpdates(1, false);
                }
            }
        }

        // Remeber serial, new packets with same serial will be ignored
        lastSerial = serial;
        lastPayload = payload;
    }

    private boolean updateChannel(Map<String, State> updates, String group, String channel, State value) {
        State v = (State) thingHandler.getChannelValue(group, channel);
        if ((v != null) && v.equals(value)) {
            return false;
        }
        logger.trace("{}: Updating channel {}.{} from CoIoT, new value={}", thingName, group, channel, value);
        updates.put(mkChannelId(group, channel), value);
        return true;

    }

    /**
     * Work around to fix inconsistent sensor types and description
     * Shelly not uses always the same coding for sen.T and sen.D - this helps to unify the format and simplifies
     * processing
     *
     * @param sen
     * @return updated sen
     */
    private CoIotDescrSen fixDescription(CoIotDescrSen sen) {
        Validate.notNull(sen, "sen must not be null!");

        // Shelly1: reports null descr+type "Switch" -> map to S
        // Shelly1PM: reports null descr+type "Overtemp" -> map to O
        // Shelly1PM: reports null descr+type "W" -> add description
        // Shelly1PM: reports temp senmsors without desc -> add description
        // Shelly Dimmer: sensors are reported without descriptions -> map to S
        // SHelly Sense: multiple issues: Description should not be lower case, invalid type for Motion and Battery
        // Shelly Sense: Battery is reported with Desc "battery", but type "H" instead of "B"
        // Shelly Sense: Motion is reported with Desc "battery", but type "H" instead of "B"
        // Shelly Bulb: Colors are coded with Type="Red" etc. rather than Type="S" and color as Descr
        if (sen.desc == null) {
            sen.desc = "";
        }

        switch (sen.desc.toLowerCase()) {
            case "motion": // fix acc to spec it's T=M
                sen.type = "M";
                sen.desc = "Motion";
                break;
            case "battery": // fix: type is B not H
                sen.type = "B";
                sen.desc = "Battery";
                break;
        }

        if (sen.desc.isEmpty()) {
            switch (sen.type.toLowerCase()) {
                case "w":
                    sen.desc = "Power";
                    break;
                case "switch":
                case "relay0":
                    sen.type = "S";
                    sen.desc = "Switch";
                    break;
                case "overtemp":
                    sen.type = "O";
                    sen.desc = "Overtemp";
                    break;
                case "input":
                    sen.type = "S";
                    sen.desc = "Input";
                    break;
                case "output":
                    sen.type = "S";
                    sen.desc = "Output";
                    break;
                case "brightness":
                    sen.type = "S";
                    sen.desc = "Brightness";
                    break;
                case "tc":
                case "tf":
                    sen.desc = "Temperature";
                    break;

                case "red":
                case "green":
                case "blue":
                case "white":
                case "gain":
                case "temp": // Bulb: Color temperature
                    sen.desc = sen.type;
                    sen.type = "S";
                    break;

                case "vswitch":
                    // it seems that Shelly tends to break their own spec: T is the description and D is no longer
                    // included
                    // -> map D to sen.T and set CatchAll for T
                    sen.desc = sen.type;
                    sen.type = "S";
                    break;

                // Default: set no description
                // (there are no T values defined in the CoIoT spec)
                case "tostate":
                default:
                    sen.desc = "";
            }
        }
        return sen;
    }

    /**
     * Send a new request (Discovery to get Device Description). Before a pending
     * request will be canceled.
     *
     * @param request The current request (this will be canceled an a new one will
     *            be created)
     * @param ipAddress Device's IP address
     * @param uri The URI we are calling (CoIoT = /cit/d or /cit/s)
     * @param con true: send as CON, false: send as NON
     * @return new packet
     *
     * @throws IOException
     */
    private Request sendRequest(@Nullable Request request, String ipAddress, String uri, Type con) throws IOException {
        if ((request != null) && !request.isCanceled()) {
            request.cancel();
        }

        resetSerial();
        return newRequest(ipAddress, uri, con).send();
    }

    /**
     * Allocate a new Request structure. A message observer will be added to get the
     * callback when a response has been received.
     *
     * @param ipAddress IP address of the device
     * @param uri URI to be addressed
     * @param uri The URI we are calling (CoIoT = /cit/d or /cit/s)
     * @param con true: send as CON, false: send as NON
     * @return new packet
     * @throws IOException
     */
    private Request newRequest(String ipAddress, String uri, Type con) throws IOException {
        // We need to build our own Request to set an empty Token
        Request request = new Request(Code.GET, con);
        request.setURI(completeUrl(ipAddress, uri));
        request.setToken(EMPTY_BYTE);
        request.addMessageObserver(new MessageObserverAdapter() {
            @Override
            public void onResponse(@Nullable Response response) {
                processResponse(response);
            }

            @Override
            public void onCancel() {
                logger.debug("{}: Coap Request was canceled", thingName);
            }

            @Override
            public void onTimeout() {
                logger.debug("{}: Coap Request timed out", thingName);
            }

        });
        return request;

    }

    private void resetSerial() {
        lastSerial = -1;
        lastPayload = "";
    }

    /**
     * Find index of Input id, which is required to map to channel name
     *
     * @param sensorId The id from the sensor update
     * @return Index of found entry (+1 will be the suffix for the channel name) or null if sensorId is not found
     */
    @SuppressWarnings("null")
    @Nullable
    private Integer getInputId(String sensorId) {
        Integer idx = 0;
        for (Map.Entry<String, CoIotDescrSen> se : sensorMap.entrySet()) {
            @Nullable
            CoIotDescrSen sen = se.getValue();
            if (sen.id.equalsIgnoreCase("Input")) {
                idx++; // iterate from input1..2..n
            }
            if (sen.id.equalsIgnoreCase(sensorId)) {
                CoIotDescrBlk blk = blockMap.get(sen.links);
                if ((blk != null) && StringUtils.substring(blk.desc, 5).equalsIgnoreCase("Relay")) {
                    idx = Integer.parseInt(StringUtils.substringAfter(blk.desc, "Relay"));
                }
                logger.trace("{}:    map to input{} channel", thingName, idx);
                return idx;
            }
        }
        logger.debug("{}: sensorId {} not found in sensorMap!", thingName, sensorId);
        return null;
    }

    /**
     * Cancel pending requests and shutdown the client
     */
    @SuppressWarnings("null")
    public void stop() {
        logger.debug("{}: Stop CoapHandler instance", thingName);
        if ((reqDescription != null) && !reqDescription.isCanceled()) {
            reqDescription.cancel();
            reqDescription = null;
        }
        if ((reqStatus != null) && !reqStatus.isCanceled()) {
            reqStatus.cancel();
            reqStatus = null;
        }
        if (statusClient != null) {
            statusClient.shutdown();
            statusClient = null;
        }
        coapServer.removeListener(this);
    }

    public void dispose() {
        stop();
    }

    private static String completeUrl(String ipAddress, String uri) {
        return "coap://" + ipAddress + ":" + COIOT_PORT + uri;

    }
}
