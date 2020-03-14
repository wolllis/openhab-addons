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
package org.openhab.binding.shelly.internal.api;

import java.util.ArrayList;

import com.google.gson.annotations.SerializedName;

/**
 * The {@link ShellyApiJsonDTO} is used for the JSon/GSon mapping
 *
 * @author Markus Michels - Initial contribution
 */
public class ShellyApiJsonDTO {

    public static final String SHELLY_API_ON = "on";
    public static final String SHELLY_API_OFF = "off";
    public static final String SHELLY_API_TRUE = "true";
    public static final String SHELLY_API_FALSE = "false";

    public static final String SHELLY_CLASS_RELAY = "relay"; // Relay: relay mode
    public static final String SHELLY_CLASS_ROLLER = "roller"; // Relay: roller mode
    public static final String SHELLY_CLASS_LIGHT = "light"; // Bulb: color mode

    public static class ShellySettingsDevice {
        public String type;
        public String mac;
        public String hostname;
        public String fw;
        public Boolean auth;
        @SerializedName("num_outputs")
        public Integer numOutputs;
        @SerializedName("num_meters")
        public Integer numMeters;
        @SerializedName("num_emeters")
        public Integer numEMeters;
        @SerializedName("num_rollers")
        public Integer numRollers;
    }

    public static class ShellySettingsWiFiAp {
        public Boolean enabled;
        public String ssid;
        public String key;
    }

    public static class ShellySettingsWiFiNetwork {
        public Boolean enabled;
        public String ssid;
        public Integer rssi;

        @SerializedName("ipv4_method")
        public String ipv4Method;
        public final String SHELLY_IPM_STATIC = "static";
        public final String SHELLY_IPM_DHCP = "dhcp";

        public String ip;
        public String gw;
        public String mask;
        public String dns;
    }

    public static class ShellySettingsMqtt {
        public Boolean enabled;
        public String server;
        public String user;
        @SerializedName("reconnect_timeout_max")
        public Double reconnectTimeoutMax;
        @SerializedName("reconnect_timeout_min")
        public Double reconnectTimeoutMin;
        @SerializedName("clean_session")
        public Boolean cleanSession;
        @SerializedName("keep_alive")
        public Integer keepAlive;
        @SerializedName("will_topic")
        public String willTopic;
        @SerializedName("will_message")
        public String willMessage;
        @SerializedName("max_qos")
        public Integer maxQOS;
        public Boolean retain;
        @SerializedName("update_period")
        public Integer updatePeriod;
    }

    public static class ShellySettingsSntp {
        public String server;
    }

    public static class ShellySettingsLogin {
        public Boolean enabled;
        public Boolean unprotected;
        public String username;
        public String password;
    }

    public static class ShellySettingsBuildInfo {
        @SerializedName("build_id")
        public String buildId;
        @SerializedName("build_timestamp")
        public String buildTimestamp;
        @SerializedName("build_version")
        public String buildVersion;
    }

    public static class ShellyStatusCloud {
        public Boolean enabled;
        public Boolean connected;
    }

    public static class ShellyStatusMqtt {
        public Boolean connected;
    }

    public static class ShellySettingsHwInfo {
        @SerializedName("hw_revision")
        public String hwRevision;
        @SerializedName("batch_id")
        public Integer batchId;
    }

    public static class ShellySettingsScheduleRules {
    }

    public static class ShellySettingsRelay {
        public String name;
        public Boolean ison;
        public Boolean overpower;
        @SerializedName("default_state")
        public String defaultState; // Accepted values: off, on, last, switch
        @SerializedName("btn_type")
        public String btnType; // Accepted values: momentary, toggle, edge, detached - // see SHELLY_BTNT_xxx
        @SerializedName("has_timer")
        public Boolean hasTimer; // Whether a timer is currently armed for this channel
        @SerializedName("auto_on")
        public Double autoOn; // Automatic flip back timer, seconds. Will engage after turning Shelly1 OFF.
        @SerializedName("auto_off")
        public Double autoOff; // Automatic flip back timer, seconds. Will engage after turning Shelly1 ON.
        @SerializedName("btn_on_url")
        public String btnOnUrl; // input is activated
        @SerializedName("btnOffUrl")
        public String btnOffUrl; // input is deactivated
        @SerializedName("out_on_url")
        public String outOnUrl; // output is activated
        @SerializedName("out_off_url")
        public String outOffUrl; // output is deactivated
        @SerializedName("roller_open_url")
        public String rollerOpenUrl; // to access when roller reaches open position
        @SerializedName("roller_close_url")
        public String rollerCloseUrl; // to access when roller reaches close position
        @SerializedName("roller_stop_url")
        public String rollerStopUrl; // to access when roller stopped
        @SerializedName("longpush_url")
        public String pushLongUrl; // to access when roller stopped
        @SerializedName("shortpush_url")
        public String pushShortUrl; // to access when roller stopped

        public Boolean schedule;
        // ArrayList<ShellySettingsScheduleRules> schedule_rules;
    }

    public static class ShellySettingsDimmer {
        public String name; // unique name of the device
        public Boolean ison; // true: output is ON
        @SerializedName("default_state")
        public String defaultState; // Accepted values: off, on, last, switch
        @SerializedName("auto_on")
        public Double autoOn; // Automatic flip back timer, seconds. Will engage after turning Shelly1 OFF.
        @SerializedName("auto_off")
        public Double autoOff; // Automatic flip back timer, seconds. Will engage after turning Shelly1 ON.
        @SerializedName("btn1_on_url")
        public String btn1OnUrl; // URL to access when SW input is activated
        @SerializedName("btn1_off_url")
        public String btn1OffUrl; // URL to access when SW input is deactivated
        @SerializedName("btn2_on_url")
        public String btn2OnUrl; // URL to access when SW input is activated
        @SerializedName("btn2_off_url")
        public String btn2OoffUrl; // URL to access when SW input is deactivated
        @SerializedName("out_on_url")
        public String outOnUrl; // URL to access when output is activated
        @SerializedName("out_off_url")
        public String outOffUrl; // URL to access when output is deactivated
        @SerializedName("longpush_url")
        public String pushLongUrl; // long push button event
        @SerializedName("shortpush_url")
        public String pushShortUrl; // short push button event
        @SerializedName("btn_type")
        public String btnType; // Accepted values: momentary, toggle, edge, detached - // see SHELLY_BTNT_xxx
        @SerializedName("swap_inputs")
        public Integer swapInputs; // 0=no
    }

    public static final String SHELLY_API_EVENTURL_BTN_ON = "btn_on_url";
    public static final String SHELLY_API_EVENTURL_BTN_OFF = "btn_off_url";
    public static final String SHELLY_API_EVENTURL_BTN1_ON = "btn1_on_url";
    public static final String SHELLY_API_EVENTURL_BTN1_OFF = "btn1_off_url";
    public static final String SHELLY_API_EVENTURL_BTN2_ON = "btn2_on_url";
    public static final String SHELLY_API_EVENTURL_BTN2_OFF = "btn2_off_url";
    public static final String SHELLY_API_EVENTURL_OUT_ON = "out_on_url";
    public static final String SHELLY_API_EVENTURL_OUT_OFF = "out_off_url";
    public static final String SHELLY_API_EVENTURL_SHORT_PUSH = "shortpush_url";
    public static final String SHELLY_API_EVENTURL_LONG_PUSH = "longpush_url";
    public static final String SHELLY_API_EVENTURL_ROLLER_OPEN = "roller_open_url";
    public static final String SHELLY_API_EVENTURL_ROLLER_CLOSE = "roller_close_url";
    public static final String SHELLY_API_EVENTURL_ROLLER_STOP = "roller_stop_url";
    public static final String SHELLY_API_EVENTURL_REPORT = "report_url";

    public static final String SHELLY_EVENT_BTN_ON = "btn_on";
    public static final String SHELLY_EVENT_BTN_OFF = "btn_off";
    public static final String SHELLY_EVENT_BTN1_OFF = "btn1_on";
    public static final String SHELLY_EVENT_BTN1_ON = "btn1_off";
    public static final String SHELLY_EVENT_BTN2_ON = "btn2_on";
    public static final String SHELLY_EVENT_BTN2_OFF = "btn2_off";
    public static final String SHELLY_EVENT_SHORTPUSH = "shortpush";
    public static final String SHELLY_EVENT_LONGPUSH = "longpush";
    public static final String SHELLY_EVENT_OUT_ON = "out_on";
    public static final String SHELLY_EVENT_OUT_OFF = "out_off";
    public static final String SHELLY_EVENT_ROLLER_OPEN = "roller_open";
    public static final String SHELLY_EVENT_ROLLER_CLOSE = "roller_close";
    public static final String SHELLY_EVENT_ROLLER_STOP = "roller_stop";
    public static final String SHELLY_EVENT_SENSORDATA = "sensordata";

    public static final String SHELLY_BTNT_MOMENTARY = "momentary";
    public static final String SHELLY_BTNT_TOGGLE = "toggle";
    public static final String SHELLY_BTNT_EDGE = "edge";
    public static final String SHELLY_BTNT_DETACHED = "detached";
    public static final String SHELLY_STATE_LAST = "last";
    public static final String SHELLY_STATE_STOP = "stop";
    public static final String SHELLY_INP_MODE_OPENCLOSE = "openclose";
    public static final String SHELLY_OBSTMODE_DISABLED = "disabled";
    public static final String SHELLY_SAFETYM_WHILEOPENING = "while_opening";
    public static final String SHELLY_ALWD_TRIGGER_NONE = "none";
    public static final String SHELLY_ALWD_ROLLER_TURN_OPEN = "open";
    public static final String SHELLY_ALWD_ROLLER_TURN_CLOSE = "close";
    public static final String SHELLY_ALWD_ROLLER_TURN_STOP = "stop";

    public static class ShellySettingsRoller {
        public Double maxtime;
        @SerializedName("maxtime_open")
        public Double maxtimeOpen;
        @SerializedName("maxtime_close")
        public Double maxtimeClose;
        @SerializedName("default_state")
        public String defaultState; // see SHELLY_STATE_xxx
        public Boolean swap;
        @SerializedName("swap_inputs")
        public Boolean swapInputs;
        @SerializedName("input_mode")
        public String inputMode; // see SHELLY_INP_MODE_OPENCLOSE
        @SerializedName("button_type")
        public String buttonType; // // see SHELLY_BTNT_xxx
        @SerializedName("btn_Reverse")
        public Integer btnReverse;
        public String state;
        public Double power;
        @SerializedName("is_valid")
        public Boolean isValid;
        @SerializedName("safety_switch")
        public Boolean safetySwitch;
        public Boolean schedule;
        // ArrayList<ShellySettingsScheduleRules> schedule_rules; // not used for now
        @SerializedName("obstacle_mode")
        public String obstaclMode; // SHELLY_OBSTMODE_
        @SerializedName("obstacle_action")
        public String obstacleAction; // see SHELLY_STATE_xxx
        @SerializedName("obstacle_power")
        public Integer obstaclePower;
        @SerializedName("obstacle_delay")
        public Integer obstacleDelay;
        @SerializedName("safety_mode")
        public String safetyMode; // see SHELLY_SAFETYM_xxx
        @SerializedName("safety_action")
        public String safetyAction; // see SHELLY_STATE_xxx
        @SerializedName("safety_allowed_on_trigger")
        public String safetyAllowedOnTrigger; // see SHELLY_ALWD_TRIGGER_xxx
        @SerializedName("off_power")
        public Integer offPower;
        public Boolean positioning;
    }

    public static class ShellyInputState {
        public Integer input;
    }

    public static class ShellySettingsMeter {
        @SerializedName("is_valid")
        public Boolean isValid;
        public Double power;
        public Double[] counters = { 0.0, 0.0, 0.0 };
        public Double total;
        public Long timestamp;
    }

    public static class ShellySettingsEMeter { // ShellyEM meter
        @SerializedName("is_valid")
        public Boolean isValid; // Whether the associated meter is functioning properly
        public Double power; // Instantaneous power, Watts
        public Double reactive; // Instantaneous reactive power, Watts
        public Double voltage; // RMS voltage, Volts
        public Double total; // Total consumed energy, Wh
        @SerializedName("total_returned")
        public Double totalReturned; // Total returned energy, Wh
    }

    public static class ShellySettingsUpdate {
        public String status;
        @SerializedName("has_update")
        public Boolean hasUpdate;
        @SerializedName("new_version")
        public String newVersion;
        @SerializedName("old_version")
        public String oldVersion;
    }

    public static class ShellySettingsGlobal {
        // https://shelly-api-docs.shelly.cloud/#shelly1pm-settings
        public ShellySettingsDevice device;
        @SerializedName("wifi_ap")
        public ShellySettingsWiFiAp wifiAp;
        @SerializedName("wifi_sta")
        public ShellySettingsWiFiNetwork wifiSta;
        @SerializedName("wifi_sta1")
        public ShellySettingsWiFiNetwork wifiSta1;
        // public ShellySettingsMqtt mqtt; // not used for now
        // public ShellySettingsSntp sntp; // not used for now
        public ShellySettingsLogin login;
        @SerializedName("pin_code")
        public String pinCode;
        @SerializedName("coiot_execute_enable")
        public Boolean coiotExecuteEnable;
        public String name;
        public String fw;
        @SerializedName("build_info")
        ShellySettingsBuildInfo buildInfo;
        ShellyStatusCloud cloud;
        public String timezone;
        public Double lat;
        public Double lng;
        public Boolean tzautodetect;
        public String time;
        public ShellySettingsHwInfo hwinfo;
        public String mode;
        @SerializedName("max_power")
        public Double maxPower;

        public ArrayList<ShellySettingsRelay> relays;
        public ArrayList<ShellySettingsDimmer> dimmers;
        public ArrayList<ShellySettingsEMeter> emeters;
        public ArrayList<ShellyInputState> inputs; // Firmware 1.5.6+

        @SerializedName("led_status_disable")
        public Boolean ledStatusDisable; // PlugS only Disable LED indication for network
                                         // status
        @SerializedName("led_power_disable")
        public Boolean ledPowerDisable; // PlugS only Disable LED indication for network
                                        // status
        @SerializedName("light_sensor")
        public String lightSensor; // Sense: sensor type
        @SerializedName("rain_sensor")
        public Boolean rainSensor; // Flood: true=in rain mode
    }

    public static final String SHELLY_API_MODE = "mode";
    public static final String SHELLY_MODE_RELAY = "relay"; // Relay: relay mode
    public static final String SHELLY_MODE_ROLLER = "roller"; // Relay: roller mode
    public static final String SHELLY_MODE_COLOR = "color"; // Bulb/RGBW2: color mode
    public static final String SHELLY_MODE_WHITE = "white"; // Bulb/RGBW2: white mode

    public static final String SHELLY_LED_STATUS_DISABLE = "led_status_disable";
    public static final String SHELLY_LED_POWER_DISABLE = "led_power_disable";

    public static class ShellySettingsAttributes {
        @SerializedName("device_type")
        public String deviceType; // Device model identifier
        @SerializedName("device_mac")
        public String deviceMac; // MAC address of the device in hexadecimal
        @SerializedName("wifi_ap")
        public String wifiAp; // WiFi access poInteger configuration, see /settings/ap for details
        @SerializedName("wifi_sta")
        public String wifiSta; // WiFi client configuration. See /settings/sta for details
        public String login; // credentials used for HTTP Basic authentication for the REST interface. If
                             // enabled is
                             // true clients must include an Authorization: Basic ... HTTP header with valid
                             // credentials
                             // when performing TP requests.
        public String name; // unique name of the device.
        public String fw; // current FW version
    }

    public static class ShellySettingsStatus {
        @SerializedName("wifi_sta")
        public ShellySettingsWiFiNetwork wifiSta; // WiFi client configuration. See /settings/sta for
                                                  // details

        public String time;
        public Integer serial;
        @SerializedName("has_update")
        public Boolean hasUpdate;
        public String mac;
        public ArrayList<ShellySettingsRelay> relays;
        public ArrayList<ShellySettingsRoller> rollers;
        public Integer input; // RGBW2 has no JSON array
        public ArrayList<ShellyInputState> inputs;
        public ArrayList<ShellySettingsLight> lights;
        public ArrayList<ShellyShortLightStatus> dimmers;
        public ArrayList<ShellySettingsMeter> meters;
        public ArrayList<ShellySettingsEMeter> emeters;

        public ShellyStatusSensor.ShellySensorTmp tmp;
        public Boolean overtemperature;

        // Shelly Dimmer only
        public Boolean loaderror;
        public Boolean overload;

        public ShellySettingsUpdate update;
        @SerializedName("ram_total")
        public Long ramTotal;
        @SerializedName("ram_free")
        public Long ramFree;
        @SerializedName("fs_size")
        public Long fsSize;
        @SerializedName("fs_free")
        public Long fsFree;
        public Long uptime;

        public String json;
    }

    public static class ShellyControlRelay {
        // https://shelly-api-docs.shelly.cloud/#shelly1-1pm-settings-relay-0
        @SerializedName("is_valid")
        public Boolean isValid;
        @SerializedName("has_timer")
        public Boolean hasTimer; // Whether a timer is currently armed for this channel
        public Boolean overpower; // Shelly1PM only if maximum allowed power was exceeded

        public String turn; // Accepted values are on and off. This will turn ON/OFF the respective output
                            // channel when request is sent .
        public Integer timer; // A one-shot flip-back timer in seconds.
    }

    public static class ShellyShortStatusRelay {
        @SerializedName("is_valid")
        public Boolean isValid;
        public Boolean ison; // Whether output channel is on or off
        @SerializedName("has_timer")
        public Boolean hasTimer; // Whether a timer is currently armed for this channel
        public Boolean overpower; // Shelly1PM only if maximum allowed power was exceeded
        public Double temperature; // Internal device temperature
        public Boolean overtemperature; // Device over heated

        @SerializedName("ext_temperature")
        public ShellyStatusSensor.ShellyExtTemperature extTemperature; // Shelly 1/1PM: sensor values
    }

    public static class ShellyShortLightStatus {
        public Boolean ison; // Whether output channel is on or off
        public String mode; // color or white - valid only for Bulb and RGBW2 even Dimmer returns it also
        public Integer brightness; // brightness: 0.100%
    }

    public static class ShellyStatusRelay {
        @SerializedName("wifi_sta")
        public ShellySettingsWiFiNetwork wifiSta; // WiFi status
        // public ShellyStatusCloud cloud; // Cloud status
        // public ShellyStatusMqtt mqtt; // mqtt status
        public String time; // current time
        public Integer serial;
        public String mac; // MAC
        public ArrayList<ShellyShortStatusRelay> relays; // relay status
        public ArrayList<ShellySettingsMeter> meters; // current meter value

        @SerializedName("has_update")
        public Boolean hasUpdate; // If a newer firmware version is available
        public ShellySettingsUpdate update; // /status/firmware value

        @SerializedName("ram_total")
        public Integer ramTotal; // Total and available amount of system memory in bytes
        @SerializedName("ram_free")
        public Integer ramFree;
        @SerializedName("fs_size")
        public Integer fsSize;
        @SerializedName("fs_free")
        public Integer fsFree; // Total and available amount of file system space in bytes
        public Integer uptime; // econds elapsed since boot
    }

    public static class ShellyStatusDimmer {
        @SerializedName("wifi_sta")
        public ShellySettingsWiFiNetwork wifiSta; // WiFi status
        // public ShellyStatusCloud cloud; // Cloud status
        // public ShellyStatusMqtt mqtt; // mqtt status
        public String time; // current time
        public Integer serial;
        public String mac; // MAC
        public ArrayList<ShellyShortLightStatus> lights; // relay status
        public ArrayList<ShellySettingsMeter> meters; // current meter value

        public ShellyStatusSensor.ShellySensorTmp tmp;
        public Boolean overtemperature;

        public Boolean loaderror;
        public Boolean overload;

        @SerializedName("has_update")
        public Boolean hasUpdate; // If a newer firmware version is available
        public ShellySettingsUpdate update; // /status/firmware value

        @SerializedName("ram_total")
        public Integer ramTotal; // Total and available amount of system memory in
                                 // bytes
        @SerializedName("ram_free")
        public Integer ramFree;
        @SerializedName("fs_size")
        public Integer fsSize;
        @SerializedName("fs_free")
        public Integer fsFree; // Total and available amount of file system space in
                               // bytes
        public Integer uptime; // econds elapsed since boot
    }

    public static class ShellyControlRoller {
        @SerializedName("roller_pos")
        public Integer rollerPos; // number Desired position in percent
        public Integer duration; // If specified, the motor will move for this period in seconds. If missing, the
                                 // value of
                                 // maxtime in /settings/roller/N will be used.
        public String state; // One of stop, open, close
        public Double power; // Current power consumption in Watts
        @SerializedName("is_valid")
        public Boolean isValid; // If the power meter functions properly
        @SerializedName("safety_switch")
        public Boolean safetySwitch; // Whether the safety input is currently triggered
        public Boolean overtemperature;
        @SerializedName("stop_reason")
        public String stopReason; // Last cause for stopping: normal, safety_switch, obstacle
        @SerializedName("last_direction")
        public String lastDirection; // Last direction of motion, open or close
        public Boolean calibrating;
        public Boolean positioning; // true when calibration was performed
        @SerializedName("current_pos")
        public Integer currentPos; // current position 0..100, 100=open
    }

    public static final String SHELLY_STOPR_NORMAL = "normal";
    public static final String SHELLY_STOPR_SAFETYSW = "safety_switch";
    public static final String SHELLY_STOPR_OBSTACLE = "obstacle";

    public static class ShellySettingsSensor {
        @SerializedName("temperature_units")
        public String temperatureUnits; // Either'C'or'F'
        @SerializedName("temperature_threshold")
        public Integer temperatureThreshold; // Temperature delta (in configured degree units) which triggers an update
        @SerializedName("humidity_threshold")
        public Integer humidityThreshold; // RH delta in % which triggers an update
        @SerializedName("sleep_mode_period")
        public Integer sleepModePeriod; // Periodic update period in hours, between 1 and 24
        @SerializedName("report_url")
        public String reportUrl; // URL gets posted on updates with sensor data
    }

    public static class ShellyStatusSensor {
        // https://shelly-api-docs.shelly.cloud/#h-amp-t-settings
        public static class ShellySensorTmp {
            public Double value; // Temperature in configured unites
            public String units; // 'C' or 'F'
            public Double tC; // temperature in deg C
            public Double tF; // temperature in deg F
            @SerializedName("is_valid")
            public Boolean isValid; // whether the internal sensor is operating properly
        }

        public static class ShellySensorHum {
            public Double value; // relative humidity in %
        }

        public static class ShellySensorBat {
            public Double value; // estimated remaining battery capacity in %
            public Double voltage; // battery voltage
        };

        public static class ShellySensorLux {
            @SerializedName("is_valid")
            public Boolean isValid; // whether the internal sensor is operating properly
            public Double value;
        }

        public static class ShellyExtTemperature {
            public static class ShellyShortTemp {
                public Double tC; // temperature in deg C
                public Double tF; // temperature in deg F
            }

            // Shelly 1/1PM have up to 3 sensors
            // for whatever reasons it's not an array, but 3 independent elements
            @SerializedName("0")
            public ShellyShortTemp sensor1;
            @SerializedName("1")
            public ShellyShortTemp sensor2;
            @SerializedName("2")
            public ShellyShortTemp sensor3;
        }

        public ShellySensorTmp tmp;
        public ShellySensorHum hum;
        public ShellySensorLux lux;
        public ShellySensorBat bat;

        public Boolean flood; // Shelly Flood: true = flood condition detected
        @SerializedName("rain_sensor")
        public Boolean rainSensor; // Shelly Flood: true=in rain mode

        public Boolean motion; // Shelly Sense: true=motion detected
        public Boolean charger; // Shelly Sense: true=charger connected

        // @SerializedName("act_reasons")
        // public String[] actReasons; // HT/Smoke/Flood: list of reasons which woke up the device
    }

    public static class ShellySettingsSmoke {
        @SerializedName("temperature_units")
        public String temperatureUnits; // Either 'C' or 'F'
        @SerializedName("temperature_threshold")
        public Integer temperatureThreshold; // Temperature delta (in configured degree units) which triggers an update
        @SerializedName("sleep_mode_period")
        public Integer sleepModePeriod; // Periodic update period in hours, between 1 and 24
    }

    public static final String SHELLY_TEMP_CELSIUS = "C";
    public static final String SHELLY_TEMP_FAHRENHEIT = "F";

    public static class ShellySettingsLight {
        public Integer red; // red brightness, 0..255, applies in mode="color"
        public Integer green; // green brightness, 0..255, applies in mode="color"
        public Integer blue; // blue brightness, 0..255, applies in mode="color"
        public Integer white; // white brightness, 0..255, applies in mode="color"
        public Integer gain; // gain for all channels, 0..100, applies in mode="color"
        public Integer temp; // color temperature in K, 3000..6500, applies in mode="white"
        public Integer brightness; // brightness, 0..100, applies in mode="white"
        public Integer effect; // Currently applied effect, description: 0: Off, 1: Meteor Shower, 2: Gradual
                               // Change, 3: Breath,
                               // 4: Flash, 5: On/Off Gradual, 6: Red/Green Change
        @SerializedName("default_state")
        public String defaultState; // one of on, off or last
        @SerializedName("auto_on")
        public Double autoOn; // see above
        @SerializedName("auto_off")
        public Double autoOff; // see above

        public Integer dcpower; // RGW2:Set to true for 24 V power supply, false for 12 V

        // Shelly Dimmer
        public String mode;
        public Boolean ison;
    }

    public static final int SHELLY_MIN_EFFECT = 0;
    public static final int SHELLY_MAX_EFFECT = 6;

    public static class ShellyStatusLightChannel {
        public Boolean ison;
        public Double power;
        public Boolean overpower;
        @SerializedName("auto_on")
        public Double autoOn; // see above
        @SerializedName("auto_off")
        public Double autoOff; // see above

        public Integer red; // red brightness, 0..255, applies in mode="color"
        public Integer green; // green brightness, 0..255, applies in mode="color"
        public Integer blue; // blue brightness, 0..255, applies in mode="color"
        public Integer white; // white brightness, 0..255, applies in mode="color"
        public Integer gain; // gain for all channels, 0..100, applies in mode="color"
        public Integer temp; // color temperature in K, 3000..6500, applies in mode="white"
        public Integer brightness; // brightness, 0..100, applies in mode="white"
        public Integer effect; // Currently applied effect, description: 0: Off, 1: Meteor Shower, 2: Gradual
                               // Change, 3: Breath,
    }

    public static class ShellyStatusLight {
        public Boolean ison; // Whether output channel is on or off
        public ArrayList<ShellyStatusLightChannel> lights;
        public ArrayList<ShellySettingsMeter> meters;

        // not yet used:
        // public String mode; // COLOR or WHITE
        // public Integer input;
        // public Boolean has_update;
        // public ShellySettingsUpdate update;
        // public ShellySettingsWiFiNetwork wifi_sta; // WiFi client configuration. See
        // /settings/sta for details
        // public ShellyStatusCloud cloud;
        // public ShellyStatusMqtt mqtt;
    }

    public static class ShellySenseKeyCode {
        String id; // ID of the stored IR code into Shelly Sense.
        String name; // Short description or name of the stored IR code.
    }

    public static class ShellySendKeyList {
        @SerializedName("key_codes")
        public ArrayList<ShellySenseKeyCode> keyCodes;
    }

    public static final String SHELLY_TIMER_AUTOON = "auto_on";
    public static final String SHELLY_TIMER_AUTOOFF = "auto_off";
    public static final String SHELLY_TIMER_ACTIVE = "has_timer";

    public static final String SHELLY_LIGHT_TURN = "turn";
    public static final String SHELLY_LIGHT_DEFSTATE = "def_state";
    public static final String SHELLY_LIGHTTIMER = "timer";

    public static final String SHELLY_COLOR_RED = "red";
    public static final String SHELLY_COLOR_BLUE = "blue";
    public static final String SHELLY_COLOR_GREEN = "green";
    public static final String SHELLY_COLOR_YELLOW = "yellow";
    public static final String SHELLY_COLOR_WHITE = "white";
    public static final String SHELLY_COLOR_GAIN = "gain";
    public static final String SHELLY_COLOR_BRIGHTNESS = "brightness";
    public static final String SHELLY_COLOR_TEMP = "temp";
    public static final String SHELLY_COLOR_EFFECT = "effect";

    public static final int SHELLY_MIN_ROLLER_POS = 0;
    public static final int SHELLY_MAX_ROLLER_POS = 100;
    public static final int SHELLY_MIN_BRIGHTNESS = 0;
    public static final int SHELLY_MAX_BRIGHTNESS = 100;
    public static final int SHELLY_MIN_GAIN = 0;
    public static final int SHELLY_MAX_GAIN = 100;
    public static final int SHELLY_MIN_COLOR = 0;
    public static final int SHELLY_MAX_COLOR = 255;
    public static final int SHELLY_DIM_STEPSIZE = 10;

    // color temperature: 3000 = warm, 4750 = white, 6565 = cold; gain: 0..100
    public static final int MIN_COLOR_TEMPERATURE = 3000;
    public static final int MAX_COLOR_TEMPERATURE = 6500;
    public static final int COLOR_TEMPERATURE_RANGE = MAX_COLOR_TEMPERATURE - MIN_COLOR_TEMPERATURE;
    public static final double MIN_BRIGHTNESS = 0.0;
    public static final double MAX_BRIGHTNESS = 100.0;
    public static final double SATURATION_FACTOR = 2.55;
    public static final double GAIN_FACTOR = SHELLY_MAX_GAIN / 100;
    public static final double BRIGHTNESS_FACTOR = SHELLY_MAX_BRIGHTNESS / 100;

    /**
     * Shelly Dimmer returns light[]. However, the structure doesn't match the lights[] of a Bulb/RGBW2.
     * The tag lights[] will be replaced with dimmers[] so this could be mapped to a different Gson structure.
     * The function requires that it's only called when the device is a dimmer - on get settings and get status
     *
     * @param json Input Json as received by the API
     * @return Modified Json
     */
    public static String fixDimmerJson(String json) {
        //
        //
        return !json.contains("\"lights\":[") ? json
                : json.replaceFirst(java.util.regex.Pattern.quote("\"lights\":["), "\"dimmers\":[");
    }

}
