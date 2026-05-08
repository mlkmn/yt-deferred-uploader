package pl.mlkmn.ytdeferreduploader.devtools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.mlkmn.ytdeferreduploader.service.DemoSeedService;
import pl.mlkmn.ytdeferreduploader.service.MockYouTubeUploadService;
import pl.mlkmn.ytdeferreduploader.service.YouTubeUploadService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.mode=DEMO")
class DevtoolsAbsentUnderDemoOnlyTest {

    @Autowired private YouTubeUploadService uploadService;
    @Autowired private ApplicationContext context;

    @MockitoBean private DemoSeedService demoSeedService;

    @Test
    void demoOnly_wiresRegularMockUploadService() {
        assertInstanceOf(MockYouTubeUploadService.class, uploadService);
    }

    @Test
    void demoOnly_devtoolsBeansAreAbsent() {
        assertFalse(context.containsBean("mockOutcomeStore"),
                "MockOutcomeStore must not wire under demo alone");
        assertFalse(context.containsBean("devtoolsJobController"),
                "DevtoolsJobController must not wire under demo alone");
        assertFalse(context.containsBean("devtoolsMockYouTubeUploadService"),
                "DevtoolsMockYouTubeUploadService must not wire under demo alone");
    }
}
