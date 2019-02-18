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
package io.cellery.observability.model.generator;

import io.cellery.observability.model.generator.internal.ServiceHolder;
import io.cellery.observability.model.generator.model.SpanInfo;
import org.apache.log4j.Logger;
import org.wso2.siddhi.annotation.Example;
import org.wso2.siddhi.annotation.Extension;
import org.wso2.siddhi.core.config.SiddhiAppContext;
import org.wso2.siddhi.core.event.ComplexEvent;
import org.wso2.siddhi.core.event.ComplexEventChunk;
import org.wso2.siddhi.core.event.stream.StreamEvent;
import org.wso2.siddhi.core.event.stream.StreamEventCloner;
import org.wso2.siddhi.core.event.stream.populater.ComplexEventPopulater;
import org.wso2.siddhi.core.exception.SiddhiAppCreationException;
import org.wso2.siddhi.core.executor.ExpressionExecutor;
import org.wso2.siddhi.core.query.processor.Processor;
import org.wso2.siddhi.core.query.processor.stream.StreamProcessor;
import org.wso2.siddhi.core.util.config.ConfigReader;
import org.wso2.siddhi.query.api.definition.AbstractDefinition;
import org.wso2.siddhi.query.api.definition.Attribute;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This is the Siddhi extension which generates the dependency graph for the spans created.
 */
@Extension(
        name = "modelGenerator",
        namespace = "observe",
        description = "This generates the dependency model of the spans",
        examples = @Example(description = "TBD"
                , syntax = "from inputStream#tracing:dependencyTree(componentName, spanId, parentId, serviceName," +
                " tags) \" +\n" +
                "                \"select * \n" +
                "                \"insert into outputStream;")
)
public class ModelGenerationExtension extends StreamProcessor {

    private static final Logger log = Logger.getLogger(ModelGenerationExtension.class);

    private ExpressionExecutor cellNameExecutor;
    private ExpressionExecutor serviceNameExecutor;
    private ExpressionExecutor operationNameExecutor;
    private ExpressionExecutor traceIdExecutor;
    private ExpressionExecutor spanIdExecutor;
    private ExpressionExecutor parentIdExecutor;
    private ExpressionExecutor spanKindExecutor;
    private Map<String, Node> cellCache = new ConcurrentHashMap<>();

    @Override
    protected void process(ComplexEventChunk<StreamEvent> complexEventChunk, Processor processor,
                           StreamEventCloner streamEventCloner, ComplexEventPopulater complexEventPopulater) {
        try {
            if (complexEventChunk.getFirst() != null && complexEventChunk.getFirst().getType().
                    equals(ComplexEvent.Type.EXPIRED)) {
                Map<String, List<SpanInfo>> spanCache = new HashMap<>();
                String traceId = null;
                SpanInfo rootSpan = null;
                while (complexEventChunk.hasNext()) {
                    StreamEvent streamEvent = complexEventChunk.next();
                    String cellName = (String) cellNameExecutor.execute(streamEvent);
                    String serviceName = (String) serviceNameExecutor.execute(streamEvent);
                    String operationName = (String) operationNameExecutor.execute(streamEvent);
                    String spanId = (String) spanIdExecutor.execute(streamEvent);
                    String parentId = (String) parentIdExecutor.execute(streamEvent);
                    if (traceId == null) {
                        traceId = (String) traceIdExecutor.execute(streamEvent);
                    }
                    SpanInfo spanInfo = new SpanInfo(cellName, serviceName, operationName, spanId, parentId);
                    List<SpanInfo> childNodes = spanCache.computeIfAbsent(parentId, k -> new ArrayList<>());
                    childNodes.add(spanInfo);
                    spanCache.putIfAbsent(parentId, childNodes);
                    if (spanId.equalsIgnoreCase(traceId)) {
                        rootSpan = spanInfo;
                    }
                }
                if (rootSpan != null) {
                    List<SpanInfo> rootCellSpans = findRootCellSpan(rootSpan, spanCache);
                    traceWalk(rootCellSpans, spanCache);
                }
            } else {
                processor.process(complexEventChunk);
            }
        } catch (Throwable throwable) {
            log.error("Unexpected error occured while processing the event in the model processor.", throwable);
        }
    }

    private List<SpanInfo> findRootCellSpan(SpanInfo rootSpan, Map<String, List<SpanInfo>> spanInfoMap) {
        List<SpanInfo> parents = new ArrayList<>();
        if (rootSpan.getCellName() == null || rootSpan.getCellName().isEmpty()) {
            List<SpanInfo> children = spanInfoMap.get(rootSpan.getSpanId());
            if (children != null) {
                for (SpanInfo child : children) {
                    if (child.getCellName() != null && !child.getCellName().isEmpty()) {
                        parents.add(child);
                    } else {
                        parents.addAll(findRootCellSpan(child, spanInfoMap));
                    }
                }
            }
        }
        return parents;
    }

    private void traceWalk(List<SpanInfo> rootSpans, Map<String, List<SpanInfo>> spanInfoMap) {
        for (SpanInfo rootSpan : rootSpans) {
            Node parentNode = ServiceHolder.getModelManager().getOrGenerateNode(rootSpan.getCellName());
            parentNode.addComponent(rootSpan.getComponentName());
            ServiceHolder.getModelManager().addNode(parentNode);
            if (spanInfoMap.get(rootSpan.getSpanId()) != null) {
                List<SpanInfo> linkedChildren = goDepth(parentNode, rootSpan, spanInfoMap);
                if (!linkedChildren.isEmpty()) {
                    traceWalk(linkedChildren, spanInfoMap);
                }
            }
        }
    }

    private List<SpanInfo> goDepth(Node cellParentNode, SpanInfo parentSpanInfo, Map<String,
            List<SpanInfo>> spanInfoMap) {
        List<SpanInfo> childSpanInfoList = spanInfoMap.get(parentSpanInfo.getSpanId());
        List<SpanInfo> linkedChildren = new ArrayList<>();
        if (childSpanInfoList != null) {
            for (SpanInfo childSpanInfo : childSpanInfoList) {
                if (childSpanInfo.getCellName() != null && !childSpanInfo.getCellName().isEmpty() &&
                        !childSpanInfo.getOperationName().equalsIgnoreCase(Constants.IGNORE_OPERATION_NAME)) {
                    Node childNode = ServiceHolder.getModelManager().getOrGenerateNode(childSpanInfo.getCellName());
                    childNode.addComponent(childSpanInfo.getComponentName());
                    ServiceHolder.getModelManager().addNode(childNode);
                    ServiceHolder.getModelManager().addLink(cellParentNode, childNode, Utils.generateServiceName(
                            parentSpanInfo.getComponentName(), childSpanInfo.getComponentName()));
                    linkedChildren.add(childSpanInfo);
                } else {
                    goDepth(cellParentNode, childSpanInfo, spanInfoMap);
                }
            }
        }
        return linkedChildren;
    }


    @Override
    protected List<Attribute> init(AbstractDefinition abstractDefinition, ExpressionExecutor[] expressionExecutors,
                                   ConfigReader configReader, SiddhiAppContext siddhiAppContext) {
        if (expressionExecutors.length != 7) {
            throw new SiddhiAppCreationException("Minimum number of attributes is six");
        } else {
            if (expressionExecutors[0].getReturnType() == Attribute.Type.STRING) {
                cellNameExecutor = expressionExecutors[0];
            } else {
                throw new SiddhiAppCreationException("Expected a field with String return type for the component name "
                        + "field, but found a field with return type - " + expressionExecutors[0].getReturnType());
            }

            if (expressionExecutors[1].getReturnType() == Attribute.Type.STRING) {
                serviceNameExecutor = expressionExecutors[1];
            } else {
                throw new SiddhiAppCreationException("Expected a field with Long return type for the span id field," +
                        "but found a field with return type - " + expressionExecutors[1].getReturnType());
            }

            if (expressionExecutors[2].getReturnType() == Attribute.Type.STRING) {
                operationNameExecutor = expressionExecutors[2];
            } else {
                throw new SiddhiAppCreationException("Expected a field with Long return type for the parent id field,"
                        + "but found a field with return type - " + expressionExecutors[2].getReturnType());
            }

            if (expressionExecutors[3].getReturnType() == Attribute.Type.STRING) {
                spanIdExecutor = expressionExecutors[3];
            } else {
                throw new SiddhiAppCreationException("Expected a field with String return type for the service name" +
                        " field, but found a field with return type - " + expressionExecutors[3].getReturnType());
            }

            if (expressionExecutors[4].getReturnType() == Attribute.Type.STRING) {
                parentIdExecutor = expressionExecutors[4];
            } else {
                throw new SiddhiAppCreationException("Expected a field with String return type for the tags field," +
                        "but found a field with return type - " + expressionExecutors[4].getReturnType());
            }

            if (expressionExecutors[5].getReturnType() == Attribute.Type.STRING) {
                spanKindExecutor = expressionExecutors[5];
            } else {
                throw new SiddhiAppCreationException("Expected a field with String return type for the " +
                        "spanKind field, but found a field with return type - "
                        + expressionExecutors[5].getReturnType());
            }

            if (expressionExecutors[6].getReturnType() == Attribute.Type.STRING) {
                traceIdExecutor = expressionExecutors[6];
            } else {
                throw new SiddhiAppCreationException("Expected a field with String return type for the " +
                        "traceId field, but found a field with return type - "
                        + expressionExecutors[5].getReturnType());
            }
        }
        return new ArrayList<>();
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public Map<String, Object> currentState() {
        return null;
    }

    @Override
    public void restoreState(Map<String, Object> map) {

    }
}
