package com.campus.venue.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / OpenAPI3 接口文档配置，访问 /doc.html
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI campusVenueOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("校园场馆预约与拼场组队系统 API")
                .description("CampusVenue 后端接口文档（统一响应体 {code,message,data,traceId}，鉴权头 Authorization）")
                .version("v1.0.0")
                .contact(new Contact().name("CampusVenue Team")));
    }
}
