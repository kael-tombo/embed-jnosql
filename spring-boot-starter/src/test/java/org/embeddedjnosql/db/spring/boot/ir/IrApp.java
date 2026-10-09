package org.embeddedjnosql.db.spring.boot.ir;

import org.embeddedjnosql.db.spring.boot.data.EnableEmbedJpaRepositories;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal application context for the Integration Review 36-Scenario test suite.
 * Lives in its own sub-package so it does not conflict with other test applications
 * in the parent {@code spring.boot} package.
 */
@SpringBootApplication(scanBasePackages = "org.embeddedjnosql.db.spring.boot.ir")
@EnableEmbedJpaRepositories(basePackages = "org.embeddedjnosql.db.spring.boot.ir")
public class IrApp {
}
