/*
 * Copyright (c) ${year} WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 *  under the License.
 *
 */

package wso2spadapter

import (
	"context"
	"fmt"
	"log"
	"net"
	"net/http"
	"testing"

	"github.com/gogo/protobuf/types"

	"github.com/cellery-io/mesh-observability/components/global/mixer-adapter/config"

	"go.uber.org/zap"
	"istio.io/api/policy/v1beta1"
	"istio.io/istio/mixer/template/metric"

	"github.com/cellery-io/mesh-observability/components/global/mixer-adapter/pkg/logging"
)

const defaultAdapterPort string = "38355"

type DummyServerResponse struct {
	response bool
	err      error
}

var (
	sampleInstance1 = &metric.InstanceMsg{
		Name: "wso2spadapter-metric",
		Dimensions: map[string]*v1beta1.Value{
			"response_code": {
				Value: &v1beta1.Value_Int64Value{Int64Value: 200},
			},
		},
		Value: &v1beta1.Value{
			Value: &v1beta1.Value_Int64Value{Int64Value: 350},
		},
	}
	sampleInstance2 = &metric.InstanceMsg{
		Name: "wso2spadapter-metric",
		Dimensions: map[string]*v1beta1.Value{
			"response_code": {
				Value: &v1beta1.Value_BoolValue{BoolValue: false},
			},
		},
		Value: &v1beta1.Value{
			Value: &v1beta1.Value_Int64Value{Int64Value: 0},
		},
	}
	sampleInstance3 = &metric.InstanceMsg{
		Name: "wso2spadapter-metric",
		Dimensions: map[string]*v1beta1.Value{
			"response_code": {
				Value: &v1beta1.Value_StringValue{StringValue: "Test"},
			},
		},
		Value: &v1beta1.Value{
			Value: &v1beta1.Value_Int64Value{Int64Value: 0},
		},
	}
	sampleInstance4 = &metric.InstanceMsg{
		Name: "wso2spadapter-metric",
		Dimensions: map[string]*v1beta1.Value{
			"response_code": {
				Value: &v1beta1.Value_DoubleValue{DoubleValue: 1.5},
			},
		},
		Value: &v1beta1.Value{
			Value: &v1beta1.Value_Int64Value{Int64Value: 0},
		},
	}
)

func (response DummyServerResponse) publishMetrics(attributeMap map[string]interface{}, logger *zap.SugaredLogger, httpClient *http.Client, spServerUrl string) bool {
	if response.err != nil {
		return false
	}
	return response.response
}

func TestNewWso2SpAdapter(t *testing.T) {
	logger, err := logging.NewLogger()
	if err != nil {
		t.Errorf("Error building logger: %s", err.Error())
	}
	adapter, err := New(defaultAdapterPort, logger, &http.Client{}, DummyServerResponse{}, nil, "")
	wantStr := "[::]:38355"
	if err != nil {
		t.Errorf("Error while creating the adapter : %s", err.Error())
	}
	if adapter == nil {
		t.Error("Adapter is nil")
	} else {
		if adapter.Addr() == wantStr {
			defer func() {
				err := adapter.Close()
				if err != nil {
					log.Fatalf("Error closing adapter: %s", err.Error())
				}
			}()
			t.Log("Success, expected address is received")
		} else {
			t.Error("Fail, Expected address is not received")
		}
	}
}

func TestWso2SpAdapter_HandleMetric(t *testing.T) {
	listener, err := net.Listen("tcp", fmt.Sprintf(":%s", defaultAdapterPort))
	if err != nil {
		t.Errorf("Unable to listen on socket: %s", err.Error())
	}

	logger, err := logging.NewLogger()
	if err != nil {
		t.Errorf("Error building logger: %s", err.Error())
	}

	wso2SpAdapter := &Wso2SpAdapter{
		listener:   listener,
		logger:     logger,
		httpClient: &http.Client{},
		publisher:  DummyServerResponse{},
	}

	var sampleInstances []*metric.InstanceMsg
	sampleInstances = append(sampleInstances, sampleInstance1)
	sampleInstances = append(sampleInstances, sampleInstance2)
	sampleInstances = append(sampleInstances, sampleInstance3)
	sampleInstances = append(sampleInstances, sampleInstance4)

	anyType := &types.Any{}
	configP := &config.Params{}
	configP.FilePath = "out.txt"
	data, err := configP.Marshal()
	anyType.Value = data

	sampleMetricRequest := metric.HandleMetricRequest{
		Instances:     sampleInstances,
		AdapterConfig: anyType,
	}

	_, err = wso2SpAdapter.HandleMetric(context.TODO(), &sampleMetricRequest)

	if err != nil {
		t.Errorf("Metric could not be handled : %s", err.Error())
	} else {
		t.Log("Successfully handled the metrics")
	}

}
