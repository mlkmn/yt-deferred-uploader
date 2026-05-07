package pl.mlkmn.ytdeferreduploader.devtools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@ActiveProfiles({"test", "devtools"})
@TestPropertySource(properties = "app.mode=SELF_HOSTED")
class DevtoolsAbsentUnderSelfHostedTest {

    @Autowired private ApplicationContext context;

    @Test
    void devtoolsWithSelfHosted_devtoolsBeansAreAbsent() {
        assertFalse(context.containsBean("mockOutcomeStore"),
                "MockOutcomeStore must not wire when app.mode=SELF_HOSTED");
        assertFalse(context.containsBean("devtoolsJobController"),
                "DevtoolsJobController must not wire when app.mode=SELF_HOSTED");
        assertFalse(context.containsBean("devtoolsMockYouTubeUploadService"),
                "DevtoolsMockYouTubeUploadService must not wire when app.mode=SELF_HOSTED");
    }
}
