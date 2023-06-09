package com.ulisesbocchio.jasyptspringbootstarter;

import com.ulisesbocchio.jasyptspringboot.configuration.EnableEncryptablePropertiesBeanFactoryPostProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.cloud.bootstrap.BootstrapApplicationListener;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Fahim Farook
 */
public class BootstrappingJasyptConfigurationTest {

    private ConfigurableApplicationContext context;

    @AfterEach
    public void after() {
        if (this.context != null) {
            this.context.close();
        }
    }

    /*
     * This is the real issue being addressed in here in JasyptSpringCloudBootstrapConfiguration.
     */
    @Test
    public void issue_notDecryptedDuringBoostrapPhase() {
        // making spring.cloud.bootstrap.enabled=true in order to bootstrap the application.
        // making jasypt.encryptor.bootstrap=false otherwise JasyptSpringCloudBootstrapConfiguration becomes active.
        startWith(new BaseBootstrappingTestListener() {

            @Override
            public void onApplicationEvent(final ApplicationStartedEvent event) {
                assertEquals( "mypassword", event.getApplicationContext().getEnvironment().getProperty("spring.cloud.config.server.svn.password"), "ENC() value is not decrypted during bootstrap phase");
            }
        }, "--spring.cloud.bootstrap.enabled=true", "--jasypt.encryptor.bootstrap=false");

        assertNotNull(this.context.getBean(EnableEncryptablePropertiesBeanFactoryPostProcessor.class));
    }

    @Test
    public void fix_decryptedDuringBoostrapPhase() {
        // making spring.cloud.bootstrap.enabled=true in order to bootstrap the application.
        // making jasypt.encryptor.bootstrap=true in order to bootstrap Jasypt.
        startWith(new BaseBootstrappingTestListener() {

            @Override
            public void onApplicationEvent(final ApplicationStartedEvent event) {
                assertEquals("mypassword", event.getApplicationContext().getEnvironment().getProperty("spring.cloud.config.server.svn.password"), "ENC() value is decrypted during bootstrap phase");
            }
        }, "--spring.cloud.bootstrap.enabled=true", "--jasypt.encryptor.bootstrap=true");

        assertNotNull(this.context.getBean(EnableEncryptablePropertiesBeanFactoryPostProcessor.class));
    }

    @Test
    public void encryptableBFPPBeanCreatedWhenBoostrapTrue() {
        startWith(new BaseBootstrappingTestListener() {

            @Override
            public void onApplicationEvent(final ApplicationStartedEvent event) {
                assertEquals("mypassword", event.getApplicationContext().getEnvironment().getProperty("spring.cloud.config.server.svn.password"), "ENC() value is decrypted during bootstrap phase");
            }
        }, "--spring.cloud.bootstrap.enabled=true");
        assertNotNull(this.context.getBean(EnableEncryptablePropertiesBeanFactoryPostProcessor.class), "EnableEncryptablePropertiesBeanFactoryPostProcessor not created when spring.cloud.bootstrap.enabled=true");
    }

    @Test
    public void encryptableBFPPBeanCreatedWhenBoostrapFalse() {
        startWith(new BaseBootstrappingTestListener() {

            @Override
            public void onApplicationEvent(final ApplicationStartedEvent event) {
                assertNotEquals("mypassword", event.getApplicationContext().getEnvironment().getProperty("spring.cloud.config.server.svn.password"), "ENC() value is decrypted during bootstrap phase");
            }
        }, "--spring.cloud.bootstrap.enabled=false");
        assertNotNull(this.context.getBean(EnableEncryptablePropertiesBeanFactoryPostProcessor.class), "EnableEncryptablePropertiesBeanFactoryPostProcessor not created when spring.cloud.bootstrap.enabled=false");
    }

    @SuppressWarnings("rawtypes")
    private void startWith(final ApplicationListener listener, final String... args) {
        try {
            final SpringApplicationBuilder builder = new SpringApplicationBuilder(BootstrapConfig.class)
                    .profiles("subversion")
                    .properties("server.port=0")
                    .web(WebApplicationType.SERVLET);

            if (listener != null) {
                builder.listeners(listener);
            }

            this.context = builder.run(args);
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }


    @Configuration
    @EnableAutoConfiguration
    static class BootstrapConfig {

    }

    static abstract class BaseBootstrappingTestListener
            implements ApplicationListener<ApplicationStartedEvent>, Ordered {

        @Override
        public int getOrder() {
            // order should be greater than
            // BootstrapApplicationListener.DEFAULT_ORDER - so that this
            // listener is invoked after BootstrapApplicationListener, otherwise
            // bootstrap.properties will not have been read. This is required
            // since encrypted text (i.e.
            // passwords) could be configured in bootstrap.properties.
            return BootstrapApplicationListener.DEFAULT_ORDER + 1;
        }
    }

}
