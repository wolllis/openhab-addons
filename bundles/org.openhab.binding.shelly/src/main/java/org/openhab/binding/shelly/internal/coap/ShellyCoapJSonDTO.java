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

import java.io.IOException;
import java.util.ArrayList;

import org.apache.commons.lang.Validate;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * The {@link ShellyCoapJSonDTO} helps the CoIoT Json into Java objects
 *
 * @author Markus Michels - Initial contribution
 */
public class ShellyCoapJSonDTO {

    public static final String COIOT_TAG_BLK = "blk";
    public static final String COIOT_TAG_SEN = "sen";
    public static final String COIOT_TAG_ACT = "act";
    public static final String COIOT_TAG_GENERIC = "G";

    public static class CoIotDescrBlk {
        @SerializedName("I")
        String id; // ID
        @SerializedName("D")
        String desc; // Description

        // Sometimes sen entries are part of the blk array - not conforming the Spec!
        @SerializedName("T")
        public String type; // Type
        @SerializedName("R")
        public String range; // Range
        @SerializedName("L")
        public String links; // Links
    }

    public static class CoIotDescrSen {
        @SerializedName("I")
        String id; // ID
        @SerializedName("D")
        String desc; // Description
        @SerializedName("T")
        public String type; // Type
        @SerializedName("R")
        public String range; // Range
        @SerializedName("L")
        public String links; // Links
    }

    public static class CoIotDescrP {
        @SerializedName("I")
        String id; // ID
        @SerializedName("D")
        String desc; // Description
        @SerializedName("R")
        public String range; // Range
    }

    public static class CoIotDescrAct {
        @SerializedName("I")
        String id; // ID
        @SerializedName("D")
        String desc; // Description
        @SerializedName("L")
        public String links; // Links
        @SerializedName("P")
        public ArrayList<CoIotDescrP> pTag; // ?
    }

    public static class CoIotDevDescription {
        public ArrayList<CoIotDescrBlk> blk;
        public ArrayList<CoIotDescrSen> sen;
        public ArrayList<CoIotDescrAct> act;
    }

    public static class CoIotSensor {
        public String index; // id
        public double value; // value
    }

    public static class CoIotGenericSensorList {
        @SerializedName("G")
        public ArrayList<CoIotSensor> generic;

        public CoIotGenericSensorList() {
            generic = new ArrayList<CoIotSensor>();
        }
    }

    protected static class CoIotSensorTypeAdapter extends TypeAdapter<CoIotGenericSensorList> {
        @Override
        public CoIotGenericSensorList read(final JsonReader in) throws IOException {
            CoIotGenericSensorList list = new CoIotGenericSensorList();

            in.beginObject();
            String generic = in.nextName();
            Validate.notNull(generic, "Invalid JSon format for CoIotSensorList");
            if (generic.equals(COIOT_TAG_GENERIC)) {
                in.beginArray();
                while (in.hasNext()) {
                    in.beginArray();
                    final CoIotSensor sensor = new CoIotSensor();
                    in.nextInt(); // alway 0
                    sensor.index = new Integer(in.nextInt()).toString();
                    sensor.value = in.nextDouble();
                    in.endArray();
                    list.generic.add(sensor);
                }
                in.endArray();
            }
            in.endObject();

            return list;
        }

        @Override
        public void write(final JsonWriter out, final CoIotGenericSensorList o) throws IOException {
            CoIotGenericSensorList sensors = o;
            out.beginObject();
            if (sensors != null) {
                out.name(COIOT_TAG_GENERIC).beginArray();
                for (int i = 0; i < sensors.generic.size(); i++) {
                    out.beginArray();
                    out.value(0);
                    out.value(sensors.generic.get(i).index);
                    out.value(sensors.generic.get(i).value);
                    out.endArray();
                }
                out.endArray();
            }
            out.endObject();
        }

    }

}
