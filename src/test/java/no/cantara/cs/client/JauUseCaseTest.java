package no.cantara.cs.client;

import no.cantara.cs.dto.Application;
import no.cantara.cs.dto.ApplicationConfig;
import no.cantara.cs.dto.CheckForUpdateRequest;
import no.cantara.cs.dto.ClientConfig;
import no.cantara.cs.dto.ClientRegistrationRequest;
import no.cantara.cs.dto.event.EventExtractionConfig;
import no.cantara.cs.testsupport.ApplicationConfigBuilder;
import no.cantara.cs.testsupport.TestServer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

/**
 * Verify endpoints used by JAU client.
 */
public class JauUseCaseTest {
    private TestServer testServer;
    private ConfigServiceClient configServiceClient;
    private ConfigServiceAdminClient configServiceAdminClient;

    private Application application;
    private ClientConfig currentClientConfig;

    @BeforeClass
    public void startServer() throws Exception {
        testServer = new TestServer(getClass());
        testServer.cleanAllData();
        testServer.start();
        configServiceClient = testServer.getConfigServiceClient();
        configServiceAdminClient = testServer.getAdminClient();

        application = configServiceAdminClient.registerApplication("jau-use-case-test");

        configServiceAdminClient.createApplicationConfig(application, ApplicationConfigBuilder.createConfigDto("JauUseCaseTest", application));
    }

    @AfterClass
    public void tearDown() {
        testServer.stop();
        configServiceClient.cleanApplicationState();
    }

    @Test
    public void startupAndRegisterClient() throws IOException {
        currentClientConfig = configServiceClient.registerClient(new ClientRegistrationRequest(application.artifactId));

        configServiceClient.saveApplicationState(currentClientConfig);
    }

    @Test(dependsOnMethods = "startupAndRegisterClient")
    public void testGetExtractionConfigs() throws IOException {
        List<EventExtractionConfig> tags = configServiceClient.getEventExtractionConfigs();

        assertEquals(tags.size(), 1);
        assertEquals(tags.get(0).groupName, "jau");
        assertEquals(tags.get(0).tags.get(0).filePath, "logs/blabla.logg");
    }

    @Test(dependsOnMethods = "startupAndRegisterClient")
    public void testCheckForUpdateWithUpToDateClientConfig() throws Exception {
        ClientConfig clientConfig = configServiceClient.checkForUpdate(currentClientConfig.clientId, new CheckForUpdateRequest(currentClientConfig.config.getLastChanged()));
        assertNull(clientConfig);
    }

    @Test(dependsOnMethods = "testCheckForUpdateWithUpToDateClientConfig")
    public void testCheckForUpdateWhenCurrentConfigHasBeenChanged() throws Exception {
        // Update current config by setting lastChanged
        ApplicationConfig updatedConfig = ApplicationConfigBuilder.createConfigDto("UpdatedConfig", application);
        updatedConfig.getConfigurationStores().iterator().next().properties.put("new-property", "new-value");
        updatedConfig.setId(currentClientConfig.config.getId());
        assertNotEquals(currentClientConfig.config.getLastChanged(), updatedConfig.getLastChanged());

        ApplicationConfig updateConfigResponse = configServiceAdminClient.updateConfig(application.id, updatedConfig);
        assertEquals(updateConfigResponse.getId(), currentClientConfig.config.getId());
        assertNotEquals(updateConfigResponse.getLastChanged(), currentClientConfig.config.getLastChanged());

        // CheckForUpdate should return updated config
        ClientConfig checkForUpdateResponse = configServiceClient.checkForUpdate(this.currentClientConfig.clientId, new CheckForUpdateRequest(currentClientConfig.config.getLastChanged()));
        assertNotNull(checkForUpdateResponse);
        assertEquals(checkForUpdateResponse.config.getConfigurationStores().iterator().next().properties.get("new-property"), "new-value");

        // Save state and verify lastChanged is updated
        configServiceClient.saveApplicationState(checkForUpdateResponse);
        assertEquals(configServiceClient.getApplicationState().getProperty("lastChanged"), updateConfigResponse.getLastChanged());

        currentClientConfig = checkForUpdateResponse;
    }

    // Not supported yet
    @Test(enabled = false, dependsOnMethods = "testCheckForUpdateWithNewClientSpecificConfig")
    public void testCheckForUpdateWithNewDefaultConfig() throws Exception {
        ApplicationConfig newDefaultConfig = configServiceAdminClient.createApplicationConfig(application, ApplicationConfigBuilder.createConfigDto("NewDefaultConfigTest", application));

        CheckForUpdateRequest checkForUpdateRequest = new CheckForUpdateRequest(currentClientConfig.config.getLastChanged());
        ClientConfig checkForUpdateResponse = configServiceClient.checkForUpdate(currentClientConfig.clientId, checkForUpdateRequest);

        assertNotNull(checkForUpdateResponse);
        assertEquals(checkForUpdateResponse.config.getId(), newDefaultConfig.getId());

        // Save state and verify lastChanged is updated
        configServiceClient.saveApplicationState(checkForUpdateResponse);
        assertEquals(configServiceClient.getApplicationState().getProperty("lastChanged"), newDefaultConfig.getLastChanged());

        currentClientConfig = checkForUpdateResponse;
    }

}
