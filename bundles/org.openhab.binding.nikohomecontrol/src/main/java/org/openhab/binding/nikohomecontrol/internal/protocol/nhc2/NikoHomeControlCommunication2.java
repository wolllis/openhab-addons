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
package org.openhab.binding.nikohomecontrol.internal.protocol.nhc2;

import static org.openhab.binding.nikohomecontrol.internal.protocol.NikoHomeControlConstants.*;

import java.lang.reflect.Type;
import java.net.InetAddress;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.io.transport.mqtt.MqttConnectionObserver;
import org.eclipse.smarthome.io.transport.mqtt.MqttConnectionState;
import org.eclipse.smarthome.io.transport.mqtt.MqttException;
import org.eclipse.smarthome.io.transport.mqtt.MqttMessageSubscriber;
import org.openhab.binding.nikohomecontrol.internal.protocol.NhcControllerEvent;
import org.openhab.binding.nikohomecontrol.internal.protocol.NikoHomeControlCommunication;
import org.openhab.binding.nikohomecontrol.internal.protocol.NikoHomeControlConstants.ActionType;
import org.openhab.binding.nikohomecontrol.internal.protocol.nhc2.NhcDevice2.NhcProperty;
import org.openhab.binding.nikohomecontrol.internal.protocol.nhc2.NhcMessage2.NhcMessageParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

/**
 * The {@link NikoHomeControlCommunication2} class is able to do the following tasks with Niko Home Control II
 * systems:
 * <ul>
 * <li>Start and stop MQTT connection with Niko Home Control II Connected Controller.
 * <li>Read all setup and status information from the Niko Home Control Controller.
 * <li>Execute Niko Home Control commands.
 * <li>Listen for events from Niko Home Control.
 * </ul>
 *
 * @author Mark Herwege - Initial Contribution
 */
@NonNullByDefault
public class NikoHomeControlCommunication2 extends NikoHomeControlCommunication
        implements MqttMessageSubscriber, MqttConnectionObserver {

    private final Logger logger = LoggerFactory.getLogger(NikoHomeControlCommunication2.class);

    private final NhcMqttConnection2 mqttConnection;

    private final List<NhcProfile2> profiles = new CopyOnWriteArrayList<>();
    private final List<NhcService2> services = new CopyOnWriteArrayList<>();

    private volatile String profile = "";

    private volatile @Nullable NhcSystemInfo2 nhcSystemInfo;
    private volatile @Nullable NhcTimeInfo2 nhcTimeInfo;

    private volatile @Nullable CompletableFuture<Boolean> communicationStarted;

    private ScheduledExecutorService scheduler;

    private final Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();

    /**
     * Constructor for Niko Home Control communication object, manages communication with
     * Niko Home Control II Connected Controller.
     *
     * @throws CertificateException when the SSL context for MQTT communication cannot be created
     * @throws UnknownHostException when the IP address is not provided
     *
     */
    public NikoHomeControlCommunication2(NhcControllerEvent handler, String clientId,
            ScheduledExecutorService scheduler) throws CertificateException {
        super(handler);
        mqttConnection = new NhcMqttConnection2(clientId, this, this);
        this.scheduler = scheduler;
    }

    @Override
    public synchronized void startCommunication() {
        communicationStarted = new CompletableFuture<>();

        InetAddress addr = handler.getAddr();
        if (addr == null) {
            logger.warn("Niko Home Control: IP address cannot be empty");
            stopCommunication();
            return;
        }
        String addrString = addr.getHostAddress();
        int port = handler.getPort();
        logger.debug("Niko Home Control: initializing for mqtt connection to CoCo on {}:{}", addrString, port);

        profile = handler.getProfile();

        String token = handler.getToken();
        if (token.isEmpty()) {
            logger.warn("Niko Home Control: JWT token cannot be empty");
            stopCommunication();
            return;
        }

        try {
            mqttConnection.startConnection(addrString, port, profile, token);
            initialize();
        } catch (MqttException e) {
            logger.warn("Niko Home Control: error in mqtt communication");
            stopCommunication();
        }
    }

    @Override
    public synchronized void stopCommunication() {
        CompletableFuture<Boolean> started = communicationStarted;
        if (started != null) {
            started.complete(false);
        }
        communicationStarted = null;
        mqttConnection.stopConnection();
    }

    @Override
    public boolean communicationActive() {
        CompletableFuture<Boolean> started = communicationStarted;
        if (started == null) {
            return false;
        }
        try {
            // Wait until we received all devices info to confirm we are active.
            return started.get(5000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.debug("Niko Home Control: exception waiting for connection start");
            return false;
        }
    }

    /**
     * After setting up the communication with the Niko Home Control Connected Controller, send all initialization
     * messages.
     *
     */
    private void initialize() throws MqttException {
        NhcMessage2 message = new NhcMessage2();

        message.method = "systeminfo.publish";
        mqttConnection.connectionPublish(profile + "/system/cmd", gson.toJson(message));

        message.method = "profiles.list";
        mqttConnection.connectionPublish("public/authentication/cmd", gson.toJson(message));

        message.method = "services.list";
        mqttConnection.connectionPublish(profile + "/authentication/cmd", gson.toJson(message));

        message.method = "devices.list";
        mqttConnection.connectionPublish(profile + "/control/devices/cmd", gson.toJson(message));

        message.method = "notifications.list";
        mqttConnection.connectionPublish(profile + "/notification/cmd", gson.toJson(message));
    }

    private void connectionLost() {
        logger.debug("Niko Home Control: connection lost");
        stopCommunication();
        handler.controllerOffline();
    }

    private void systemEvt(String response) {
        Type messageType = new TypeToken<NhcMessage2>() {
        }.getType();
        List<NhcTimeInfo2> timeInfo = null;
        List<NhcSystemInfo2> systemInfo = null;
        try {
            NhcMessage2 message = gson.fromJson(response, messageType);
            List<NhcMessageParam> messageParams = message.params;
            if (messageParams != null) {
                timeInfo = messageParams.stream().filter(p -> (p.timeInfo != null)).findFirst().get().timeInfo;
                systemInfo = messageParams.stream().filter(p -> (p.systemInfo != null)).findFirst().get().systemInfo;
            }
        } catch (JsonSyntaxException e) {
            logger.debug("Niko Home Control: unexpected json {}", response);
        } catch (NoSuchElementException ignore) {
            // Ignore if timeInfo not present in response, this should not happen in a timeInfo response
        }
        if (timeInfo != null) {
            nhcTimeInfo = timeInfo.get(0);
        }
        if (systemInfo != null) {
            nhcSystemInfo = systemInfo.get(0);
            handler.updatePropertiesEvent();
        }
    }

    private void systeminfoPublishRsp(String response) {
        Type messageType = new TypeToken<NhcMessage2>() {
        }.getType();
        List<NhcSystemInfo2> systemInfo = null;
        try {
            NhcMessage2 message = gson.fromJson(response, messageType);
            List<NhcMessageParam> messageParams = message.params;
            if (messageParams != null) {
                systemInfo = messageParams.stream().filter(p -> (p.systemInfo != null)).findFirst().get().systemInfo;
            }
        } catch (JsonSyntaxException e) {
            logger.debug("Niko Home Control: unexpected json {}", response);
        } catch (NoSuchElementException ignore) {
            // Ignore if systemInfo not present in response, this should not happen in a systemInfo response
        }
        if (systemInfo != null) {
            nhcSystemInfo = systemInfo.get(0);
        }
    }

    private void profilesListRsp(String response) {
        Type messageType = new TypeToken<NhcMessage2>() {
        }.getType();
        List<NhcProfile2> profileList = null;
        try {
            NhcMessage2 message = gson.fromJson(response, messageType);
            List<NhcMessageParam> messageParams = message.params;
            if (messageParams != null) {
                profileList = messageParams.stream().filter(p -> (p.profiles != null)).findFirst().get().profiles;
            }
        } catch (JsonSyntaxException e) {
            logger.debug("Niko Home Control: unexpected json {}", response);
        } catch (NoSuchElementException ignore) {
            // Ignore if profiles not present in response, this should not happen in a profiles response
        }
        profiles.clear();
        if (profileList != null) {
            profiles.addAll(profileList);
        }
    }

    private void servicesListRsp(String response) {
        Type messageType = new TypeToken<NhcMessage2>() {
        }.getType();
        List<NhcService2> serviceList = null;
        try {
            NhcMessage2 message = gson.fromJson(response, messageType);
            List<NhcMessageParam> messageParams = message.params;
            if (messageParams != null) {
                serviceList = messageParams.stream().filter(p -> (p.services != null)).findFirst().get().services;
            }
        } catch (JsonSyntaxException e) {
            logger.debug("Niko Home Control: unexpected json {}", response);
        } catch (NoSuchElementException ignore) {
            // Ignore if services not present in response, this should not happen in a services response
        }
        services.clear();
        if (serviceList != null) {
            services.addAll(serviceList);
        }
    }

    private void devicesListRsp(String response) {
        Type messageType = new TypeToken<NhcMessage2>() {
        }.getType();
        List<NhcDevice2> deviceList = null;
        try {
            NhcMessage2 message = gson.fromJson(response, messageType);
            List<NhcMessageParam> messageParams = message.params;
            if (messageParams != null) {
                deviceList = messageParams.stream().filter(p -> (p.devices != null)).findFirst().get().devices;
            }
        } catch (JsonSyntaxException e) {
            logger.debug("Niko Home Control: unexpected json {}", response);
        } catch (NoSuchElementException ignore) {
            // Ignore if devices not present in response, this should not happen in a devices response
        }
        if (deviceList == null) {
            return;
        }

        for (NhcDevice2 device : deviceList) {
            addDevice(device);
            updateState(device);
        }

        // Once a devices list response is received, we know the communication is fully started.
        logger.debug("Niko Home Control: Communication start complete.");
        handler.controllerOnline();
        CompletableFuture<Boolean> future = communicationStarted;
        if (future != null) {
            future.complete(true);
        }
    }

    private void devicesEvt(String response) {
        Type messageType = new TypeToken<NhcMessage2>() {
        }.getType();
        List<NhcDevice2> deviceList = null;
        String method = null;
        try {
            NhcMessage2 message = gson.fromJson(response, messageType);
            method = message.method;
            List<NhcMessageParam> messageParams = message.params;
            if (messageParams != null) {
                deviceList = messageParams.stream().filter(p -> (p.devices != null)).findFirst().get().devices;
            }
        } catch (JsonSyntaxException e) {
            logger.debug("Niko Home Control: unexpected json {}", response);
        } catch (NoSuchElementException ignore) {
            // Ignore if devices not present in response, this should not happen in a devices event
        }
        if (deviceList == null) {
            return;
        }

        if ("devices.removed".equals(method)) {
            deviceList.forEach(this::removeDevice);
            return;
        } else if ("devices.added".equals(method)) {
            deviceList.forEach(this::addDevice);
        }

        deviceList.forEach(this::updateState);
    }

    private void notificationEvt(String response) {
        Type messageType = new TypeToken<NhcMessage2>() {
        }.getType();
        List<NhcNotification2> notificationList = null;
        try {
            NhcMessage2 message = gson.fromJson(response, messageType);
            List<NhcMessageParam> messageParams = message.params;
            if (messageParams != null) {
                notificationList = messageParams.stream().filter(p -> (p.notifications != null)).findFirst()
                        .get().notifications;
            }
        } catch (JsonSyntaxException e) {
            logger.debug("Niko Home Control: unexpected json {}", response);
        } catch (NoSuchElementException ignore) {
            // Ignore if notifications not present in response, this should not happen in a notifications event
        }
        logger.debug("Niko Home Control: notifications {}", notificationList);
        if (notificationList == null) {
            return;
        }

        for (NhcNotification2 notification : notificationList) {
            if ("new".equals(notification.status)) {
                String alarmText = notification.text;
                switch (notification.type) {
                    case "alarm":
                        handler.alarmEvent(alarmText);
                        break;
                    case "notification":
                        handler.noticeEvent(alarmText);
                        break;
                    default:
                        logger.debug("Niko Home Control: unexpected message type {}", notification.type);
                }
            }
        }
    }

    private void addDevice(NhcDevice2 device) {
        String location;
        location = device.parameters.stream().map(p -> p.locationName).filter(Objects::nonNull).findFirst()
                .orElse(null);

        if ("action".equals(device.type)) {
            if (!actions.containsKey(device.uuid)) {
                logger.debug("Niko Home Control: adding action device {}, {}", device.uuid, device.name);

                ActionType actionType;
                switch (device.model) {
                    case "generic":
                    case "pir":
                    case "simulation":
                    case "comfort":
                    case "alarms":
                    case "alloff":
                    case "overallcomfort":
                    case "garagedoor":
                        actionType = ActionType.TRIGGER;
                        break;
                    case "light":
                    case "socket":
                    case "switched-generic":
                    case "switched-fan":
                        actionType = ActionType.RELAY;
                        break;
                    case "dimmer":
                        actionType = ActionType.DIMMER;
                        break;
                    case "rolldownshutter":
                    case "sunblind":
                    case "venetianblind":
                    case "gate":
                        actionType = ActionType.ROLLERSHUTTER;
                        break;
                    default:
                        actionType = ActionType.GENERIC;
                        logger.debug("Niko Home Control: device type {} not recognised, default to GENERIC action",
                                device.type);
                }

                NhcAction2 nhcAction = new NhcAction2(device.uuid, device.name, device.model, device.technology,
                        actionType, location, this);
                actions.put(device.uuid, nhcAction);
            }
        } else if ("thermostat".equals(device.type) || "hvac".contentEquals(device.type)) {
            if (!thermostats.containsKey(device.uuid)) {
                logger.debug("Niko Home Control: adding thermostat device {}, {}", device.uuid, device.name);

                NhcThermostat2 nhcThermostat = new NhcThermostat2(device.uuid, device.name, device.model,
                        device.technology, location, this);
                thermostats.put(device.uuid, nhcThermostat);
            }
        } else if ("centralmeter".equals(device.type)) {
            logger.debug("Niko Home Control: adding centralmeter device {}, {}", device.uuid, device.name);
            NhcEnergyMeter2 nhcEnergyMeter = new NhcEnergyMeter2(device.uuid, device.name, device.model,
                    device.technology, this, scheduler);
            energyMeters.put(device.uuid, nhcEnergyMeter);
        } else {
            logger.debug("Niko Home Control: device type {} not supported for {}, {}", device.type, device.uuid,
                    device.name);
        }
    }

    private void removeDevice(NhcDevice2 device) {
        if (actions.containsKey(device.uuid)) {
            actions.get(device.uuid).actionRemoved();
            actions.remove(device.uuid);
        } else if (thermostats.containsKey(device.uuid)) {
            thermostats.get(device.uuid).thermostatRemoved();
            thermostats.remove(device.uuid);
        } else if (energyMeters.containsKey(device.uuid)) {
            energyMeters.get(device.uuid).energyMeterRemoved();
            energyMeters.remove(device.uuid);
        }
    }

    private void updateState(NhcDevice2 device) {
        if (actions.containsKey(device.uuid)) {
            updateActionState((NhcAction2) actions.get(device.uuid), device);
        } else if (thermostats.containsKey(device.uuid)) {
            updateThermostatState((NhcThermostat2) thermostats.get(device.uuid), device);
        } else if (energyMeters.containsKey(device.uuid)) {
            updateEnergyMeterState((NhcEnergyMeter2) energyMeters.get(device.uuid), device);
        }
    }

    private void updateActionState(NhcAction2 action, NhcDevice2 device) {
        if (action.getType() == ActionType.ROLLERSHUTTER) {
            updateRollershutterState(action, device);
        } else {
            updateLightState(action, device);
        }
    }

    private void updateLightState(NhcAction2 action, NhcDevice2 device) {
        Optional<NhcProperty> statusProperty = device.properties.stream().filter(p -> (p.status != null)).findFirst();
        Optional<NhcProperty> dimmerProperty = device.properties.stream().filter(p -> (p.brightness != null))
                .findFirst();
        Optional<NhcProperty> basicStateProperty = device.properties.stream().filter(p -> (p.basicState != null))
                .findFirst();

        String booleanState = null;
        if (statusProperty.isPresent()) {
            booleanState = statusProperty.get().status;
        } else if (basicStateProperty.isPresent()) {
            booleanState = basicStateProperty.get().basicState;
        }

        if (booleanState != null) {
            if (NHCON.equals(booleanState)) {
                action.setBooleanState(true);
                logger.debug("Niko Home Control: setting action {} internally to ON", action.getId());
            } else if (NHCOFF.equals(booleanState)) {
                action.setBooleanState(false);
                logger.debug("Niko Home Control: setting action {} internally to OFF", action.getId());
            }
        }

        if (dimmerProperty.isPresent()) {
            action.setState(Integer.parseInt(dimmerProperty.get().brightness));
            logger.debug("Niko Home Control: setting action {} internally to {}", action.getId(),
                    dimmerProperty.get().brightness);
        }
    }

    private void updateRollershutterState(NhcAction2 action, NhcDevice2 device) {
        Optional<NhcProperty> positionProperty = device.properties.stream().filter(p -> (p.position != null))
                .findFirst();
        Optional<NhcProperty> movingProperty = device.properties.stream().filter(p -> (p.moving != null)).findFirst();

        if (!(movingProperty.isPresent() && Boolean.parseBoolean(movingProperty.get().moving))
                && positionProperty.isPresent()) {
            action.setState(Integer.parseInt(positionProperty.get().position));
            logger.debug("Niko Home Control: setting action {} internally to {}", action.getId(),
                    positionProperty.get().position);
        }
    }

    private void updateThermostatState(NhcThermostat2 thermostat, NhcDevice2 device) {
        Optional<Boolean> overruleActiveProperty = device.properties.stream().map(p -> p.overruleActive)
                .filter(Objects::nonNull).map(t -> Boolean.parseBoolean(t)).findFirst();
        Optional<Integer> overruleSetpointProperty = device.properties.stream().map(p -> p.overruleSetpoint)
                .filter(Objects::nonNull).map(t -> Math.round(Float.parseFloat(t) * 10)).findFirst();
        Optional<Integer> overruleTimeProperty = device.properties.stream().map(p -> p.overruleTime)
                .filter(Objects::nonNull).map(t -> Math.round(Float.parseFloat(t))).findFirst();
        Optional<String> programProperty = device.properties.stream().map(p -> p.program).filter(Objects::nonNull)
                .findFirst();
        Optional<Integer> setpointTemperatureProperty = device.properties.stream().map(p -> p.setpointTemperature)
                .filter(Objects::nonNull).map(t -> Math.round(Float.parseFloat(t) * 10)).findFirst();
        Optional<NhcProperty> ecoSaveProperty = device.properties.stream().filter(p -> (p.ecoSave != null)).findFirst();
        Optional<Integer> ambientTemperatureProperty = device.properties.stream().map(p -> p.ambientTemperature)
                .filter(Objects::nonNull).map(t -> Math.round(Float.parseFloat(t) * 10)).findFirst();

        Optional<NhcProperty> demandProperty = device.properties.stream()
                .filter(p -> ((p.demand != null) || (p.operationMode != null))).findFirst();

        String modeString = programProperty.orElse("");
        int mode = IntStream.range(0, THERMOSTATMODES.length).filter(i -> THERMOSTATMODES[i].equals(modeString))
                .findFirst().orElse(thermostat.getMode());

        int measured = ambientTemperatureProperty.orElse(thermostat.getMeasured());
        int setpoint = setpointTemperatureProperty.orElse(thermostat.getSetpoint());

        int overrule = 0;
        int overruletime = 0;
        if (overruleActiveProperty.orElse(false)) {
            overrule = overruleSetpointProperty.orElse(0);
            overruletime = overruleTimeProperty.orElse(0);
        }

        int ecosave = ecoSaveProperty.isPresent() ? ("True".equals(ecoSaveProperty.get().ecoSave) ? 1 : 0)
                : thermostat.getEcosave();

        int demand = thermostat.getDemand();
        if (demandProperty.isPresent()) {
            String demandString;
            if (demandProperty.get().demand != null) {
                demandString = demandProperty.get().demand;
            } else {
                demandString = demandProperty.get().operationMode;
            }
            switch (demandString) {
                case "None":
                    demand = 0;
                    break;
                case "Heating":
                    demand = 1;
                    break;
                case "Cooling":
                    demand = -1;
                    break;
            }
        }

        logger.debug(
                "Niko Home Control: setting thermostat {} with measured {}, setpoint {}, mode {}, overrule {}, overruletime {}, ecosave {}, demand {}",
                thermostat.getId(), measured, setpoint, mode, overrule, overruletime, ecosave, demand);
        thermostat.updateState(measured, setpoint, mode, overrule, overruletime, ecosave, demand);
    }

    private void updateEnergyMeterState(NhcEnergyMeter2 energyMeter, NhcDevice2 device) {
        device.properties.stream().map(p -> p.electricalPower).filter(Objects::nonNull).findFirst()
                .ifPresent(electricalPower -> {
                    try {
                        energyMeter.setPower(Integer.parseInt(electricalPower));
                        logger.debug("Niko Home Control: setting energy meter {} power to {}", energyMeter.getId(),
                                electricalPower);
                    } catch (NumberFormatException e) {
                        energyMeter.setPower(null);
                        logger.trace("Niko Home Control: received empty energy meter {} power reading",
                                energyMeter.getId());
                    }
                });

    }

    @Override
    public void executeAction(String actionId, String value) {
        NhcMessage2 message = new NhcMessage2();

        message.method = "devices.control";
        ArrayList<NhcMessageParam> params = new ArrayList<>();
        NhcMessageParam param = new NhcMessageParam();
        params.add(param);
        message.params = params;
        ArrayList<NhcDevice2> devices = new ArrayList<>();
        NhcDevice2 device = new NhcDevice2();
        devices.add(device);
        param.devices = devices;
        device.uuid = actionId;
        device.properties = new ArrayList<>();
        NhcProperty property = new NhcProperty();
        device.properties.add(property);

        NhcAction2 action = (NhcAction2) actions.get(actionId);

        switch (action.getType()) {
            case GENERIC:
            case TRIGGER:
                property.basicState = NHCTRIGGERED;
                break;
            case RELAY:
                property.status = value;
                break;
            case DIMMER:
                if (NHCON.equals(value)) {
                    action.setBooleanState(true); // this will trigger sending the stored brightness value event out
                    property.status = value;
                } else if (NHCOFF.equals(value)) {
                    property.status = value;
                } else {
                    // If the light is off, turn the light on before sending the brightness value, needs to happen
                    // in 2 separate messages.
                    if (!action.booleanState()) {
                        executeAction(actionId, NHCON);
                    }
                    property.brightness = value;
                }
                break;
            case ROLLERSHUTTER:
                if (NHCSTOP.equals(value)) {
                    property.action = value;
                } else if (NHCUP.equals(value)) {
                    property.position = "100";
                } else if (NHCDOWN.equals(value)) {
                    property.position = "0";
                } else {
                    int position = 100 - Integer.parseInt(value);
                    property.position = String.valueOf(position);
                }
                break;
        }

        String topic = profile + "/control/devices/cmd";
        String gsonMessage = gson.toJson(message);
        sendDeviceMessage(topic, gsonMessage);
    }

    @Override
    public void executeThermostat(String thermostatId, String mode) {
        NhcMessage2 message = new NhcMessage2();

        message.method = "devices.control";
        ArrayList<NhcMessageParam> params = new ArrayList<>();
        NhcMessageParam param = new NhcMessageParam();
        params.add(param);
        message.params = params;
        ArrayList<NhcDevice2> devices = new ArrayList<>();
        NhcDevice2 device = new NhcDevice2();
        devices.add(device);
        param.devices = devices;
        device.uuid = thermostatId;
        device.properties = new ArrayList<>();

        NhcProperty overruleActiveProp = new NhcProperty();
        device.properties.add(overruleActiveProp);
        overruleActiveProp.overruleActive = "False";

        NhcProperty program = new NhcProperty();
        device.properties.add(program);
        program.program = mode;

        String topic = profile + "/control/devices/cmd";
        String gsonMessage = gson.toJson(message);
        sendDeviceMessage(topic, gsonMessage);
    }

    @Override
    public void executeThermostat(String thermostatId, int overruleTemp, int overruleTime) {
        NhcMessage2 message = new NhcMessage2();

        message.method = "devices.control";
        ArrayList<NhcMessageParam> params = new ArrayList<>();
        NhcMessageParam param = new NhcMessageParam();
        params.add(param);
        message.params = params;
        ArrayList<NhcDevice2> devices = new ArrayList<>();
        NhcDevice2 device = new NhcDevice2();
        devices.add(device);
        param.devices = devices;
        device.uuid = thermostatId;
        device.properties = new ArrayList<>();

        if (overruleTime > 0) {
            NhcProperty overruleActiveProp = new NhcProperty();
            overruleActiveProp.overruleActive = "True";
            device.properties.add(overruleActiveProp);

            NhcProperty overruleSetpointProp = new NhcProperty();
            overruleSetpointProp.overruleSetpoint = String.valueOf(overruleTemp);
            device.properties.add(overruleSetpointProp);

            NhcProperty overruleTimeProp = new NhcProperty();
            overruleTimeProp.overruleTime = String.valueOf(overruleTime);
            device.properties.add(overruleTimeProp);
        } else {
            NhcProperty overruleActiveProp = new NhcProperty();
            overruleActiveProp.overruleActive = "False";
            device.properties.add(overruleActiveProp);
        }

        String topic = profile + "/control/devices/cmd";
        String gsonMessage = gson.toJson(message);
        sendDeviceMessage(topic, gsonMessage);
    }

    @Override
    public void startEnergyMeter(String energyMeterId) {
        NhcMessage2 message = new NhcMessage2();

        message.method = "devices.control";
        ArrayList<NhcMessageParam> params = new ArrayList<>();
        NhcMessageParam param = new NhcMessageParam();
        params.add(param);
        message.params = params;
        ArrayList<NhcDevice2> devices = new ArrayList<>();
        NhcDevice2 device = new NhcDevice2();
        devices.add(device);
        param.devices = devices;
        device.uuid = energyMeterId;
        device.properties = new ArrayList<>();

        NhcProperty reportInstantUsageProp = new NhcProperty();
        device.properties.add(reportInstantUsageProp);
        reportInstantUsageProp.reportInstantUsage = "True";

        String topic = profile + "/control/devices/cmd";
        String gsonMessage = gson.toJson(message);

        ((NhcEnergyMeter2) energyMeters.get(energyMeterId)).startEnergyMeter(topic, gsonMessage);
    }

    @Override
    public void stopEnergyMeter(String energyMeterId) {
        ((NhcEnergyMeter2) energyMeters.get(energyMeterId)).stopEnergyMeter();
    }

    /**
     * Method called from the {@link NhcEnergyMeter2} object to send message to Niko Home Control.
     *
     * @param topic
     * @param gsonMessage
     */
    public void executeEnergyMeter(String topic, String gsonMessage) {
        sendDeviceMessage(topic, gsonMessage);
    }

    private void sendDeviceMessage(String topic, String gsonMessage) {
        try {
            mqttConnection.connectionPublish(topic, gsonMessage);

        } catch (MqttException e) {
            logger.warn("Niko Home Control: sending command failed, trying to restart communication");
            restartCommunication();
            // retry sending after restart
            try {
                if (communicationActive()) {
                    mqttConnection.connectionPublish(topic, gsonMessage);
                } else {
                    logger.warn("Niko Home Control: failed to restart communication");
                    connectionLost();
                }
            } catch (MqttException e1) {
                logger.warn("Niko Home Control: error resending device command");
                connectionLost();
            }
        }
    }

    @Override
    public void processMessage(String topic, byte[] payload) {
        String message = new String(payload);
        if ("public/system/evt".equals(topic) || (profile + "/system/evt").equals(topic)) {
            systemEvt(message);
        } else if ((profile + "/system/rsp").equals(topic)) {
            logger.debug("Niko Home Control: received topic {}, payload {}", topic, message);
            systeminfoPublishRsp(message);
        } else if ("public/authentication/rsp".equals(topic)) {
            logger.debug("Niko Home Control: received topic {}, payload {}", topic, message);
            profilesListRsp(message);
        } else if ((profile + "/notification/evt").equals(topic)) {
            logger.debug("Niko Home Control: received topic {}, payload {}", topic, message);
            notificationEvt(message);
        } else if ((profile + "/control/devices/evt").equals(topic)) {
            logger.debug("Niko Home Control: received topic {}, payload {}", topic, message);
            devicesEvt(message);
        } else if ((profile + "/control/devices/rsp").equals(topic)) {
            logger.debug("Niko Home Control: received topic {}, payload {}", topic, message);
            devicesListRsp(message);
        } else if ((profile + "/authentication/rsp").equals(topic)) {
            logger.debug("Niko Home Control: received topic {}, payload {}", topic, message);
            servicesListRsp(message);
        } else if ("public/control/devices.error".equals(topic) || (profile + "/control/devices.error").equals(topic)) {
            logger.warn("Niko Home Control: received error {}", message);
        } else {
            logger.trace("Niko Home Control: not acted on received message topic {}, payload {}", topic, message);
        }
    }

    /**
     * @return system info retrieved from Connected Controller
     */
    public NhcSystemInfo2 getSystemInfo() {
        NhcSystemInfo2 systemInfo = nhcSystemInfo;
        if (systemInfo == null) {
            systemInfo = new NhcSystemInfo2();
        }
        return systemInfo;
    }

    /**
     * @return time info retrieved from Connected Controller
     */
    public NhcTimeInfo2 getTimeInfo() {
        NhcTimeInfo2 timeInfo = nhcTimeInfo;
        if (timeInfo == null) {
            timeInfo = new NhcTimeInfo2();
        }
        return timeInfo;
    }

    /**
     * @return comma separated list of profiles with uuid's retrieved from Connected Controller
     */
    public String getProfiles() {
        return profiles.stream().map(NhcProfile2::profile).collect(Collectors.joining(", "));
    }

    /**
     * @return comma separated list of services retrieved from Connected Controller
     */
    public String getServices() {
        return services.stream().map(NhcService2::name).collect(Collectors.joining(", "));
    }

    @Override
    public void connectionStateChanged(MqttConnectionState state, @Nullable Throwable error) {
        if (error != null) {
            logger.debug("Connection state: {}", state, error);
            restartCommunication();
            if (!communicationActive()) {
                logger.warn("Niko Home Control: failed to restart communication");
                connectionLost();
            }
        } else {
            logger.trace("Connection state: {}", state);
        }
    }
}
