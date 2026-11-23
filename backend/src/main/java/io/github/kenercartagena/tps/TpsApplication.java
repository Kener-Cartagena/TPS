package io.github.kenercartagena.tps;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * Entry point of the Transaction Processing System.
 *
 * <p>TPS is a modular monolith: business modules (accounts, ledger, transactions, ...)
 * live in sub-packages of this one, and Spring Modulith verifies their boundaries in
 * tests. See {@code docs/adr/0001-modular-monolith.md}.
 *
 * <p>{@code shared} is a shared module: it is available to every other module and is
 * always included when a single module is bootstrapped in an integration test.
 */
@SpringBootApplication
@Modulithic(systemName = "TPS", sharedModules = "shared")
public class TpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(TpsApplication.class, args);
    }
}
