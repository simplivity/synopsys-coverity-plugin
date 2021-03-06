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
package com.synopsys.integration.jenkins.coverity;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Optional;

import org.jenkinsci.plugins.plaincredentials.FileCredentials;

import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.synopsys.integration.coverity.authentication.AuthenticationKeyFile;
import com.synopsys.integration.coverity.authentication.AuthenticationKeyFileUtility;
import com.synopsys.integration.jenkins.SynopsysCredentialsHelper;
import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.log.SilentIntLogger;
import com.synopsys.integration.rest.credentials.CredentialsBuilder;

import hudson.security.ACL;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;

public class SynopsysCoverityCredentialsHelper extends SynopsysCredentialsHelper {
    public static final Class<FileCredentials> AUTH_KEY_FILE_CREDENTIALS_CLASS = FileCredentials.class;
    public static final CredentialsMatcher AUTH_KEY_FILE_CREDENTIALS = CredentialsMatchers.instanceOf(AUTH_KEY_FILE_CREDENTIALS_CLASS);
    public static final CredentialsMatcher SUPPORTED_CREDENTIALS_TYPES = CredentialsMatchers.anyOf(AUTH_KEY_FILE_CREDENTIALS, CredentialsMatchers.instanceOf(USERNAME_PASSWORD_CREDENTIALS_CLASS));
    private final IntLogger logger;

    public SynopsysCoverityCredentialsHelper(IntLogger logger, Jenkins jenkins) {
        super(jenkins);
        this.logger = logger;
    }

    public static SynopsysCoverityCredentialsHelper silentHelper(Jenkins jenkins) {
        return new SynopsysCoverityCredentialsHelper(new SilentIntLogger(), jenkins);
    }

    public ListBoxModel listSupportedCredentials() {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        return new StandardListBoxModel()
                   .includeEmptyValue()
                   .includeMatchingAs(ACL.SYSTEM, Jenkins.getInstance(), StandardCredentials.class, Collections.emptyList(), SUPPORTED_CREDENTIALS_TYPES);
    }

    public Optional<FileCredentials> getAuthenticationKeyFileCredentialsById(String credentialsId) {
        return getCredentialsById(AUTH_KEY_FILE_CREDENTIALS_CLASS, credentialsId);
    }

    public Optional<String> getCoverityUsernameById(String credentialsId) {
        return getIntegrationCredentialsById(credentialsId)
                   .getUsername();
    }

    public Optional<String> getCoverityPassphraseById(String credentialsId) {
        return getUsernamePasswordCredentialsById(credentialsId)
                   .map(UsernamePasswordCredentialsImpl::getPassword)
                   .map(Secret::getPlainText);
    }

    @Override
    public com.synopsys.integration.rest.credentials.Credentials getIntegrationCredentialsById(String credentialsId) {
        Optional<UsernamePasswordCredentialsImpl> possibleUsernamePasswordCredentials = getUsernamePasswordCredentialsById(credentialsId);
        Optional<FileCredentials> possibleAuthKeyCredentials = getAuthenticationKeyFileCredentialsById(credentialsId);
        CredentialsBuilder credentialsBuilder = com.synopsys.integration.rest.credentials.Credentials.newBuilder();

        if (possibleUsernamePasswordCredentials.isPresent()) {
            UsernamePasswordCredentialsImpl usernamePasswordCredentials = possibleUsernamePasswordCredentials.get();

            credentialsBuilder.setUsernameAndPassword(usernamePasswordCredentials.getUsername(), usernamePasswordCredentials.getPassword().getPlainText());
        }

        if (possibleAuthKeyCredentials.isPresent()) {
            FileCredentials fileCredentials = possibleAuthKeyCredentials.get();
            AuthenticationKeyFileUtility authenticationKeyFileUtility = AuthenticationKeyFileUtility.defaultUtility();

            try (InputStream keyFileInputStream = fileCredentials.getContent()) {
                AuthenticationKeyFile authenticationKeyFile = authenticationKeyFileUtility.readAuthenticationKeyFile(keyFileInputStream);

                credentialsBuilder.setUsernameAndPassword(authenticationKeyFile.username, authenticationKeyFile.key);
            } catch (IOException e) {
                logger.trace("Could not parse authentication key file with credentials id " + credentialsId + " because: ", e);
            }

        }

        return credentialsBuilder.build();
    }

}
