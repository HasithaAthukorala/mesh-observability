/*
 *  Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package io.cellery.observability.telemetry.receiver;

import io.cellery.observability.telemetry.receiver.generated.AttributesOuterClass;
import io.cellery.observability.telemetry.receiver.generated.MixerGrpc;
import io.cellery.observability.telemetry.receiver.generated.Report;
import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;
import org.apache.commons.io.IOUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.siddhi.core.SiddhiAppRuntime;
import org.wso2.siddhi.core.SiddhiManager;
import org.wso2.siddhi.core.event.Event;
import org.wso2.siddhi.core.stream.output.StreamCallback;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class TelemetryEventSourceTestCase {
    private SiddhiAppRuntime siddhiAppRuntime;
    private int receive = 0;
    private MixerGrpc.MixerBlockingStub mixerBlockingStub;

    @BeforeClass
    public void init() throws IOException {
        initSiddhiApp();
        initClient();
    }

    private void initSiddhiApp() throws IOException {
        String tracingAppContent = IOUtils.toString(this.getClass().
                getResourceAsStream(File.separator + "telemetry-stream.siddhi"), StandardCharsets.UTF_8.name());
        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(tracingAppContent);
        siddhiAppRuntime.addCallback("TelemetryStream", new StreamCallback() {
            @Override
            public void receive(Event[] events) {
                receive++;
            }
        });
        siddhiAppRuntime.start();
    }

    private void initClient() {
        ManagedChannel managedChannel = NettyChannelBuilder.forAddress("localhost", 9091).build();
        this.mixerBlockingStub = MixerGrpc.newBlockingStub(managedChannel);
    }

    @Test
    public void report() {
        AttributesOuterClass.CompressedAttributes stringAttr = AttributesOuterClass.CompressedAttributes.newBuilder()
                .putAllStrings().build();
        Report.ReportRequest reportRequest = Report.ReportRequest.newBuilder().addAttributes(stringAttr).build();
        Report.ReportResponse reportResponse = mixerBlockingStub.report(reportRequest);
        Assert.assertTrue(reportResponse != null);
    }
}
