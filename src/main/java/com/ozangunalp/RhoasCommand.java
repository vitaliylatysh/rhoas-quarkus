package com.ozangunalp;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.openshift.cloud.api.kas.auth.models.TopicSettings;
import org.keycloak.adapters.installed.KeycloakInstalled;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openshift.cloud.api.kas.DefaultApi;
import com.openshift.cloud.api.kas.auth.TopicsApi;
import com.openshift.cloud.api.kas.auth.models.NewTopicInput;
import com.openshift.cloud.api.kas.auth.models.Topic;
import com.openshift.cloud.api.kas.invoker.ApiClient;
import com.openshift.cloud.api.kas.invoker.ApiException;
import com.openshift.cloud.api.kas.invoker.Configuration;
import com.openshift.cloud.api.kas.invoker.auth.HttpBearerAuth;
import com.openshift.cloud.api.kas.models.KafkaRequest;
import com.openshift.cloud.api.kas.models.KafkaRequestPayload;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "rhoas", mixinStandardHelpOptions = true)
public class RhoasCommand implements Runnable {

    private static final Duration MIN_TOKEN_VALIDITY = Duration.ofSeconds(30);
    private static final String API_CLIENT_BASE_PATH = "https://api.openshift.com";
    private static final String API_INSTANCE_CLIENT_BASE_PATH = "https://identity.api.openshift.com";

    ObjectMapper objectMapper = new ObjectMapper();

    private static CommandParameters parameters = new CommandParameters();

    private ApiClient defaultClient = getApiClient();
    private DefaultApi apiInstance = new DefaultApi(defaultClient);
    private com.openshift.cloud.api.kas.auth.invoker.ApiClient defaultInstanceClient = getApiInstanceClient();
    private TopicsApi apiInstanceTopic = new TopicsApi(defaultInstanceClient);

    @Override
    public void run() {
        if(Objects.nonNull(parameters)){
            if(Objects.nonNull(parameters.instanceName)){
//                System.out.println(createInstance(parameters.instanceName));
            }
            if(Objects.nonNull(parameters.instanceTopic)){
                System.out.println(createInstanceTopic(parameters.instanceTopic));
            }
        } 
    }

    private KafkaRequest createInstance(String name){
        KafkaRequestPayload kafkaRequestPayload = new KafkaRequestPayload(); // KafkaRequestPayload | Kafka data
        kafkaRequestPayload.setName(name);

        try {
            return apiInstance.createKafka(true, kafkaRequestPayload);
        } catch (ApiException e) {
            System.err.println("Exception when calling DefaultApi#createKafka");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Reason: " + e.getResponseBody());
            System.err.println("Response headers: " + e.getResponseHeaders());
            e.printStackTrace();
        }

        return null;
    }

    private Topic createInstanceTopic(String topicName){
        NewTopicInput topicInput = new NewTopicInput();
        topicInput.setName(topicName);
        TopicSettings ts = new TopicSettings();
        List list = new ArrayList<>();
        ts.setConfig(list);
        ts.setNumPartitions(1);
        topicInput.setSettings(ts);


        try {
            return apiInstanceTopic.createTopic(topicInput);
        } catch (com.openshift.cloud.api.kas.auth.invoker.ApiException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return null;
    }

    /**
     * From https://github.com/redhat-developer/app-services-sdk-java/tree/main/packages/kafka-management-sdk#getting-started
     */
    private ApiClient getApiClient() {
        ApiClient apiClient = Configuration.getDefaultApiClient();
        apiClient.setBasePath(API_CLIENT_BASE_PATH);

        String tokenString = getBearerToken(null);

        // Configure HTTP bearer authorization: Bearer
        HttpBearerAuth bearer = (HttpBearerAuth) apiClient.getAuthentication("Bearer");
        bearer.setBearerToken(tokenString);
        return apiClient;
    }

    private com.openshift.cloud.api.kas.auth.invoker.ApiClient getApiInstanceClient() {
        com.openshift.cloud.api.kas.auth.invoker.ApiClient apiClient
                = com.openshift.cloud.api.kas.auth.invoker.Configuration.getDefaultApiClient();

        // https://admin-server-my-instanc-ca-nv-d-aqfmd-jmum-g.bf2.kafka.rhcloud.com/api/v1/topics
        //                      my-instanc-ca-nv-d-aqfmd-jmum-g.bf2.kafka.rhcloud.com:443
        String serverUrl = null;
        try {
            serverUrl = "https://admin-server-" + apiInstance.getKafkas(null, null, null,null).getItems().get(0).getBootstrapServerHost();
        } catch (ApiException e) {
            e.printStackTrace();
        }
        apiClient.setBasePath(serverUrl);

//        String tokenString = getBearerToken("META-INF/keycloak-instance.json");
        String tokenString = "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJnakluN0lUY3BrMGlMN0QwTU9jT2xZVkc5c1pwOWR2c2dBOHJUb2FWd0s0In0.eyJleHAiOjE2NTM0NzU5ODYsImlhdCI6MTY1MzQ3NTY4NiwianRpIjoiZDI4OTBhZDItNjQwNy00ZGViLTliOWUtNWYwMDY0YWI0Y2JlIiwiaXNzIjoiaHR0cHM6Ly9pZGVudGl0eS5hcGkub3BlbnNoaWZ0LmNvbS9hdXRoL3JlYWxtcy9yaG9hcyIsInN1YiI6IjI2OWU0YjJiLWY1YzItNGYyYy04MGIzLTI2ZmY1MWIxOGVhMyIsInR5cCI6IkJlYXJlciIsImF6cCI6InNydmMtYWNjdC1lMDBjNDU0Ny0wYTBiLTRkMTYtYTVhYi1mZjQ5ODU4ODg3ODEiLCJhY3IiOiIxIiwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbIm9mZmxpbmVfYWNjZXNzIiwiZGVmYXVsdC1yb2xlcy1yaG9hcyIsInVtYV9hdXRob3JpemF0aW9uIl19LCJzY29wZSI6Im9wZW5pZCBwcm9maWxlIGVtYWlsIiwiY2xpZW50SG9zdCI6IjE3Ni4zNi45LjM5IiwiY2xpZW50SWQiOiJzcnZjLWFjY3QtZTAwYzQ1NDctMGEwYi00ZDE2LWE1YWItZmY0OTg1ODg4NzgxIiwiZW1haWxfdmVyaWZpZWQiOmZhbHNlLCJyaC11c2VyLWlkIjoiNTUxNzQ3OTUiLCJyaC1vcmctaWQiOiIxNTk5MDgxMSIsInByZWZlcnJlZF91c2VybmFtZSI6InNlcnZpY2UtYWNjb3VudC1zcnZjLWFjY3QtZTAwYzQ1NDctMGEwYi00ZDE2LWE1YWItZmY0OTg1ODg4NzgxIiwiY2xpZW50QWRkcmVzcyI6IjE3Ni4zNi45LjM5IiwidXNlcm5hbWUiOiJzZXJ2aWNlLWFjY291bnQtc3J2Yy1hY2N0LWUwMGM0NTQ3LTBhMGItNGQxNi1hNWFiLWZmNDk4NTg4ODc4MSJ9.QpGUA2922N78BeL2LKWZOc7w1xX169SET7Ofp85W6eSyYoJQ7-_9hGNqpdIlrvY2ZOHfVWklNxiLjvelMgQvjz1k8AQKa4QfpwkdRzantdpk0AyIFuw6OXjlwM9ixsusWFA0h9EDvXjIl4LA_GTmlB4bm1fv4wK759YXF1GDX0rLj26Tc5E_TwcK6NauVEgTuHC77UZqmjAJnNv1Z_uKm_oOVTkWyvZnoawSaUM7BwvmW5Jmc30kYk8Ayq5fXb0rpX2537sdtgg7Ivl0zhvv5IHGaE32-SI9lrb1fCsslpPgwWPmdIENbq8raOvav-0htJ8jIwQNQw89bR6NvipIlQ";

        // Configure HTTP bearer authorization: Bearer
        com.openshift.cloud.api.kas.auth.invoker.auth.OAuth bearer =
                (com.openshift.cloud.api.kas.auth.invoker.auth.OAuth) apiClient.getAuthentication("Bearer");
        bearer.setAccessToken(tokenString);
//        HttpBearerAuth auth = (HttpBearerAuth) apiClient.getAuthentication("Bearer");
//        auth.setBearerToken(tokenString);
        return apiClient;
    }

    /**
     * From https://www.keycloak.org/docs/latest/securing_apps/#_installed_adapter
     */
    private String getBearerToken(String fileName) {
        try {
            KeycloakInstalled keycloak;
            RhoasTokens storedTokens = null;
            if (fileName == null) {
                // reads the configuration from classpath: META-INF/keycloak.json
                keycloak = new KeycloakInstalled();
                storedTokens = getStoredTokenResponse(null);
            } else {
                InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName);
                keycloak = new KeycloakInstalled(is);
            }
            // TODO set resteasyclient depending on the platform
            // keycloak.setResteasyClient();


            // ensure token is valid for at least 30 seconds
            if (storedTokens != null && storedTokens.accessTokenIsValidFor(MIN_TOKEN_VALIDITY)) {
                return storedTokens.access_token;
            } else if (storedTokens != null && storedTokens.refreshTokenIsValidFor(MIN_TOKEN_VALIDITY)) {
                keycloak.refreshToken(storedTokens.refresh_token);
                storeTokenResponse(keycloak);
                return keycloak.getTokenString();
            } else {
                // opens desktop browser
                // TODO this will create a callback server on localhost on a random port using Undertow. We may need to change that to a vertx server.
                keycloak.loginDesktop();
                storeTokenResponse(keycloak);
                return keycloak.getTokenString();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    RhoasTokens storeTokenResponse(KeycloakInstalled keycloak) throws IOException {
        RhoasTokens rhoasTokens = new RhoasTokens();
        rhoasTokens.refresh_token = keycloak.getRefreshToken();
        rhoasTokens.access_token = keycloak.getTokenString();
        long timeMillis = System.currentTimeMillis();
        rhoasTokens.refresh_expiration = timeMillis + keycloak.getTokenResponse().getRefreshExpiresIn() * 1000;
        rhoasTokens.access_expiration = timeMillis + keycloak.getTokenResponse().getExpiresIn() * 1000;
        objectMapper.writeValue(parameters.tokensPath.toFile(), rhoasTokens);
        return rhoasTokens;
    }

    RhoasTokens getStoredTokenResponse(String fileName) {
        try {
            if (fileName == null) {
                return objectMapper.readValue(parameters.tokensPath.toFile(), RhoasTokens.class);
            } else {
                return objectMapper.readValue(parameters.tokensPath2.toFile(), RhoasTokens.class);
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static void main(String[] args) {
        new CommandLine(parameters).parseArgs(args);
        RhoasCommand rhoasCommand = new RhoasCommand();
        rhoasCommand.run();
    }
}
