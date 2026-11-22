package io.github.kenercartagena.tps;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Transaction Processing System.
 *
 * <p>TPS is a modular monolith: business modules (accounts, ledger, transactions, ...)
 * live in sub-packages of this one. See {@code docs/adr/0001-modular-monolith.md}.
 */
@SpringBootApplication
public class TpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(TpsApplication.class, args);
    }
}
