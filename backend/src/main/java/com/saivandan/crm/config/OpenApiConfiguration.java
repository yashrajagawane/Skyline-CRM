package com.saivandan.crm.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
  @Bean OpenAPI saiVandanOpenAPI() {
    return new OpenAPI().info(new Info().title("Skyline CRM CRM API").version("v1").description("Role-aware Real Estate CRM and ERP API for Skyline CRM."));
  }
}
