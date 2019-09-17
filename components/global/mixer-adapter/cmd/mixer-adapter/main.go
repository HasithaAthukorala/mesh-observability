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

package main

import (
	"crypto/tls"
	"crypto/x509"
	"fmt"
	"io/ioutil"
	"log"
	"net/http"
	"os"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials"

	"github.com/cellery-io/mesh-observability/components/global/mixer-adapter/pkg/logging"
	"github.com/cellery-io/mesh-observability/components/global/mixer-adapter/pkg/wso2spadapter"
)

const (
	defaultAdapterPort         string = "38355"
	grpcAdapterCredentialPath  string = "GRPC_ADAPTER_CREDENTIAL"
	grpcAdapterPrivateKeyPath  string = "GRPC_ADAPTER_PRIVATE_KEY"
	grpcAdapterCertificatePath string = "GRPC_ADAPTER_CERTIFICATE"
	spServerUrl                string = "SP_SERVER_URL"
)

func main() {

	port := defaultAdapterPort //Pre defined port for the adaptor. ToDo: Should get this as an environment variable

	if len(os.Args) > 1 {
		port = os.Args[1]
	}

	logger, err := logging.NewLogger()
	if err != nil {
		log.Fatalf("Error building logger: %s", err.Error())
	}
	defer func() {
		err := logger.Sync()
		if err != nil {
			log.Fatalf("Error syncing logger: %s", err.Error())
		}
	}()

	/* Mutual TLS feature to secure connection between workloads
	   This is optional. */
	credential := os.Getenv(grpcAdapterCredentialPath)   // adapter.crt //change the name
	privateKey := os.Getenv(grpcAdapterPrivateKeyPath)   // adapter.key
	certificate := os.Getenv(grpcAdapterCertificatePath) // ca.pem
	spServerUrl := os.Getenv(spServerUrl)

	logger.Infof("Sp server url : %s", spServerUrl)

	client := &http.Client{}
	spServerResponse := wso2spadapter.ServerResponse{}

	var serverOption grpc.ServerOption = nil

	if credential != "" {
		serverOption, err = getServerTLSOption(credential, privateKey, certificate)
		if err != nil {
			logger.Warn("Server option could not be fetched, Connection will not be encrypted")
		}
	}

	adapter, err := wso2spadapter.New(port, logger, client, spServerResponse, serverOption, spServerUrl)
	if err != nil {
		logger.Error("unable to start server: ", err.Error())
		os.Exit(-1)
	}

	shutdown := make(chan error, 1)
	go func() {
		adapter.Run(shutdown)
	}()
	err = <-shutdown
	if err != nil {
		logger.Error(err.Error())
	}
}

func getServerTLSOption(credential, privateKey, caCertificate string) (grpc.ServerOption, error) {
	certificate, err := tls.LoadX509KeyPair(
		credential,
		privateKey,
	)
	if err != nil {
		return nil, fmt.Errorf("failed to load key cert pair")
	}
	certPool := x509.NewCertPool()
	bytesArray, err := ioutil.ReadFile(caCertificate)
	if err != nil {
		return nil, fmt.Errorf("failed to read client ca cert: %s", err)
	}

	ok := certPool.AppendCertsFromPEM(bytesArray)
	if !ok {
		return nil, fmt.Errorf("failed to append client certs")
	}

	tlsConfig := &tls.Config{
		Certificates: []tls.Certificate{certificate},
		ClientCAs:    certPool,
	}
	tlsConfig.ClientAuth = tls.RequireAndVerifyClientCert

	return grpc.Creds(credentials.NewTLS(tlsConfig)), nil
}
