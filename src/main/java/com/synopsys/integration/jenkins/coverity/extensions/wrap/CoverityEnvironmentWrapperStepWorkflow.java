/**
 * synopsys-coverity
 *
 * Copyright (c) 2020 Synopsys, Inc.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.synopsys.integration.jenkins.coverity.extensions.wrap;

import java.io.IOException;
import java.util.List;

import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.function.ThrowingSupplier;
import com.synopsys.integration.jenkins.JenkinsVersionHelper;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsAbortException;
import com.synopsys.integration.jenkins.coverity.extensions.ConfigureChangeSetPatterns;
import com.synopsys.integration.jenkins.coverity.stepworkflow.CoverityJenkinsStepWorkflow;
import com.synopsys.integration.jenkins.coverity.stepworkflow.CoverityWorkflowStepFactory;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.stepworkflow.StepWorkflow;
import com.synopsys.integration.stepworkflow.StepWorkflowResponse;

import hudson.AbortException;
import hudson.scm.ChangeLogSet;
import jenkins.tasks.SimpleBuildWrapper;

public class CoverityEnvironmentWrapperStepWorkflow extends CoverityJenkinsStepWorkflow<Object> {
    public static final String FAILURE_MESSAGE = "Unable to inject Coverity Environment: ";

    private final CoverityWorkflowStepFactory coverityWorkflowStepFactory;
    private final SimpleBuildWrapper.Context context;
    private final String workspaceRemotePath;
    private final String coverityInstanceUrl;
    private final String projectName;
    private final String streamName;
    private final String viewName;
    private final Boolean createMissingProjectsAndStreams;
    private final List<ChangeLogSet<?>> changeSets;
    private final ConfigureChangeSetPatterns configureChangeSetPatterns;

    public CoverityEnvironmentWrapperStepWorkflow(JenkinsIntLogger jenkinsIntLogger, JenkinsVersionHelper jenkinsVersionHelper, ThrowingSupplier<WebServiceFactory, CoverityJenkinsAbortException> webServiceFactorySupplier,
        CoverityWorkflowStepFactory coverityWorkflowStepFactory, SimpleBuildWrapper.Context context, String workspaceRemotePath, String coverityInstanceUrl, String projectName, String streamName, String viewName,
        Boolean createMissingProjectsAndStreams, List<ChangeLogSet<?>> changeSets, ConfigureChangeSetPatterns configureChangeSetPatterns) {
        super(jenkinsIntLogger, jenkinsVersionHelper, webServiceFactorySupplier);
        this.coverityWorkflowStepFactory = coverityWorkflowStepFactory;
        this.context = context;
        this.workspaceRemotePath = workspaceRemotePath;
        this.coverityInstanceUrl = coverityInstanceUrl;
        this.projectName = projectName;
        this.streamName = streamName;
        this.viewName = viewName;
        this.createMissingProjectsAndStreams = createMissingProjectsAndStreams;
        this.changeSets = changeSets;
        this.configureChangeSetPatterns = configureChangeSetPatterns;
    }

    protected StepWorkflow<Object> buildWorkflow() throws AbortException {
        return StepWorkflow
                   //.first(coverityWorkflowStepFactory.createStepValidateCoverityInstallation(false))
                   .first(coverityWorkflowStepFactory.createStepCreateAuthenticationKeyFile(workspaceRemotePath, coverityInstanceUrl))
                   .then(coverityWorkflowStepFactory.createStepSetUpCoverityEnvironment(changeSets, configureChangeSetPatterns, workspaceRemotePath, coverityInstanceUrl, projectName, streamName, viewName))
                   .then(coverityWorkflowStepFactory.createStepPopulateEnvVars(context::env))
                   .andSometimes(coverityWorkflowStepFactory.createStepCreateMissingProjectsAndStreams(coverityInstanceUrl, projectName, streamName)).butOnlyIf(createMissingProjectsAndStreams, Boolean.TRUE::equals)
                   .build();
    }

    public Boolean perform() throws IOException {
        StepWorkflowResponse<Object> response = runWorkflow();
        try {
            if (!response.wasSuccessful()) {
                throw response.getException();
            }
        } catch (IntegrationException e) {
            logger.debug(null, e);
            throw new AbortException(FAILURE_MESSAGE + e.getMessage());
        } catch (Exception e) {
            throw new IOException(FAILURE_MESSAGE + e.getMessage(), e);
        }

        return response.wasSuccessful();
    }

    @Override
    protected void cleanUp() throws AbortException {
        // The CoverityEnvironmentWrapper needs to clean up later than other workflows, so we create a Disposer and attach it to the context instead.
    }

}
