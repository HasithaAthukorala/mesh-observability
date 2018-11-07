/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import Constants from "./constants";

/**
 * Utilities used for processing Constants related data.
 */
class TracingUtils {


    /**
     * Build a tree model using a spans list.
     *
     * @param {Array.<Span>} spansList The spans list from which the tree should be built
     * @returns {Span} The root span of the tree
     */
    static buildTree(spansList) {
        // Finding the root spans candidates (There can be one root span or two sibling root spans)
        const spanIdList = spansList.map((span) => span.spanId);
        const rootSpanCandidates = spansList.filter((span) => span.parentSpanId === span.traceId
            || !spanIdList.includes(span.parentSpanId));

        // Finding the root span and initializing current span
        let rootSpan;
        if (rootSpanCandidates.length === 1) { // Single root
            rootSpan = rootSpanCandidates[0];
        } else if (rootSpanCandidates.length === 2) { // Two routes with one client span and one server span
            let rootSpanIndex;
            if (rootSpanCandidates[0].isSiblingOf(rootSpanCandidates[1])) {
                if (rootSpanCandidates[0].isParentOf(rootSpanCandidates[1])) {
                    rootSpanIndex = 0;
                } else {
                    rootSpanIndex = 1;
                }
            } else {
                throw Error("Invalid Trace: Expected 1 root span, found two non-client kind root spans candidates.");
            }
            rootSpan = rootSpanCandidates[rootSpanIndex];
            rootSpanCandidates[0].sibling = rootSpanCandidates[1];
            rootSpanCandidates[1].sibling = rootSpanCandidates[0];
        } else {
            throw Error(`Invalid Trace: Expected 1 root span, found ${rootSpanCandidates.length} spans`);
        }

        // Adding references to the connected nodes
        for (let i = 0; i < spansList.length; i++) {
            for (let j = 0; j < spansList.length; j++) {
                const spanA = spansList[i];
                const spanB = spansList[j];
                if (i !== j && (spanA.isSiblingOf(spanB) || spanA.isParentOf(spanB)
                        || spanB.isParentOf(spanA))) {
                    spanA.addSpanReference(spanB);
                }
            }
        }

        return rootSpan;
    }

    /**
     * Traverse the span tree and label the nodes.
     *
     * @param {Span} tree The root span of the tree
     */
    static labelSpanTree(tree) {
        tree.walk((span, data) => {
            span.isSystemComponent = TracingUtils.isFromSystemComponent(span);
            if (TracingUtils.isFromCellGateway(span)) {
                span.cell = this.getCell(span);
            } else if (!span.isSystemComponent) {
                span.cell = data;
            }
            return span.cell;
        }, null);
    }

    /**
     * Traverse the span tree and generate a list of ordered spans.
     *
     * @param {Span} tree The root span of the tree
     * @returns {Array.<Span>} The list of spans ordered by time and tree structure
     */
    static getOrderedList(tree) {
        const spanList = [];

        tree.walk((span, data) => {
            data.push(span);
            return data;
        }, spanList);

        return spanList;
    }

    /**
     * Get the cell name from cell gateway span.
     *
     * @param {Span} cellGatewaySpan A span belonging to the cell gateway
     * @returns {Object} Cell details
     */
    static getCell(cellGatewaySpan) {
        let cell = null;
        if (cellGatewaySpan) {
            const matches = cellGatewaySpan.serviceName.match(Constants.VICK.Cell.GATEWAY_NAME_PATTERN);
            if (Boolean(matches) && matches.length === 3) {
                cell = {
                    name: matches[1],
                    version: matches[2].replace(/_/g, ".")
                };
            } else {
                throw Error(`Invalid cell gateway name: ${cellGatewaySpan.serviceName}`);
            }
        }
        return cell;
    }

    /**
     * Check whether a span belongs to the cell gateway.
     *
     * @param {Span} span The span which should be checked
     * @returns {boolean} True if the component to which the span belongs to is a cell gateway
     */
    static isFromCellGateway(span) {
        return Boolean(span) && Constants.VICK.Cell.GATEWAY_NAME_PATTERN.test(span.serviceName);
    }

    /**
     * Check whether a span belongs to the System.
     *
     * @param {Span} span The span which should be checked
     * @returns {boolean} True if the component to which the span belongs to is a system component
     */
    static isFromSystemComponent(span) {
        return Boolean(span) && (this.isFromCellGateway(span)
            || span.serviceName === Constants.VICK.System.ISTIO_MIXER_NAME
            || span.serviceName === Constants.VICK.System.GLOBAL_GATEWAY_NAME);
    }

}

export default TracingUtils;
