package pl.mlkmn.ytdeferreduploader.devtools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.mlkmn.ytdeferreduploader.service.DemoSeedService;
import pl.mlkmn.ytdeferreduploader.service.YouTubeUploadService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles({"test", "devtools"})
@TestPropertySource(properties = "app.mode=DEMO")
class DevtoolsWiringTest {

    @Autowired private YouTubeUploadService uploadService;
    @Autowired private ApplicationContext context;

    // Suppress real seeding so the upload scheduler has nothing to chew on during the test.
    @MockitoBean private DemoSeedService demoSeedService;

    @Test
    void devtoolsAndDemo_wiresDevtoolsServiceAsPrimary() {
        assertInstanceOf(DevtoolsMockYouTubeUploadService.class, uploadService);
    }

    @Test
    void devtoolsAndDemo_registersAllDevtoolsBeans() {
        assertTrue(context.containsBean("mockOutcomeStore"),
                "MockOutcomeStore should be wired");
        assertTrue(context.containsBean("devtoolsJobController"),
                "DevtoolsJobController should be wired");
        assertEquals(1, context.getBeansOfType(Sleeper.class).size(),
                "exactly one Sleeper bean (the default Thread::sleep)");
    }
}
