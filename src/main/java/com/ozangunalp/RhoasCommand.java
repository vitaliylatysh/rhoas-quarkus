package com.ozangunalp;

import java.util.concurrent.TimeUnit;

import org.keycloak.adapters.installed.KeycloakInstalled;

import com.openshift.cloud.api.kas.DefaultApi;
import com.openshift.cloud.api.kas.invoker.ApiClient;
import com.openshift.cloud.api.kas.invoker.ApiException;
import com.openshift.cloud.api.kas.invoker.Configuration;
import com.openshift.cloud.api.kas.invoker.auth.HttpBearerAuth;
import com.openshift.cloud.api.kas.models.KafkaRequestList;
import picocli.CommandLine.Command;

@Command(name = "rhoas", mixinStandardHelpOptions = true)
public class RhoasCommand implements Runnable {

    @Override
    public void run() {
        ApiClient defaultClient = getApiClient();

        DefaultApi apiInstance = new DefaultApi(defaultClient);
        try {
            KafkaRequestList result = apiInstance.getKafkas(null, null, "created_at", "");
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling DefaultApi#createKafka");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Reason: " + e.getResponseBody());
            System.err.println("Response headers: " + e.getResponseHeaders());
            e.printStackTrace();
        }
    }

    /**
     * From https://github.com/redhat-developer/app-services-sdk-java/tree/main/packages/kafka-management-sdk#getting-started
     */
    private ApiClient getApiClient() {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://api.openshift.com");

        String tokenString = getBearerToken();

        // Configure HTTP bearer authorization: Bearer
        HttpBearerAuth Bearer = (HttpBearerAuth) defaultClient.getAuthentication("Bearer");
        Bearer.setBearerToken(tokenString);
        return defaultClient;
    }

    /**
     * From https://www.keycloak.org/docs/latest/securing_apps/#_installed_adapter
     */
    private String getBearerToken() {
        try {
            // reads the configuration from classpath: META-INF/keycloak.json
            KeycloakInstalled keycloak = new KeycloakInstalled();
            // TODO set resteasyclient depending on the platform
            // keycloak.setResteasyClient();

            // opens desktop browser
            // TODO this will create a callback server on localhost on a random port using Undertow. We may need to change that to a vertx server.
            keycloak.loginDesktop();

            // No exception : return token string to use in send backend request
            // TODO store access token and refresh token
            System.out.println("Refresh token : " + keycloak.getRefreshToken());
            System.out.println("Access token : " + keycloak.getTokenString());

            // ensure token is valid for at least 30 seconds
            long minValidity = 30L;
            return keycloak.getTokenString(minValidity, TimeUnit.SECONDS);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
