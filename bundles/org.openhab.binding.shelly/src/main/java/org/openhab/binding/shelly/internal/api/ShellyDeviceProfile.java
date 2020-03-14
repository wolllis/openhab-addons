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

import static org.openhab.binding.shelly.internal.ShellyBindingConstants.SHELLYDT_DIMMER;
import static org.openhab.binding.shelly.internal.ShellyUtils.*;
import static org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.*;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.shelly.internal.ShellyBindingConstants;
import org.openhab.binding.shelly.internal.ShellyUtils;
import org.openhab.binding.shelly.internal.api.ShellyApiJsonDTO.ShellySettingsGlobal;

import com.google.gson.Gson;

/**
 * The {@link ShellyDeviceProfile} creates a device profile based on the settings returned from the API's /settings
 * call. This is used to be more dynamic in controlling the device, but also to overcome some issues in the API (e.g.
 * RGBW2 returns "no meter" even it has one)
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyDeviceProfile {
    public String thingName = "";
    public String deviceType = "";

    public String settingsJson = "";
    public @Nullable ShellySettingsGlobal settings;

    public String hostname = "";
    public String mode = "";

    public String hwRev = "";
    public String hwBatchId = "";
    public String mac = "";
    public String fwId = "";
    public String fwVersion = "";
    public String fwDate = "";

    public Boolean hasRelays = false; // true if it has at least 1 power meter
    public Integer numRelays = 0; // number of relays/outputs
    public Integer numRollers = 9; // number of Rollers, usually 1
    public Boolean isRoller = false; // true for Shelly2 in roller mode
    public Boolean isDimmer = false; // true for a Shelly Dimmer (SHDM-1)

    public Boolean hasMeter = false; // true if it has at least 1 power meter
    public Integer numMeters = 0;
    public Boolean isEMeter = false; // true for ShellyEM

    public Boolean hasBattery = false; // true if battery device
    public Boolean hasLed = false; // true if battery device
    public Boolean isPlugS = false; // true if it is a Shelly Plug S
    public Boolean isLight = false; // true if it is a Shelly Bulb/RGBW2
    public Boolean isBulb = false; // true pnly if it is a Bulb
    public Boolean isSense = false; // true if thing is a Shelly Sense
    public Boolean inColor = false; // true if bulb/rgbw2 is in color mode
    public Boolean isSensor = false; // true for HT & Smoke
    public Boolean isSmoke = false; // true for Smoke

    public Map<String, String> irCodes = new HashMap<String, String>(); // Sense: list of stored IR codes

    public Boolean supportsButtonUrls = false; // true if the btn_xxx urls are supported
    public Boolean supportsOutUrls = false; // true if the out_xxx urls are supported
    public Boolean supportsPushUrls = false; // true if sensor report_url is supported
    public Boolean supportsRollerUrls = false; // true if the roller_xxx urls are supported
    public Boolean supportsSensorUrls = false; // true if sensor report_url is supported

    @SuppressWarnings("null")
    public static ShellyDeviceProfile initialize(String thingType, String json) {
        Gson gson = new Gson();

        ShellyDeviceProfile profile = new ShellyDeviceProfile();
        Validate.notNull(profile);

        profile.settingsJson = json;
        profile.settings = gson.fromJson(json, ShellySettingsGlobal.class);
        Validate.notNull(profile.settings, "converted device settings must not be null!");

        // General settings
        profile.deviceType = ShellyUtils.getString(profile.settings.device.type);
        profile.mac = getString(profile.settings.device.mac);
        profile.hostname = profile.settings.device.hostname != null && !profile.settings.device.hostname.isEmpty()
                ? profile.settings.device.hostname.toLowerCase()
                : "shelly-" + profile.mac.toUpperCase().substring(6, 11);
        profile.mode = getString(profile.settings.mode) != null ? getString(profile.settings.mode).toLowerCase() : "";
        profile.hwRev = profile.settings.hwinfo != null ? getString(profile.settings.hwinfo.hwRevision) : "";
        profile.hwBatchId = profile.settings.hwinfo != null ? getString(profile.settings.hwinfo.batchId.toString())
                : "";
        profile.fwDate = getString(StringUtils.substringBefore(profile.settings.fw, "/"));
        profile.fwVersion = getString(StringUtils.substringBetween(profile.settings.fw, "/", "@"));
        profile.fwId = getString(StringUtils.substringAfter(profile.settings.fw, "@"));

        profile.isRoller = profile.mode.equalsIgnoreCase(SHELLY_MODE_ROLLER);
        profile.isPlugS = thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYPLUGS.getId());
        profile.hasLed = profile.isPlugS;
        profile.isBulb = thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYBULB.getId());
        profile.isDimmer = profile.deviceType.equalsIgnoreCase(SHELLYDT_DIMMER);
        profile.isLight = profile.isBulb
                || thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYRGBW2_COLOR.getId())
                || thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYRGBW2_WHITE.getId());
        profile.inColor = profile.isLight && profile.mode.equalsIgnoreCase(SHELLY_MODE_COLOR);

        profile.isSmoke = thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYSMOKE.getId());
        profile.isSense = thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYSENSE.getId());
        profile.isSensor = profile.isSense || profile.isSmoke
                || thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYHT.getId())
                || thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYFLOOD.getId())
                || thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYSENSE.getId());
        profile.hasBattery = thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYHT.getId())
                || thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYSMOKE.getId())
                || thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYFLOOD.getId())
                || thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYSENSE.getId());

        profile.numRelays = !profile.isLight ? getInteger(profile.settings.device.numOutputs) : 0;
        if ((profile.numRelays > 0) && (profile.settings.relays == null)) {
            profile.numRelays = 0;
        }
        profile.hasRelays = (profile.numRelays > 0) || profile.isDimmer;
        profile.numRollers = getInteger(profile.settings.device.numRollers);

        profile.isEMeter = profile.settings.emeters != null;
        profile.numMeters = !profile.isEMeter ? getInteger(profile.settings.device.numMeters)
                : getInteger(profile.settings.device.numEMeters);
        if ((profile.numMeters == 0) && profile.isLight) {
            // RGBW2 doesn't report, but has one
            profile.numMeters = profile.inColor ? 1 : getInteger(profile.settings.device.numOutputs);
        }
        profile.hasMeter = (profile.numMeters > 0);

        profile.supportsButtonUrls = profile.settingsJson.contains(SHELLY_API_EVENTURL_BTN_ON)
                || profile.settingsJson.contains(SHELLY_API_EVENTURL_BTN1_ON)
                || profile.settingsJson.contains(SHELLY_API_EVENTURL_BTN2_ON);
        profile.supportsOutUrls = profile.settingsJson.contains(SHELLY_API_EVENTURL_OUT_ON);
        profile.supportsPushUrls = profile.settingsJson.contains(SHELLY_API_EVENTURL_SHORT_PUSH);
        profile.supportsRollerUrls = profile.settingsJson.contains(SHELLY_API_EVENTURL_ROLLER_OPEN);
        profile.supportsSensorUrls = profile.settingsJson.contains(SHELLY_API_EVENTURL_REPORT);

        return profile;
    }
}
