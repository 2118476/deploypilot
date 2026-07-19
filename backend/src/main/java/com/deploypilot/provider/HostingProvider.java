package com.deploypilot.provider;

import com.deploypilot.model.enums.ProviderType;
import com.deploypilot.provider.model.CreateSiteRequest;
import com.deploypilot.provider.model.DeployRequest;
import com.deploypilot.provider.model.DeploymentStatus;
import com.deploypilot.provider.model.EnvVarInput;
import com.deploypilot.provider.model.HostingSite;
import com.deploypilot.provider.model.ProviderAccount;

import java.util.List;

/**
 * Operations against a hosting provider (Netlify, Render). Implementations keep
 * all provider-specific request/response shapes internal. No operation deletes a
 * resource in this stage.
 */
public interface HostingProvider {

    ProviderType type();

    /** Validates the credential and returns the connected account's identity. */
    ProviderAccount getAccount(ProviderCredential credential);

    /** Existing sites/services on the account (bounded). */
    List<HostingSite> listSites(ProviderCredential credential);

    /** A single site/service by id, or throws {@link ProviderException.NotFound}. */
    HostingSite getSite(ProviderCredential credential, String siteId);

    /**
     * Creates a new site/service linked to the requested repository on the free
     * plan. Callers must first check {@link #listSites} to avoid duplicates.
     */
    HostingSite createSite(ProviderCredential credential, CreateSiteRequest request);

    /**
     * Re-applies repository/build configuration to an existing resource. Providers
     * that do not require a repair step may keep the existing resource unchanged.
     */
    default HostingSite configureSite(ProviderCredential credential, String siteId, CreateSiteRequest request) {
        return getSite(credential, siteId);
    }

    /**
     * Clears and recreates a repository binding after a deployment supplies positive
     * evidence that the provider cannot clone it. Callers must not use this as a
     * routine configuration operation: unlinking can remove provider-managed deploy
     * keys and hooks. Providers without a separate repair operation may reapply their
     * normal configuration.
     */
    default HostingSite repairRepositoryBinding(ProviderCredential credential, String siteId,
                                                CreateSiteRequest request) {
        return configureSite(credential, siteId, request);
    }

    /** Sets/updates environment variables on the site/service. Values are not logged. */
    void setEnvVars(ProviderCredential credential, String siteId, List<EnvVarInput> vars);

    /** Triggers a deployment and returns the initial status. */
    DeploymentStatus triggerDeploy(ProviderCredential credential, String siteId, DeployRequest request);

    /** Reads the status of a deployment. */
    DeploymentStatus getDeploymentStatus(ProviderCredential credential, String siteId, String deploymentId);

    /** Returns sanitised build/deploy logs (secrets redacted, size-capped). */
    String getSanitizedLogs(ProviderCredential credential, String siteId, String deploymentId);

    /** Cancels an in-progress deployment where the provider supports it safely. */
    void cancelDeploy(ProviderCredential credential, String siteId, String deploymentId);

    /** Restarts/redeploys the latest deployment where supported; returns the new status. */
    DeploymentStatus restart(ProviderCredential credential, String siteId);
}
