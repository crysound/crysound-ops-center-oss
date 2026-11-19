package com.crysound.opscenter.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class SecurityConfigurationTest {
    private final String operatorPassword = UUID.randomUUID().toString();
    private final String viewerPassword = UUID.randomUUID().toString();

    private Map<String, Object> credentials() {
        return new HashMap<>(Map.of(
                "ops.security.operator-username", "demo-operator",
                "ops.security.operator-password", operatorPassword,
                "ops.security.viewer-username", "demo-viewer",
                "ops.security.viewer-password", viewerPassword));
    }

    private ApplicationContextRunner runner() throws IOException {
        var yaml = new YamlPropertySourceLoader().load("demo", new ClassPathResource("application.yml"));
        return new ApplicationContextRunner().withUserConfiguration(Accounts.class)
                .withInitializer(context -> {
                    var sources = context.getEnvironment().getPropertySources();
                    sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    yaml.forEach(sources::addLast);
                });
    }

    @Test
    void everyMissingEnvironmentCredentialStopsStartup() throws IOException {
        for (String missing : credentials().keySet()) {
            Map<String, Object> supplied = credentials();
            supplied.remove(missing);
            runner().withPropertyValues(supplied.entrySet().stream()
                            .map(entry -> entry.getKey() + "=" + entry.getValue()).toArray(String[]::new))
                    .run(context -> assertThat(context).hasFailed()
                            .getFailure().hasRootCauseInstanceOf(IllegalStateException.class));
        }
    }

    @Test
    void blankAndUnresolvedCredentialsCannotCreateAccounts() throws IOException {
        for (String field : credentials().keySet()) {
            for (String invalid : new String[]{"", " ", "${EXAMPLE_UNSET}"}) {
                Map<String, Object> supplied = credentials();
                supplied.put(field, invalid);
                runner().withPropertyValues(supplied.entrySet().stream()
                                .map(entry -> entry.getKey() + "=" + entry.getValue()).toArray(String[]::new))
                        .run(context -> assertThat(context).hasFailed()
                                .getFailure().hasRootCauseInstanceOf(IllegalStateException.class));
            }
        }
    }

    @Test
    void shortPasswordsFailBootStartupWithoutAppearingInLogs(CapturedOutput output) {
        for (String field : new String[]{"ops.security.operator-password", "ops.security.viewer-password"}) {
            String rejected = UUID.randomUUID().toString().substring(0, 8);
            Map<String, Object> supplied = credentials();
            supplied.put(field, rejected);
            supplied.put("spring.main.banner-mode", "off");
            var environment = new StandardEnvironment();
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            var application = new SpringApplication(Accounts.class);
            application.setEnvironment(environment);
            application.setWebApplicationType(WebApplicationType.NONE);
            application.setRegisterShutdownHook(false);
            application.setLogStartupInfo(false);
            application.setDefaultProperties(supplied);
            assertThatThrownBy(application::run).hasRootCauseInstanceOf(IllegalStateException.class);
            assertThat(output.getAll()).contains("passwords require at least 12 characters")
                    .doesNotContain(rejected, operatorPassword, viewerPassword);
        }
    }

    @Test
    void passwordsOverSeventyTwoUtf8BytesStopStartup() throws IOException {
        String atLimit = "é".repeat(36);
        String overLimit = atLimit + "x";
        for (String field : new String[]{"ops.security.operator-password", "ops.security.viewer-password"}) {
            Map<String, Object> accepted = credentials();
            accepted.put(field, atLimit);
            runner().withPropertyValues(accepted.entrySet().stream()
                            .map(entry -> entry.getKey() + "=" + entry.getValue()).toArray(String[]::new))
                    .run(context -> assertThat(context).hasNotFailed());
            Map<String, Object> rejected = credentials();
            rejected.put(field, overLimit);
            runner().withPropertyValues(rejected.entrySet().stream()
                            .map(entry -> entry.getKey() + "=" + entry.getValue()).toArray(String[]::new))
                    .run(context -> assertThat(context).hasFailed().getFailure()
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .rootCause().hasMessageNotContaining(overLimit));
        }
    }

    @Test
    void configuredCredentialsAreEncodedAndAuthenticate() throws IOException {
        Map<String, Object> supplied = credentials();
        runner().withPropertyValues(supplied.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue()).toArray(String[]::new))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var users = context.getBean(UserDetailsService.class);
                    var encoder = new BCryptPasswordEncoder();
                    assertThat(encoder.matches(operatorPassword,
                            users.loadUserByUsername("demo-operator").getPassword())).isTrue();
                    assertThat(encoder.matches(viewerPassword,
                            users.loadUserByUsername("demo-viewer").getPassword())).isTrue();
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SecurityProperties.class)
    static class Accounts {
        @Bean
        UserDetailsService users(SecurityProperties properties) {
            return new SecurityConfig().users(properties, new BCryptPasswordEncoder());
        }
    }
}
