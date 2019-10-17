/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package file

import (
	"fmt"
	"io/ioutil"
	"os"
	"path/filepath"
	"testing"

	"github.com/cellery-io/mesh-observability/components/global/mixer-adapter/pkg/logging"
)

var (
	testStr = "{\"contextReporterKind\":\"inbound\", \"destinationUID\":\"kubernetes://istio-policy-74d6c8b4d5-mmr49.istio-system\", \"requestID\":\"6e544e82-2a0c-4b83-abcc-0f62b89cdf3f\", \"requestMethod\":\"POST\", \"requestPath\":\"/istio.mixer.v1.Mixer/Check\", \"requestTotalSize\":\"2748\", \"responseCode\":\"200\", \"responseDurationNanoSec\":\"695653\", \"responseTotalSize\":\"199\", \"sourceUID\":\"kubernetes://pet-be--controller-deployment-6f6f5768dc-n9jf7.default\", \"spanID\":\"ae295f3a4bbbe537\", \"traceID\":\"b55a0f7f20d36e49f8612bac4311791d\"}"
)

func TestPersister_Write(t *testing.T) {
	logger, err := logging.NewLogger()
	if err != nil {
		t.Errorf("Error building logger: %s", err.Error())
	}

	buffer := make(chan string, 20)

	persister := &Persister{
		WaitingSize: 5,
		Logger:      logger,
		Buffer:      buffer,
		Directory:   ".",
	}

	buffer <- testStr
	buffer <- testStr

	persister.Write()

	persister.Directory = "./wrong_directory"
	persister.Write()

	files, err := filepath.Glob("./*.txt")
	for _, fname := range files {
		err = os.Remove(fname)
	}

}

func TestPersister_Fetch(t *testing.T) {
	logger, err := logging.NewLogger()
	if err != nil {
		t.Errorf("Error building logger: %s", err.Error())
	}

	_ = ioutil.WriteFile("./test.txt", []byte(testStr), 0644)

	buffer := make(chan string, 5)

	persister := &Persister{
		WaitingSize: 4,
		Logger:      logger,
		Buffer:      buffer,
		Directory:   "./",
	}

	run := make(chan bool, 1)
	jsonArr, _ := persister.Fetch(run)

	if jsonArr == testStr {
		t.Log("data matches")
	}

	_ = ioutil.WriteFile("./test.txt", []byte(""), 0644)
	_, _ = persister.Fetch(run)

	persister.Directory = "./wrong_dir"
	_, _ = persister.Fetch(run)

	files, err := filepath.Glob("./*.txt")
	for _, fname := range files {
		err = os.Remove(fname)
	}

}

func TestPersister_Clean(t *testing.T) {
	logger, err := logging.NewLogger()
	if err != nil {
		t.Errorf("Error building logger: %s", err.Error())
	}

	buffer := make(chan string, 5)

	persister := &Persister{
		WaitingSize: 4,
		Logger:      logger,
		Buffer:      buffer,
		Directory:   "./",
	}

	_ = ioutil.WriteFile("./test.txt", []byte(testStr), 0644)
	fname = "./test.txt"
	persister.Clean(nil)

	_ = ioutil.WriteFile("./test.txt", []byte(testStr), 0644)
	persister.Clean(fmt.Errorf("test error 1"))

	fname = "./wrong.f.txt"
	persister.Clean(nil)

	files, err := filepath.Glob("./*.txt")
	for _, fname := range files {
		err = os.Remove(fname)
	}
}
