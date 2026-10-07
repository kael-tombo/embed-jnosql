package org.embeddedjnosql.db.demo.spring;

import org.embeddedjnosql.db.demo.model.SampleData;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class EcommerceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcommerceApplication.class, args);
    }

    @Bean
    CommandLineRunner initData(ProductService productService) {
        return args -> {
            if (productService.count() == 0) {
                SampleData.products().forEach(productService::save);
            }
        };
    }
}
