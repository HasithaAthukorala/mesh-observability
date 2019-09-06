/*
 *  Copyright (c) ${year} WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package io.cellery.observability.telemetry.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * This is a Utils class.
 */
public class Utils {
    private static final Gson gson = new Gson();

    private Utils() {
    }

    public static String toString(Map<String, String> attributes) {
        return gson.toJson(attributes);
    }

    public static String toString(ByteString byteString) {
        return gson.toJson(byteString);
    }

    public static String toString(String string) {
        return gson.toJson(string);
    }

    public static HashMap<String, Object> toMap(String json) throws IOException {
        HashMap<String, Object> result;
        try {
            result = new ObjectMapper().readValue(json, HashMap.class);
            return result;
        } catch (IOException e) {
            throw new IOException("Unexpected IO error while reading reading the json");
        }
    }

}
